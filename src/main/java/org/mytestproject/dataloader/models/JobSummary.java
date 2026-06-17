package org.mytestproject.dataloader.models;

/**
 * End-user-facing summary of a job run: status, row counts, and a friendly message.
 * Deliberately omits the raw JobExecution dump (exit stack traces, parameters, timestamps) —
 * that detail is logged for developers instead.
 */
public record JobSummary(
        Long executionId,
        String status,
        long rowsRead,
        long rowsWritten,
        long rowsSkipped,
        String message
) {}
