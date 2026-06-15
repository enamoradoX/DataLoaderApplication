package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.JobTitle;
import org.mytestproject.dataloader.repositories.JobTitleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a job title by its text, creating it on first sight, so the loaders can wire the
 * Employee -> JobTitle foreign key from the plain "role" column in the file. Same find-or-create +
 * cache pattern as DepartmentService. Assumes one load runs at a time (true for this app).
 */
@Service
@Slf4j
public class JobTitleService {

    private final JobTitleRepository repository;
    private final Map<String, JobTitle> cache = new ConcurrentHashMap<>();

    public JobTitleService(JobTitleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public JobTitle getOrCreate(String title) {
        String key = (title == null) ? "" : title.trim();
        JobTitle cached = cache.get(key);
        if (cached != null) {
            log.info("JobTitle cache HIT for '{}'", key);
            return cached;
        }
        log.info("JobTitle cache MISS for '{}'", key);

        JobTitle jobTitle = repository.findByTitle(key)
                .orElseGet(() -> repository.save(new JobTitle(key)));
        cache.put(key, jobTitle);
        return jobTitle;
    }
}
