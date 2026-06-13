package org.mytestproject.dataloader.controllers;

import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.models.ReprocessResult;
import org.mytestproject.dataloader.services.ReprocessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reprocess")
public class ReprocessController {

    private final ReprocessService reprocessService;

    public ReprocessController(ReprocessService reprocessService) {
        this.reprocessService = reprocessService;
    }

    /**
     * Re-submits a single corrected record. Returns 200 with the new id if it now passes
     * validation and is saved, or 422 with the list of remaining errors if it still fails.
     */
    @PostMapping
    public ResponseEntity<ReprocessResult> reprocess(@RequestBody EmployeeRecordData data) {
        ReprocessResult result = reprocessService.reprocess(data);
        return result.success()
                ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }
}
