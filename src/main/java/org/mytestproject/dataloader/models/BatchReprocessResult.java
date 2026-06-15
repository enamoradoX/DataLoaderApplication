package org.mytestproject.dataloader.models;

import java.util.List;

/** Per-row outcome of a bulk reprocess, so the page can show which rows saved and which still fail. */
public record BatchReprocessResult(
        Long skipId,
        boolean success,
        Long savedId,
        List<String> errors
) {}
