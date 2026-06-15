# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 4 application that loads employee records from a flat file (`src/main/resources/data.txt`) into an
in-memory H2 database. It demonstrates two parallel loading strategies (custom iterator vs. Spring Batch) and
streams validation/skip errors to Kafka and a local audit log.

## Build & run

```bash
./mvnw clean compile      # compile
./mvnw test                # run all tests
./mvnw test -Dtest=DataLoaderApplicationTests#contextLoads   # run a single test method
./mvnw spring-boot:run     # run the app (listens on port 8081, see application.properties)
```

Kafka (used for skip-event publishing) runs via Docker Compose:

```bash
docker-compose up -d       # starts zookeeper, kafka, and kafka-ui (http://localhost:8080)
```

If Kafka isn't running, skip-event publishing fails silently (logged, doesn't fail the batch job — see
`EmployeeSkipListener.publishToKafka`).

H2 console is available at `http://localhost:8081/h2-console` (JDBC URL `jdbc:h2:mem:payroll_db`, user `sa`,
password `password`).

## Architecture

### Two loading strategies

The app supports two ways of loading `data.txt` into the `Employee` table, selected by
`app.loader.strategy` in `application.properties` (`iterator` or `batch`):

- **Custom iterator** — `services/DataLoaderService.java`: manually reads `data.txt` line by line, validates/parses
  each field by hand, batches inserts in chunks of 500 via `EmployeeRepository.saveAll`, and skips bad rows with
  per-line logging.
- **Spring Batch job** — configured in `configurations/SpringBatchConfig.java`, bean name `employeeLoaderJob`.

Note: `services/DatabaseLoaderRunner.java` is a `@Component` whose `CommandLineRunner` logic is currently commented
out, so neither strategy runs automatically on startup. The Spring Batch job is instead triggered via the REST API
(see below), and `spring.batch.job.enabled=false` prevents auto-run on boot.

### Spring Batch pipeline (`SpringBatchConfig`)

`employeeLoaderJob` → `csvFileLoadingStep` (chunk size 500):

1. **Reader** — `FlatFileItemReader<EmployeeDto>` over `classpath:data.txt`, skips the header row, maps fields to
   `EmployeeDto` (a record with Jakarta Bean Validation annotations in `models/EmployeeDto.java`).
2. **Processor** — `CompositeItemProcessor` chaining:
   - `jsrValidator()` — a plain `ItemProcessor<EmployeeDto, EmployeeDto>` that calls the shared
     `jakarta.validation.Validator` bean and throws `ConstraintViolationException` on violations. (This replaced
     the old `BeanValidatingItemProcessor`, whose `ValidationException` produced an ugly multi-line message; the
     custom processor + the same `Validator` the legacy loader uses gives clean per-field messages identical
     across both loaders.)
   - a mapping lambda (`EmployeeDto` → `Employee` entity)
3. **Writer** — inline lambda calling `EmployeeRepository.saveAll(chunk.getItems())`.
4. **Fault tolerance** — `faultTolerant()` with:
   - `retryPolicy(writeRetryPolicy())` — Spring Framework 7 `org.springframework.core.retry.RetryPolicy` (retry +
     exponential backoff folded together) that retries **only** `TransientDataAccessException` (DB blips), not
     deterministic validation/parse failures. Note: Spring Batch 6's `.chunk()` returns `ChunkOrientedStepBuilder`,
     which uses this new `RetryPolicy` — the legacy spring-retry `.backOffPolicy(...)` is **not** available here.
   - `skip(Exception.class).skipLimit(100)` with `EmployeeSkipListener` as the skip listener.
5. **Job listener** — `JobPerformanceListener` logs a before/after summary (read/write/skip counts, duration).

A `DataSourceInitializer` bean (`batchSchemaInitializer`) loads Spring Batch's bundled `schema-h2.sql` on startup
(`continueOnError(true)`) since `spring.batch.jdbc.initialize-schema=always`.

### Skip handling & observability

Both loaders report skips the **same way**, and a deliberate design goal is to keep the two in parity (same
validation rules, same log format/severity, same notification flow). For each skipped record a loader:

- logs at `error` via the standard `Logger` (console) — every skip is an error, since any skipped row means the
  user's data didn't fully load
- logs a structured line via a separate `auditLogger` (configured in `src/main/resources/logback-spring.xml` to
  write to `logs/skipped_records.log`): `PHASE: ... | RECORD ID: ... | ERROR: ...`
- persists a `SkippedRecord` row (`entities/SkippedRecord.java`) **synchronously** via
  `services/SkippedRecordService`, stamped with a `loadId` correlation id (batch: the `JobExecution` id, captured
  in `EmployeeSkipListener.beforeStep`; legacy: a `UUID` per run). Writes are idempotent per `(loadId, recordId)`
  for known ids (Batch chunk re-scans / multiple validation errors merge into one row) and use `REQUIRES_NEW` so
  the audit row commits independently of a rolling-back load. This table backs the end-of-run digest + review page.
- publishes a `SkipEvent` via the shared `services/SkipEventPublisher` to the Kafka topic
  `employee-skip-events-topic` (a `NewTopic` bean in `configurations/KafkaConfiguration.java`, 3 partitions, keyed
  by record ID). Kafka is kept for potential other consumers; it no longer drives email.

`models/SkipEvent.java` carries `phase, recordId, errorMessage, timestamp` plus a nullable
`EmployeeRecordData data` (`models/EmployeeRecordData.java`: the 5 row columns as Strings) — populated when the
row was captured (validation/write skips, and parse skips that still split into columns), `null` otherwise.

