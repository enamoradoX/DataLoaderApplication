package org.mytestproject.dataloader.models;

import java.time.Instant;

public record SkipEvent(
        String phase,
        String recordId,
        String errorMessage,
        Instant timestamp,
        EmployeeRecordData data // the original row, when available, so it can be shown and reprocessed; null otherwise
) {}
