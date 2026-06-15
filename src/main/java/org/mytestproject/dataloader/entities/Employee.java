package org.mytestproject.dataloader.entities;

import jakarta.persistence.*;

@Entity
@Table(name="Employee")
public class Employee {

    public Employee(String employeeName, String role, Long salary, String email, Department department){
        this.employeeName = employeeName;
        this.role = role;
        this.salary = salary;
        this.email = email;
        this.department = department;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emp_Name")
    private String employeeName;

    @Column(name = "emp_Role")
    private String role;

    @Column(name = "emp_Salary")
    private Long salary;

    @Column(name = "emp_Email")
    private String email;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

   public Employee() {

    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getSalary() {
        return salary;
    }

    public void setSalary(Long salary) {
        this.salary = salary;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
}