- **Batch path** — `listeners/EmployeeSkipListener.java` (`SkipListener<EmployeeDto, Employee>`): `onSkipInRead`
  (parse failures, no payload), `onSkipInProcess` (validation, unpacking `ConstraintViolationException` per field,
  payload from the `EmployeeDto`), `onSkipInWrite` (DB failures, payload from the `Employee` entity).
- **Legacy path** — `services/DataLoaderService.java` emits the same phases/format from its hand-rolled parser.

### Digest email + reprocess flow

The notification model is **one digest email per load run** (not per skip), driven by the persisted
`SkippedRecord` table rather than by Kafka:

- **End-of-run trigger** — `JobPerformanceListener.afterJob` (batch) and the end of
  `DataLoaderService.loadLocalDataFile()` (legacy) query `SkippedRecordService.findByLoad(loadId)` and, if any,
  call `EmailNotificationService.sendLoadDigest(...)`. (Synchronous: skip rows are committed via `REQUIRES_NEW`,
  so they're visible by the end-of-run hook.)
- `services/EmailNotificationService.java` — `sendLoadDigest(loadId, skips)` sends ONE plain-text email listing
  each skipped record and a single link to the review page `{app.notifications.email.skips-base-url}/{loadId}`
  (defaults to `http://localhost:4200/skips`). Gated by `app.notifications.email.enabled`; send failures are
  logged, never rethrown. SMTP + addresses come from `MAIL_USERNAME`/`MAIL_APP_PASSWORD` env vars (empty defaults
  so the context still loads when unset — e.g. tests).
- `consumers/SkipEventConsumer.java` — `@KafkaListener` on `employee-skip-events-topic` (explicit
  `consumerFactory`/`skipEventKafkaListenerContainerFactory` beans; `JacksonJsonDeserializer` fixed to `SkipEvent`
  with `ignoreTypeHeaders()`). Now **log-only** — a sample hook for other services; it no longer sends email.
- `services/ReprocessService.java` — re-runs a corrected record through the **same** shared `Validator` against
  `EmployeeDto`, saves on success. `reprocess(...)` handles one record (`POST /api/reprocess`); `reprocessBatch(...)`
  handles many and marks each saved row's `SkippedRecord` as `REPROCESSED` (`POST /api/skips/{loadId}/reprocess`).
- Frontend (`frontend/`, Angular): route `/reprocess` is the single-record edit UI; route `/skips/:loadId` is the
  per-load review page the digest links to — it fetches `GET /api/skips/{loadId}`, shows each pending skip as an
  editable row, and bulk-submits to the batch endpoint, rendering per-row saved/error outcomes.
  `src/main/resources/static/reprocess.html` is the older standalone version (superseded; not linked from email).

### REST API (`controllers/JobOperatorController.java`)

Base path `/api/batch`, backed by `JobOperator`/`JobRepository`:

- `POST /api/batch/start` — starts `employeeLoaderJob` with a unique `time` parameter, returns the execution ID
- `POST /api/batch/stop/{executionId}` — sends a stop signal to a running execution
- `GET /api/batch/summary/{executionId}` — returns `JobExecution.toString()` for the given execution ID

### REST API (`controllers/LegacyDataLoader.java`)

Base path `/api/legacy`, wraps the "iterator" strategy (`DataLoaderService`):

- `POST /api/legacy/start` — synchronously runs `DataLoaderService.loadLocalDataFile()` and returns a
  success/failure message.

### REST API (`controllers/ReprocessController.java`)

Base path `/api/reprocess`:

- `POST /api/reprocess` — body is `EmployeeRecordData` (the 5 columns as JSON strings). Re-validates via
  `ReprocessService` and saves; returns **200** with `{success, savedId}` or **422** with `{success:false, errors}`.
  Backs the Angular `/reprocess` edit page. (No auth — fine for local dev, would need protection before any real
  deployment.)

### REST API (`controllers/SkipsController.java`)

Base path `/api/skips`, backs the per-load review page:

- `GET /api/skips/{loadId}` — returns the still-`PENDING` `SkippedRecord`s for a load as `SkippedRecordView`
  (skipId, recordId, phase, errorMessage, status, editable `data`).
- `POST /api/skips/{loadId}/reprocess` — body is a list of `BatchReprocessItem` (`{skipId, data}`). Reprocesses
  each via `ReprocessService.reprocessBatch`, marks saved rows `REPROCESSED`, returns **200** with a list of
  `BatchReprocessResult` (`{skipId, success, savedId, errors}`) — partial success is normal.

### Data model

- `entities/Employee.java` — JPA entity, table `Employee`, columns `emp_Name`, `emp_Role`, `emp_Salary`, `emp_Email`
  (id auto-generated).
- `models/EmployeeDto.java` — validation-only record used by the reader/processor. The flat-file reader's
  `.names("id","employeeName","email","role","salary")` matches `data.txt`'s header order, and its
  `fieldSetMapper` reads each field by name (not position) before constructing `EmployeeDto`, so reordering
  columns in `data.txt` requires updating `.names(...)` accordingly but does not require touching the
  `fieldSetMapper` itself.

- `services/DataLoaderService.java` (the "iterator" loader strategy) parses `data.txt` with a raw
  `line.split(",")` using the correct positional order (`id, employeeName, email, role, salary`), then validates
  each row through the **same shared `Validator`/`EmployeeDto`** the batch job uses. It is not run on startup
  (`DatabaseLoaderRunner`'s `CommandLineRunner` is commented out) but is reachable via `POST /api/legacy/start`.

`src/main/resources/data.txt` intentionally contains edge-case rows (negative IDs, overflow salaries, malformed
emails, unicode names) to exercise the validation/skip paths.
