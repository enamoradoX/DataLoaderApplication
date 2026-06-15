package org.mytestproject.dataloader.controllers;

import org.mytestproject.dataloader.entities.SkippedRecord;
import org.mytestproject.dataloader.models.BatchReprocessItem;
import org.mytestproject.dataloader.models.BatchReprocessResult;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.models.SkippedRecordView;
import org.mytestproject.dataloader.services.ReprocessService;
import org.mytestproject.dataloader.services.SkippedRecordService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/skips")
public class SkipsController {

    private final SkippedRecordService skippedRecordService;
    private final ReprocessService reprocessService;

    public SkipsController(SkippedRecordService skippedRecordService, ReprocessService reprocessService) {
        this.skippedRecordService = skippedRecordService;
        this.reprocessService = reprocessService;
    }

    /** The still-pending skips for a load run — what the review page renders. */
    @GetMapping("/{loadId}")
    public List<SkippedRecordView> getSkips(@PathVariable String loadId) {
        return skippedRecordService.findPendingByLoad(loadId).stream()
                .map(SkipsController::toView)
                .toList();
    }

    /** Reprocess the corrected rows from the review page; returns a per-row result. */
    @PostMapping("/{loadId}/reprocess")
    public List<BatchReprocessResult> reprocessAll(@PathVariable String loadId,
                                                   @RequestBody List<BatchReprocessItem> items) {
        return reprocessService.reprocessBatch(items);
    }

    private static SkippedRecordView toView(SkippedRecord record) {
        EmployeeRecordData data = new EmployeeRecordData(
                record.getRawId(), record.getRawName(), record.getRawEmail(),
                record.getRawDepartment(), record.getRawRole(), record.getRawSalary());
        return new SkippedRecordView(
                record.getId(), record.getRecordId(), record.getPhase(),
                record.getErrorMessage(), record.getStatus().name(), data);
    }
}
