package org.mytestproject.dataloader.entities;

import jakarta.persistence.*;

@Entity
@Table(name="Employee")
public class Employee {

    public Employee(Integer employeeNumber, String employeeName, JobTitle jobTitle, Long salary, String email, Department department){
        this.employeeNumber = employeeNumber;
        this.employeeName = employeeName;
        this.jobTitle = jobTitle;
        this.salary = salary;
        this.email = email;
        this.department = department;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The business id from the source file (e.g. 2001). Distinct from the generated PK; used to
    // resolve the manager self-reference by the manager's business id.
    @Column(name = "employee_number", unique = true)
    private Integer employeeNumber;

    @Column(name = "emp_Name")
    private String employeeName;

    // Self-referencing FK: this employee's manager (another Employee). Null for the top of the org.
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @ManyToOne
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

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

    public Integer getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(Integer employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public Employee getManager() {
        return manager;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public JobTitle getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(JobTitle jobTitle) {
        this.jobTitle = jobTitle;
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
