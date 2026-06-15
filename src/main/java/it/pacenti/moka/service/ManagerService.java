package it.pacenti.moka.service;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.exception.DuplicateEmployeeException;
import it.pacenti.moka.exception.EmployeeNotFoundException;
import it.pacenti.moka.exception.InvalidLeaveRequestStateException;
import it.pacenti.moka.exception.LeaveRequestNotFoundException;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;
import it.pacenti.moka.scheduling.ShiftScheduler;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklySchedule;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Application service responsible for manager use cases.
 *
 * This class orchestrates:
 * - employee creation and retrieval
 * - employee updates through explicit use cases
 * - leave request submission
 * - leave request approval / rejection
 * - employee enrichment (skills / weekly unavailability)
 * - schedule generation
 *
 * Business invariants remain inside the domain model.
 * Persistence concerns remain inside repositories.
 */
public class ManagerService {

    private static final Logger LOGGER = Logger.getLogger(ManagerService.class.getName());

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeFactory employeeFactory;
    private final ShiftScheduler shiftScheduler;

    public ManagerService(EmployeeRepository employeeRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          EmployeeFactory employeeFactory,
                          ShiftScheduler shiftScheduler) {
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
        this.shiftScheduler = Objects.requireNonNull(
                shiftScheduler,
                "Shift scheduler cannot be null"
        );
    }

    public ManagerService(EmployeeRepository employeeRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          EmployeeFactory employeeFactory) {
        this(
                employeeRepository,
                leaveRequestRepository,
                employeeFactory,
                new ShiftScheduler()
        );
    }

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

    public Optional<Employee> findEmployee(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return employeeRepository.findByName(normalizeName(name));
    }

    public Employee getEmployeeByName(String name) {
        return getRequiredEmployee(name);
    }

    public List<Employee> getAllEmployees() {
        return List.copyOf(employeeRepository.findAll());
    }

    public void addSkillToEmployee(String employeeName, Skill skill, Proficiency proficiency) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");
        Objects.requireNonNull(skill, "Skill cannot be null");
        Objects.requireNonNull(proficiency, "Proficiency cannot be null");

        Employee employee = getRequiredEmployee(employeeName);
        employee.addOrUpdateSkill(skill, proficiency);
        employeeRepository.save(employee);

        LOGGER.info("Added/updated skill " + skill
                + " with proficiency " + proficiency
                + " for employee " + employee.getName());
    }

    public void removeSkillFromEmployee(String employeeName, Skill skill) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");
        Objects.requireNonNull(skill, "Skill cannot be null");

        Employee employee = getRequiredEmployee(employeeName);
        employee.removeSkill(skill);
        employeeRepository.save(employee);

        LOGGER.info("Removed skill " + skill + " from employee " + employee.getName());
    }

    public void changeEmployeePriority(String employeeName, Priority newPriority) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");
        Objects.requireNonNull(newPriority, "Priority cannot be null");

        Employee employee = getRequiredEmployee(employeeName);
        employee.changePriority(newPriority);
        employeeRepository.save(employee);

        LOGGER.info("Changed priority for employee " + employee.getName()
                + " to " + newPriority);
    }

    public void deleteEmployee(String employeeName) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");

        Employee employee = getRequiredEmployee(employeeName);
        employeeRepository.deleteByName(employee.getName());

        LOGGER.info("Deleted employee: " + employee.getName());
    }

    public void addWeeklyTimeOff(String employeeName, DayOfWeek day, TimeRange range) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "Time range cannot be null");

        Employee employee = getRequiredEmployee(employeeName);
        employee.getAvailability().addTimeOff(day, range);
        employeeRepository.save(employee);

        LOGGER.info("Added weekly time off for employee " + employee.getName()
                + " on " + day + " in range " + range);
    }

    public void addFullDayOff(String employeeName, DayOfWeek day) {
        Objects.requireNonNull(employeeName, "Employee name cannot be null");
        Objects.requireNonNull(day, "Day cannot be null");

        Employee employee = getRequiredEmployee(employeeName);
        employee.getAvailability().addFullDayOff(day);
        employeeRepository.save(employee);

        LOGGER.info("Added full day off for employee " + employee.getName()
                + " on " + day);
    }

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

    public List<LeaveRequest> getPendingRequests() {
        return List.copyOf(leaveRequestRepository.findPending());
    }

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

    public void rejectRequest(int requestId) {
        LeaveRequest request = getRequiredRequest(requestId);
        ensurePending(request);

        request.reject();
        leaveRequestRepository.save(request);

        LOGGER.info("Rejected leave request " + requestId
                + " for employee " + request.getEmployee().getName());
    }

    public WeeklySchedule generateSchedule(WeeklyScheduleTemplate template) {
        Objects.requireNonNull(template, "Weekly schedule template cannot be null");

        List<Employee> employees = employeeRepository.findAll();
        WeeklySchedule schedule = shiftScheduler.generateSchedule(template, employees);

        LOGGER.info("Generated schedule for week starting " + template.getWeekStart());
        return schedule;
    }

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

        return normalized.toLowerCase();
    }
}