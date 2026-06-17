package org.mytestproject.dataloader.models;

/**
 * What the review page needs for one skipped record: the skip's DB id (to target it on
 * reprocess), its phase/error for display, and the editable row columns.
 */
public record SkippedRecordView(
        Long skipId,
        String targetType,
        String recordId,
        String phase,
        String errorMessage,
        String status,
        EmployeeRecordData data
) {}
