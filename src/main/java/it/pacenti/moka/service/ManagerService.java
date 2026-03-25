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
import it.pacenti.moka.exception.TemplateNotInitializedException;
import it.pacenti.moka.exception.UnmanagedEmployeeException;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;
import it.pacenti.moka.scheduling.ShiftScheduler;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklySchedule;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Application service that exposes the operations available to the manager.
 * It orchestrates application use cases and delegates business rules
 * to domain objects, repositories, and specialized services.
 */
public class ManagerService {

    private static final Logger LOGGER = Logger.getLogger(ManagerService.class.getName());
    private static final int MAX_DAILY_MINUTES = 8 * 60;

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeFactory employeeFactory;
    private final ShiftScheduler scheduler;

    private WeeklyScheduleTemplate template;
    private WeeklySchedule currentSchedule;

    public ManagerService(EmployeeRepository employeeRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          EmployeeFactory employeeFactory,
                          ShiftScheduler scheduler) {
        this.employeeRepository = Objects.requireNonNull(
                employeeRepository, "Employee repository cannot be null"
        );
        this.leaveRequestRepository = Objects.requireNonNull(
                leaveRequestRepository, "Leave request repository cannot be null"
        );
        this.employeeFactory = Objects.requireNonNull(
                employeeFactory, "Employee factory cannot be null"
        );
        this.scheduler = Objects.requireNonNull(
                scheduler, "Scheduler cannot be null"
        );
    }

    // =========================================================
    // EMPLOYEE MANAGEMENT
    // =========================================================

    public Employee createEmployee(String name, Priority priority, int agreedHours, int hourlyCost) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(priority, "Priority cannot be null");

        String normalizedName = normalizeName(name);

        if (employeeRepository.existsByName(normalizedName)) {
            throw new DuplicateEmployeeException(normalizedName);
        }

        Employee employee = employeeFactory.createEmployee(
                normalizedName,
                priority,
                agreedHours,
                hourlyCost
        );

        employeeRepository.save(employee);
        LOGGER.info("Created employee: " + employee.getName());

