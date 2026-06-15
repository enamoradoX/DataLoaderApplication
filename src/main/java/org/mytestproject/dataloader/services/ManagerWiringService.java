package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.repositories.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Second pass of the load: re-reads the file and sets each loaded employee's manager FK from the
 * managerId column. This is its own step because a manager can appear LATER in the file than the
 * employee who reports to them, so the link can't be resolved during the first (load) pass.
 *
 * Shared by both load strategies — the batch job's managerWiringStep and the legacy loader.
 */
@Service
@Slf4j
public class ManagerWiringService {

    @Value("classpath:data.txt")
    private Resource dataFile;

    private final EmployeeRepository employeeRepository;

    public ManagerWiringService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** Returns the number of manager links successfully wired. */
    @Transactional
    public int wireManagers() {
        int wired = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataFile.getInputStream(), StandardCharsets.UTF_8));
             Stream<String> lines = reader.lines()) {

            java.util.Iterator<String> iterator = lines.skip(1).iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.isBlank()) continue;

                // id,employeeName,email,department,role,managerId,salary
                String[] row = line.split(",");
                if (row.length != 7) continue; // malformed rows were already skipped during the load

                String managerIdStr = row[5].trim();
                if (managerIdStr.isEmpty()) continue; // top of the org, no manager

                Integer employeeNumber;
                Integer managerNumber;
                try {
                    employeeNumber = Integer.valueOf(row[0].trim());
                    managerNumber = Integer.valueOf(managerIdStr);
                } catch (NumberFormatException e) {
                    continue; // bad ids were handled during the load pass
                }

                Optional<Employee> employeeOpt = employeeRepository.findByEmployeeNumber(employeeNumber);
                if (employeeOpt.isEmpty()) {
                    continue; // this employee wasn't loaded (e.g. it was skipped), nothing to wire
                }
                Optional<Employee> managerOpt = employeeRepository.findByEmployeeNumber(managerNumber);
                if (managerOpt.isEmpty()) {
                    log.warn("Manager {} for employee {} was not loaded; leaving manager unset.",
                            managerNumber, employeeNumber);
                    continue;
                }

                Employee employee = employeeOpt.get();
                employee.setManager(managerOpt.get());
                employeeRepository.save(employee);
                wired++;
            }
        } catch (Exception e) {
            log.error("Failed during manager wiring pass: {}", e.getMessage());
        }
        log.info("Manager wiring complete: {} manager link(s) set.", wired);
        return wired;
    }
}
