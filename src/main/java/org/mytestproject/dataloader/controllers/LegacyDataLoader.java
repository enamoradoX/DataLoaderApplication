package org.mytestproject.dataloader.controllers;

import org.mytestproject.dataloader.services.DataLoaderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/legacy")
public class LegacyDataLoader {

    private final DataLoaderService dataLoaderService;

    public LegacyDataLoader(DataLoaderService dataLoaderService) {
        this.dataLoaderService = dataLoaderService;
    }

    @PostMapping("/start")
    public String startJob() {
        boolean success = dataLoaderService.loadLocalDataFile();
        return success
                ? "Legacy load completed successfully."
                : "Legacy load completed with no records saved. Check logs for details.";
    }
}
