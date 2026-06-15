package org.mytestproject.dataloader.models;

/**
 * The raw, editable columns of a single employee row, all as Strings so even malformed
 * values (e.g. a non-numeric salary) can be carried, displayed, and corrected.
 *
 * Used both as the payload embedded in a {@link SkipEvent} (so the email/UI has the row to
 * show) and as the request body for the reprocess endpoint (the corrected values to retry).
 * Fields may be null when a skip happened before the row could be split into columns.
 */
public record EmployeeRecordData(
        String id,
        String name,
        String email,
        String department,
        String role,
        String salary
) {}
