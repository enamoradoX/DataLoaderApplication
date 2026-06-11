package org.mytestproject.dataloader.models;

public record SkippedRecordAudit(
        String rawDataId,
        String phase,
        String errorMessage,
        long timestamp
) {
    @Override
    public String toString() {
        return String.format("[%tF %<tT] PHASE: %s | RECORD ID: %s | ERROR: %s",
                timestamp, phase, rawDataId, errorMessage);
    }
}
