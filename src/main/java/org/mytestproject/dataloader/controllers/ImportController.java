package org.mytestproject.dataloader.controllers;

import org.mytestproject.dataloader.models.ImportResponse;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Drag-and-drop import: accepts a file upload, saves it to the import directory under a
 * server-generated name (never the client's filename — that avoids path traversal), then starts
 * the matching batch job with the saved path as the 'inputFile' job parameter.
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    // Maps the URL {type} to the job that loads it.
    private static final Map<String, String> TYPE_TO_JOB = Map.of(
            "departments", "departmentLoaderJob",
            "employees", "employeeLoaderJob");

    // Expected header line per type — used to reject a wrong file (e.g. customer data) up front.
    private static final Map<String, String> EXPECTED_HEADER = Map.of(
            "departments", "name",
            "employees", "id,employeeName,email,department,role,managerId,salary");

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;

    @Value("${app.import.directory}")
    private String importDirectory;

    public ImportController(JobOperator jobOperator, Map<String, Job> jobs) {
        this.jobOperator = jobOperator;
        this.jobs = jobs;
    }

    @PostMapping("/{type}")
    public ResponseEntity<ImportResponse> upload(@PathVariable String type,
                                                 @RequestParam("file") MultipartFile file) throws Exception {
        String jobName = TYPE_TO_JOB.get(type);
        if (jobName == null) {
            return ResponseEntity.badRequest().body(new ImportResponse(type, null, null, null,
                    "Unknown type '" + type + "'. Allowed: " + TYPE_TO_JOB.keySet()));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ImportResponse(type, jobName, null, null,
                    "Uploaded file is empty."));
        }

        // Save with a server-generated name inside the import directory.
        Path dir = Path.of(importDirectory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String storedName = type + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".txt";
        Path target = dir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        // Reject a wrong-format file up front: the first line must match the expected header.
        String header;
        try (var reader = Files.newBufferedReader(target)) {
            header = reader.readLine();
        }
        String expected = EXPECTED_HEADER.get(jobName == null ? "" : type);
        if (expected != null && (header == null || !header.trim().equalsIgnoreCase(expected))) {
            Files.deleteIfExists(target); // don't keep / load a file that isn't this type
            return ResponseEntity.badRequest().body(new ImportResponse(type, jobName, null, null,
                    "This doesn't look like a " + type + " file. Expected header '" + expected
                            + "' but got '" + (header == null ? "" : header.trim()) + "'."));
        }

        // Start the matching job, pointing its @StepScope reader at the uploaded file.
        Job job = jobs.get(jobName);
        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", target.toUri().toString()) // file:/... URI -> Spring Resource
                .addLong("time", System.currentTimeMillis())        // unique -> new JobInstance per upload
                .toJobParameters();
        JobExecution execution = jobOperator.start(job, params);

        return ResponseEntity.ok(new ImportResponse(type, jobName, execution.getId(), storedName,
                "Import started (execution " + execution.getId() + ", status " + execution.getStatus() + ")."));
    }
}
