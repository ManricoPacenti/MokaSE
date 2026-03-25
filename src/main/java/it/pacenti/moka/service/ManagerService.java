package it.pacenti.moka.service;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.repository.EmployeeRepository;
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
 * Application service exposing the operations available to the manager.
 * It orchestrates use cases and delegates domain-specific logic
 * to domain objects, repositories, and specialized services.
 */
public class ManagerService {

    private static final Logger LOGGER = Logger.getLogger(ManagerService.class.getName());

    private final EmployeeRepository employeeRepository;
    private final EmployeeFactory employeeFactory;
    private final ShiftScheduler scheduler;

    private final List<LeaveRequest> leaveRequests;

    private WeeklyScheduleTemplate template;
    private WeeklySchedule currentSchedule;
    private int nextLeaveRequestId;

    public ManagerService(
            EmployeeRepository employeeRepository,
            EmployeeFactory employeeFactory,
            ShiftScheduler scheduler
    ) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "Employee repository cannot be null");
        this.employeeFactory = Objects.requireNonNull(employeeFactory, "Employee factory cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Scheduler cannot be null");

        this.leaveRequests = new ArrayList<>();
        this.nextLeaveRequestId = 1;
    }

    public Employee createEmployee(String name, Priority priority, int agreedHours, int hourlyCost) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(priority, "Priority cannot be null");

        String normalizedName = name.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        if (employeeRepository.existsByName(normalizedName)) {
            throw new IllegalArgumentException("An employee with this name already exists: " + normalizedName);
        }

        Employee employee = employeeFactory.createEmployee(normalizedName, priority, agreedHours, hourlyCost);
        employeeRepository.save(employee);

        LOGGER.info("Created employee: " + employee.getName());
        return employee;
    }

    public void addSkill(Employee employee, Skill skill, Proficiency proficiency) {
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(skill, "Skill cannot be null");
        Objects.requireNonNull(proficiency, "Proficiency cannot be null");

        managedEmployee.getSkills().addOrUpdate(skill, proficiency);
        employeeRepository.save(managedEmployee);

        LOGGER.info("Added/updated skill " + skill + " for employee " + managedEmployee.getName());
    }

    public void addUnavailability(Employee employee, DayOfWeek day, TimeRange range) {
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "Time range cannot be null");

        managedEmployee.getAvailability().addTimeOff(day, range);
        employeeRepository.save(managedEmployee);

        LOGGER.info("Added unavailability for " + managedEmployee.getName());
    }

    public void addLeave(Employee employee, Leave leave) {
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(leave, "Leave cannot be null");

        managedEmployee.addLeave(leave);
        employeeRepository.save(managedEmployee);

        LOGGER.info("Added leave for " + managedEmployee.getName());
    }

    public void createTemplate(LocalDate weekStart) {
        this.template = new WeeklyScheduleTemplate(
                Objects.requireNonNull(weekStart, "Week start cannot be null")
        );
        LOGGER.info("Created new weekly template for week starting " + weekStart);
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

    public boolean removeTemplateSlot(ShiftSlot slot) {
        ensureTemplateExists();
        boolean removed = template.removeSlot(Objects.requireNonNull(slot, "Shift slot cannot be null"));

        if (removed) {
            LOGGER.info("Removed slot from template: " + slot);
        }

        return removed;
    }

    public WeeklySchedule generateSchedule() {
        ensureTemplateExists();

        currentSchedule = scheduler.generateSchedule(template, employeeRepository.findAll());
        LOGGER.info("Generated weekly schedule");

        return currentSchedule;
    }

    public WeeklySchedule getCurrentSchedule() {
        return currentSchedule;
    }

    public List<Employee> getEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> findEmployeeByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return employeeRepository.findByName(name);
    }

    public LeaveRequest submitLeaveRequest(Employee employee, Leave leave) {
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(leave, "Leave cannot be null");

        LeaveRequest request = new LeaveRequest(nextLeaveRequestId++, managedEmployee, leave);
        leaveRequests.add(request);

        LOGGER.info("Leave request submitted by " + managedEmployee.getName());
        return request;
    }

    public List<LeaveRequest> getPendingRequests() {
        List<LeaveRequest> result = new ArrayList<>();

        for (LeaveRequest request : leaveRequests) {
            if (request.getStatus() == RequestStatus.PENDING) {
                result.add(request);
            }
        }

        return Collections.unmodifiableList(result);
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return Collections.unmodifiableList(new ArrayList<>(leaveRequests));
    }

    public void approveLeaveRequest(int requestId) {
        LeaveRequest request = getLeaveRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Leave request is not pending: " + requestId);
        }

        request.getEmployee().addLeave(request.getLeave());
        request.approve();

        employeeRepository.save(request.getEmployee());

        LOGGER.info("Approved leave request n° " + requestId);
    }

    public void rejectLeaveRequest(int requestId) {
        LeaveRequest request = getLeaveRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Leave request is not pending: " + requestId);
        }

        request.reject();

        LOGGER.info("Rejected leave request n° " + requestId);
    }

    /**
     * Validates a manual assignment and returns warning messages
     * for soft-constraint violations.
     */
    public List<String> validateManualAssignment(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(slot, "Shift slot cannot be null");
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        List<String> warnings = new ArrayList<>();

        if (schedule.getRemainingHours(managedEmployee) < slot.durationMinutes() / 60.0) {
            warnings.add("Employee exceeds agreed weekly hours");
        }

        long assignedToday = schedule.getAssignedMinutesFor(managedEmployee, slot.getDay());
        if (assignedToday + slot.durationMinutes() > 8 * 60) {
            warnings.add("Employee exceeds daily working hours");
        }

        if (!managedEmployee.getAvailability().isAvailable(slot)) {
            warnings.add("Employee is unavailable for this time range");
        }

        return warnings;
    }

    /**
     * Forces a manual assignment after hard constraints
     * have already been checked by the caller.
     */
    public void forceAssign(ShiftSlot slot, Employee employee, WeeklySchedule schedule) {
        Objects.requireNonNull(slot, "Shift slot cannot be null");
        Employee managedEmployee = requireManagedEmployee(employee);
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        schedule.assign(slot, managedEmployee);
        LOGGER.info("Force-assigned " + managedEmployee.getName() + " to slot " + slot);
    }

    private LeaveRequest getLeaveRequestById(int requestId) {
        return findLeaveRequestById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));
    }

    private Optional<LeaveRequest> findLeaveRequestById(int requestId) {
        for (LeaveRequest request : leaveRequests) {
            if (request.getId() == requestId) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    private Employee requireManagedEmployee(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");

        return employeeRepository.findByName(employee.getName())
                .orElseThrow(() -> new IllegalArgumentException("Employee is not managed by this service"));
    }

    private void ensureTemplateExists() {
        if (template == null) {
            throw new IllegalStateException("Template has not been created yet");
        }
    }
}