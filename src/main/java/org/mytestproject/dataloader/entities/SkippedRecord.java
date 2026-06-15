package org.mytestproject.dataloader.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * A row that was skipped during a load, persisted so the digest email and the
 * "review and reprocess" page can query all skips for a given load run.
 *
 * One row per skipped record per load (keyed logically by loadId + recordId);
 * multiple validation errors on the same record are merged into errorMessage.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "skipped_record", indexes = @Index(name = "idx_skipped_load", columnList = "loadId"))
public class SkippedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlation id of the load run (batch JobExecution id, or a UUID for the legacy loader). */
    private String loadId;

    /** READ / PROCESS_VALIDATION / PROCESS / WRITE_DATABASE. */
    private String phase;

    /** The record's id when known, else "UNKNOWN". */
    private String recordId;

    @Column(length = 2000)
    private String errorMessage;

    // The original row columns, as captured (may be null if the row never parsed into columns).
    private String rawId;
    private String rawName;
    private String rawEmail;
    private String rawDepartment;
    private String rawRole;
    private String rawSalary;

    @Enumerated(EnumType.STRING)
    private SkipStatus status;

    private Instant createdAt;
}
