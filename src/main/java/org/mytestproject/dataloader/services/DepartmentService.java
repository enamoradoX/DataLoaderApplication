package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Department;
import org.mytestproject.dataloader.repositories.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a department by name, creating it on first sight, so the loaders can wire the
 * Employee -> Department foreign key without the input file carrying department ids.
 *
 * A name -> Department cache avoids a DB lookup for every row (departments repeat heavily).
 * The cached Department keeps its real id, which is all JPA needs to write department_id on an
 * Employee (the @ManyToOne has no cascade). Assumes one load runs at a time (true for this app).
 */
@Service
@Slf4j
public class DepartmentService {

    private final DepartmentRepository repository;
    private final Map<String, Department> cache = new ConcurrentHashMap<>();

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Department getOrCreate(String name) {
        String key = (name == null) ? "" : name.trim();
        Department cached = cache.get(key);
        if (cached != null) {
            log.info("CACHE HIT.<" + "-".repeat(10));
            return cached;
        } else {
            log.info("CACHE MISS.<" + "-".repeat(10));
        }

        Department department = repository.findByName(key)
                .orElseGet(() -> repository.save(new Department(key)));
        cache.put(key, department);
        return department;
    }
}
