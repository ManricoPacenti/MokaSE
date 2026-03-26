package it.pacenti.moka.service;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.exception.DuplicateEmployeeException;
import it.pacenti.moka.exception.EmployeeNotFoundException;
import it.pacenti.moka.exception.InvalidLeaveRequestStateException;
import it.pacenti.moka.exception.LeaveRequestNotFoundException;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Application service responsible for manager use cases.
 *
 * This class orchestrates:
 * - employee creation and retrieval
 * - leave request submission
 * - leave request approval / rejection
 *
 * Business invariants remain inside the domain model.
 * Persistence concerns remain inside repositories.
 */
public class ManagerService {

    private static final Logger LOGGER = Logger.getLogger(ManagerService.class.getName());

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeFactory employeeFactory;

    public ManagerService(EmployeeRepository employeeRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          EmployeeFactory employeeFactory) {
        this.employeeRepository = Objects.requireNonNull(
                employeeRepository,
                "Employee repository cannot be null"
        );
        this.leaveRequestRepository = Objects.requireNonNull(
                leaveRequestRepository,
                "Leave request repository cannot be null"
        );
        this.employeeFactory = Objects.requireNonNull(
                employeeFactory,
                "Employee factory cannot be null"
        );
    }

    /**
     * Creates a new employee in a valid initial state and persists it.
     *
     * @throws DuplicateEmployeeException if an employee with the same normalized name already exists
     */
    public Employee createEmployee(String name,
                                   Priority priority,
                                   int agreedHours,
                                   int hourlyCost) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(priority, "Priority cannot be null");

        String normalizedName = normalizeName(name);

        if (employeeRepository.existsByName(normalizedName)) {
            throw new DuplicateEmployeeException(normalizedName);
        }

        Employee employee = employeeFactory.createEmployee(
                name,
                priority,
                agreedHours,
                hourlyCost
        );

        employeeRepository.save(employee);

        LOGGER.info("Created employee: " + employee.getName());
        return employee;
    }

    /**
     * Finds an employee by name.
     *
     * @return Optional containing the employee if found, otherwise empty
     */
    public Optional<Employee> findEmployee(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return employeeRepository.findByName(normalizeName(name));
    }

    /**
     * Returns all managed employees.
     */
    public List<Employee> getAllEmployees() {
        return List.copyOf(employeeRepository.findAll());
    }

    /**
     * Creates and persists a new leave request for an existing employee.
     *
     * @throws EmployeeNotFoundException if the employee does not exist
     */
    public LeaveRequest createLeaveRequest(String employeeName, Leave leave) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");
        Objects.requireNonNull(leave, "Leave cannot be null");

        Employee employee = getRequiredEmployee(employeeName);

        LeaveRequest request = new LeaveRequest(
                leaveRequestRepository.nextId(),
                employee,
                leave
        );

        leaveRequestRepository.save(request);

        LOGGER.info("Created leave request " + request.getId()
                + " for employee " + employee.getName());

        return request;
    }

    /**
     * Returns all pending leave requests.
     */
    public List<LeaveRequest> getPendingRequests() {
        return List.copyOf(leaveRequestRepository.findPending());
    }

    /**
     * Approves a pending request.
     *
     * Approval workflow:
     * 1. request must exist
     * 2. request must be pending
     * 3. leave is added to employee
     * 4. request status becomes APPROVED
     * 5. both employee and request are persisted
     *
     * Important:
     * employee.addLeave(...) is intentionally executed before request.approve().
     * If the leave conflicts with existing approved leaves, the domain will throw
     * and the request will remain PENDING.
     *
     * @throws LeaveRequestNotFoundException if the request does not exist
     * @throws InvalidLeaveRequestStateException if the request is not pending
     */
    public void approveRequest(int requestId) {
        LeaveRequest request = getRequiredRequest(requestId);
        ensurePending(request);

        Employee employee = request.getEmployee();

        employee.addLeave(request.getLeave());
        request.approve();

        employeeRepository.save(employee);
        leaveRequestRepository.save(request);

        LOGGER.info("Approved leave request " + requestId
                + " for employee " + employee.getName());
    }

    /**
     * Rejects a pending request and persists the new state.
     *
     * @throws LeaveRequestNotFoundException if the request does not exist
     * @throws InvalidLeaveRequestStateException if the request is not pending
     */
    public void rejectRequest(int requestId) {
        LeaveRequest request = getRequiredRequest(requestId);
        ensurePending(request);

        request.reject();
        leaveRequestRepository.save(request);

        LOGGER.info("Rejected leave request " + requestId
                + " for employee " + request.getEmployee().getName());
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private Employee getRequiredEmployee(String employeeName) {
        String normalizedName = normalizeName(employeeName);

        return employeeRepository.findByName(normalizedName)
                .orElseThrow(() -> new EmployeeNotFoundException(normalizedName));
    }

    private LeaveRequest getRequiredRequest(int requestId) {
        return leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));
    }

    private void ensurePending(LeaveRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidLeaveRequestStateException(
                    request.getId(),
                    request.getStatus()
            );
        }
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalized = name.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        return normalized;
    }

    //Alias bridge-method:

    public Optional<Employee> findEmployeeByName(String name) {
        return findEmployee(name);
    }

    public Employee getEmployeeByName(String name) {
        return findEmployee(name)
                .orElseThrow(() -> new EmployeeNotFoundException(normalizeName(name)));
    }

    public LeaveRequest submitLeaveRequest(String employeeName, Leave leave) {
        return createLeaveRequest(employeeName, leave);
    }

    public LeaveRequest submitLeaveRequest(Employee employee, Leave leave) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        return createLeaveRequest(employee.getName(), leave);
    }

    public void approveLeaveRequest(int requestId) {
        approveRequest(requestId);
    }

    public void rejectLeaveRequest(int requestId) {
        rejectRequest(requestId);
    }

    //Save Applicativo
    public void saveEmployee(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        employeeRepository.save(employee);
    }
}