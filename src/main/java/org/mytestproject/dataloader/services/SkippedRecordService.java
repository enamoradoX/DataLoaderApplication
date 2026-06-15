package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.SkipStatus;
import org.mytestproject.dataloader.entities.SkippedRecord;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.repositories.SkippedRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persists skipped records synchronously during a load so the end-of-run digest email and the
 * review/reprocess page can query them by loadId. Shared by both loaders (parity with the
 * Kafka SkipEventPublisher, which still fires alongside this for other consumers).
 */
@Service
@Slf4j
public class SkippedRecordService {

    private final SkippedRecordRepository repository;

    public SkippedRecordService(SkippedRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Records one skip. Idempotent per (loadId, recordId) when the record id is known: a repeated
     * call (e.g. Spring Batch re-scanning a chunk) merges new error text instead of inserting a
     * duplicate. READ-phase skips have no stable id ("UNKNOWN"), so each is inserted as-is.
     *
     * REQUIRES_NEW so the audit row commits independently of the load's own transaction
     * (which may roll back/retry around the skip).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String loadId, String phase, String recordId, String errorMessage, EmployeeRecordData data) {
        boolean hasKnownId = recordId != null && !recordId.isBlank() && !"UNKNOWN".equals(recordId);

        if (hasKnownId) {
            Optional<SkippedRecord> existingOpt = repository.findByLoadIdAndRecordId(loadId, recordId);
            if (existingOpt.isPresent()) {
                SkippedRecord existing = existingOpt.get();
                if (existing.getErrorMessage() == null || !existing.getErrorMessage().contains(errorMessage)) {
                    existing.setErrorMessage(appendError(existing.getErrorMessage(), errorMessage));
                }
                if (existing.getRawName() == null && data != null) {
                    applyData(existing, data);
                }
                repository.save(existing);
                return;
            }
        }

        SkippedRecord record = new SkippedRecord();
        record.setLoadId(loadId);
        record.setPhase(phase);
        record.setRecordId(recordId);
        record.setErrorMessage(errorMessage);
        record.setStatus(SkipStatus.PENDING);
        record.setCreatedAt(Instant.now());
        applyData(record, data);
        repository.save(record);
    }

    /** All skips for a load run, used by the end-of-run digest. */
    @Transactional(readOnly = true)
    public List<SkippedRecord> findByLoad(String loadId) {
        return repository.findByLoadId(loadId);
    }

    /** Still-pending skips for a load run, used by the review page. */
    @Transactional(readOnly = true)
    public List<SkippedRecord> findPendingByLoad(String loadId) {
        return repository.findByLoadIdAndStatus(loadId, SkipStatus.PENDING);
    }

    /** Marks a skip as reprocessed so it drops off the review page. No-op if the id is unknown. */
    @Transactional
    public void markReprocessed(Long skipId) {
        repository.findById(skipId).ifPresent(record -> {
            record.setStatus(SkipStatus.REPROCESSED);
            repository.save(record);
        });
    }

    private static void applyData(SkippedRecord record, EmployeeRecordData data) {
        if (data == null) {
            return;
        }
        record.setRawId(data.id());
        record.setRawName(data.name());
        record.setRawEmail(data.email());
        record.setRawDepartment(data.department());
        record.setRawRole(data.role());
        record.setRawSalary(data.salary());
    }

    private static String appendError(String existing, String message) {
        return (existing == null || existing.isBlank()) ? message : existing + "; " + message;
    }
}