        return employee;
    }

    public List<Employee> getEmployees() {
        return Collections.unmodifiableList(new ArrayList<>(employeeRepository.findAll()));
    }

    public Optional<Employee> findEmployeeByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return employeeRepository.findByName(normalizeName(name));
    }

    public Employee getEmployeeByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalizedName = normalizeName(name);

        return employeeRepository.findByName(normalizedName)
                .orElseThrow(() -> new EmployeeNotFoundException(normalizedName));
    }

    public void addSkill(String employeeName, Skill skill, Proficiency proficiency) {
        Employee employee = getEmployeeByName(employeeName);
        Objects.requireNonNull(skill, "Skill cannot be null");
        Objects.requireNonNull(proficiency, "Proficiency cannot be null");

        employee.getSkills().addOrUpdate(skill, proficiency);
        employeeRepository.save(employee);

        LOGGER.info("Added/updated skill " + skill + " for employee " + employee.getName());
    }

    public void addSkill(Employee employee, Skill skill, Proficiency proficiency) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        addSkill(employee.getName(), skill, proficiency);
    }

    public void addUnavailability(String employeeName, DayOfWeek day, TimeRange range) {
        Employee employee = getEmployeeByName(employeeName);
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "Time range cannot be null");

        employee.getAvailability().addTimeOff(day, range);
        employeeRepository.save(employee);

        LOGGER.info("Added unavailability for " + employee.getName()
                + " on " + day + " during " + range);
    }

    public void addUnavailability(Employee employee, DayOfWeek day, TimeRange range) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        addUnavailability(employee.getName(), day, range);
    }

    public void addLeave(String employeeName, Leave leave) {
        Employee employee = getEmployeeByName(employeeName);
        Objects.requireNonNull(leave, "Leave cannot be null");

        employee.addLeave(leave);
        employeeRepository.save(employee);

        LOGGER.info("Added leave for " + employee.getName());
    }

    public void addLeave(Employee employee, Leave leave) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        addLeave(employee.getName(), leave);
    }

    // =========================================================
    // TEMPLATE MANAGEMENT
    // =========================================================

    public WeeklyScheduleTemplate createTemplate(LocalDate weekStart) {
        Objects.requireNonNull(weekStart, "Week start cannot be null");

        this.template = new WeeklyScheduleTemplate(weekStart);
        LOGGER.info("Created new weekly template for week starting " + weekStart);

        return template;
    }

    public void setTemplate(WeeklyScheduleTemplate template) {
        this.template = Objects.requireNonNull(template, "Template cannot be null");
        LOGGER.info("Template set");
    }

    public WeeklyScheduleTemplate getTemplate() {
        return template;
    }

    public void addTemplateSlot(ShiftSlot slot) {
        ensureTemplateExists();
        template.addSlot(Objects.requireNonNull(slot, "Slot cannot be null"));

        LOGGER.info("Added slot to template: " + slot);
    }

    public void addTemplateSlot(DayOfWeek day, TimeRange range, Skill requiredSkill) {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "Time range cannot be null");
        Objects.requireNonNull(requiredSkill, "Required skill cannot be null");

        addTemplateSlot(new ShiftSlot(day, range, requiredSkill));
    }

    public boolean removeTemplateSlot(ShiftSlot slot) {
        ensureTemplateExists();
        Objects.requireNonNull(slot, "Slot cannot be null");

        boolean removed = template.removeSlot(slot);
        if (removed) {
            LOGGER.info("Removed slot from template: " + slot);
        }

        return removed;
    }

    public boolean removeTemplateSlot(DayOfWeek day, TimeRange range, Skill requiredSkill) {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "Time range cannot be null");
        Objects.requireNonNull(requiredSkill, "Required skill cannot be null");

        return removeTemplateSlot(new ShiftSlot(day, range, requiredSkill));
    }

    // =========================================================
    // SCHEDULING
    // =========================================================

    public WeeklySchedule generateSchedule() {
        ensureTemplateExists();

        List<Employee> employees = employeeRepository.findAll();
        currentSchedule = scheduler.generateSchedule(template, employees);

        LOGGER.info("Generated weekly schedule with " + employees.size() + " employees");
        return currentSchedule;
    }

    public WeeklySchedule generateSchedule(WeeklyScheduleTemplate template) {
        setTemplate(template);
        return generateSchedule();
    }

    public WeeklySchedule getCurrentSchedule() {
        return currentSchedule;
    }

    // =========================================================
    // LEAVE REQUEST WORKFLOW
    // =========================================================

    public LeaveRequest submitLeaveRequest(String employeeName, Leave leave) {
        Employee employee = getEmployeeByName(employeeName);
        Objects.requireNonNull(leave, "Leave cannot be null");

        LeaveRequest request = new LeaveRequest(
                leaveRequestRepository.nextId(),
                employee,
                leave
        );

        leaveRequestRepository.save(request);

        LOGGER.info("Leave request submitted by " + employee.getName()
                + " with request id " + request.getId());

        return request;
    }

    public LeaveRequest submitLeaveRequest(Employee employee, Leave leave) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        return submitLeaveRequest(employee.getName(), leave);
    }

    public List<LeaveRequest> getPendingRequests() {
        return Collections.unmodifiableList(new ArrayList<>(leaveRequestRepository.findPending()));
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return Collections.unmodifiableList(new ArrayList<>(leaveRequestRepository.findAll()));
    }

    public void approveLeaveRequest(int requestId) {
        LeaveRequest request = getPendingLeaveRequestById(requestId);

        Employee employee = request.getEmployee();
        employee.addLeave(request.getLeave());
        request.approve();

        employeeRepository.save(employee);
        leaveRequestRepository.save(request);

        LOGGER.info("Approved leave request n° " + requestId
                + " for employee " + employee.getName());
    }

    public void rejectLeaveRequest(int requestId) {
        LeaveRequest request = getPendingLeaveRequestById(requestId);
        request.reject();

        leaveRequestRepository.save(request);

        LOGGER.info("Rejected leave request n° " + requestId
                + " for employee " + request.getEmployee().getName());
    }

    // =========================================================
    // MANUAL ASSIGNMENT SUPPORT
    // =========================================================

    /**
     * Validates a manual assignment and returns warning messages
     * for soft-constraint violations.
     *
     * Hard constraints should still be checked by the caller before forcing
     * the assignment, or by the domain model during assign().
     */
    public List<String> validateManualAssignment(Employee employee,
                                                 ShiftSlot slot,
                                                 WeeklySchedule schedule) {
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(slot, "Shift slot cannot be null");
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        List<String> warnings = new ArrayList<>();

        double slotHours = slot.durationMinutes() / 60.0;

        if (schedule.getRemainingHours(managedEmployee) < slotHours) {
            warnings.add("Employee exceeds agreed weekly hours");
        }

        long assignedToday = schedule.getAssignedMinutesFor(managedEmployee, slot.getDay());
        if (assignedToday + slot.durationMinutes() > MAX_DAILY_MINUTES) {
            warnings.add("Employee exceeds daily working hours");
        }

        if (!managedEmployee.getAvailability().isAvailable(slot)) {
            warnings.add("Employee is unavailable for this time range");
        }

        return warnings;
    }

    /**
     * Forces a manual assignment after hard constraints have been checked.
     */
    public void forceAssign(ShiftSlot slot, Employee employee, WeeklySchedule schedule) {
        Objects.requireNonNull(slot, "Shift slot cannot be null");
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        Employee managedEmployee = requireManagedEmployee(employee);
        schedule.assign(slot, managedEmployee);

        LOGGER.info("Force-assigned " + managedEmployee.getName() + " to slot " + slot);
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private LeaveRequest getPendingLeaveRequestById(int requestId) {
        LeaveRequest request = getLeaveRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidLeaveRequestStateException(requestId, request.getStatus());
        }

        return request;
    }

    private LeaveRequest getLeaveRequestById(int requestId) {
        return leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(requestId));
    }

    private Employee requireManagedEmployee(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");

        String normalizedName = normalizeName(employee.getName());

        return employeeRepository.findByName(normalizedName)
                .orElseThrow(() -> new UnmanagedEmployeeException(normalizedName));
    }

    private void ensureTemplateExists() {
        if (template == null) {
            throw new TemplateNotInitializedException();
        }
    }

    private String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        return normalized;
    }
}