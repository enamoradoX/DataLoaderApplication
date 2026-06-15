package org.mytestproject.dataloader.repositories;

import org.mytestproject.dataloader.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends  JpaRepository<Employee, Long> {

    // Used by the round-3 manager-wiring step to resolve a manager by their business id.
    Optional<Employee> findByEmployeeNumber(Integer employeeNumber);
}
