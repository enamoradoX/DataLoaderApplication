package org.mytestproject.dataloader.entities;

/** Lifecycle of a persisted skipped record. */
public enum SkipStatus {
    /** Captured during a load, not yet successfully reprocessed. */
    PENDING,
    /** Corrected and saved via the reprocess flow. */
    REPROCESSED
}
