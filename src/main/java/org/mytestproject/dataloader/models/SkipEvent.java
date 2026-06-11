package org.mytestproject.dataloader.models;

import java.time.Instant;

public record SkipEvent(
        String phase,
        String recordId,
        String errorMessage,
        Instant timestamp
) {}