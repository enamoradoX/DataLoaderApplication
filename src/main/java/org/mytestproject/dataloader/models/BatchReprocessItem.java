package org.mytestproject.dataloader.models;

/** One corrected row submitted from the review page: which skip it is, and its edited values. */
public record BatchReprocessItem(
        Long skipId,
        EmployeeRecordData data
) {}
