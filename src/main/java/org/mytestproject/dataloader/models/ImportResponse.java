package org.mytestproject.dataloader.models;

/** Result of a drag-and-drop import: which job ran, its execution id, and a human-readable message. */
public record ImportResponse(
        String type,
        String jobName,
        Long executionId,
        String storedAs,
        String message
) {}
