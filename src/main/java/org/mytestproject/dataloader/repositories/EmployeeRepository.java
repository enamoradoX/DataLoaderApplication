package org.mytestproject.dataloader.repositories;

import org.mytestproject.dataloader.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends  JpaRepository<Employee, Long> {
}
