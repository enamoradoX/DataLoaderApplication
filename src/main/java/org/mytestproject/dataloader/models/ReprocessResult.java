package org.mytestproject.dataloader.models;

import java.util.List;

/**
 * Outcome of a single-record reprocess attempt: either saved (with the new DB id) or
 * rejected with the list of validation/parse errors, in the same "Field 'x' message" form
 * used by the loaders.
 */
public record ReprocessResult(
        boolean success,
        Long savedId,
        List<String> errors
) {
    public static ReprocessResult saved(Long savedId) {
        return new ReprocessResult(true, savedId, List.of());
    }

    public static ReprocessResult rejected(List<String> errors) {
        return new ReprocessResult(false, null, errors);
    }
}
