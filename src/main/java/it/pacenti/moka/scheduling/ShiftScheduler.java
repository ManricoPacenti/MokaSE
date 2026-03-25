package it.pacenti.moka.scheduling;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service responsible for generating a weekly schedule from
 * a weekly schedule template and a list of employees.
 */
public class ShiftScheduler {

    private static final Logger LOGGER = Logger.getLogger(ShiftScheduler.class.getName());
    private static final int MAX_DAILY_MINUTES = 8 * 60;

    /**
     * Generates the final weekly schedule starting from a template.
     *
     * @param template the weekly schedule template
     * @param employees the employees available for scheduling
     * @return the generated weekly schedule
     */
    public WeeklySchedule generateSchedule(WeeklyScheduleTemplate template, List<Employee> employees) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(employees, "Employee list cannot be null");

        WeeklySchedule schedule = new WeeklySchedule(template.getWeekStart());
        schedule.addSlots(template.getSlots());

        List<ShiftSlot> orderedSlots = sortSlots(template.getSlots());

        for (ShiftSlot slot : orderedSlots) {
            assignBestEmployee(slot, schedule, employees);
        }

        LOGGER.info("Schedule generation completed");
        return schedule;
    }

    /**
     * Sorts slots according to the business priority of days
     * and then by start time.
     */
    public List<ShiftSlot> sortSlots(List<ShiftSlot> slots) {
        Objects.requireNonNull(slots, "Slots cannot be null");

        List<ShiftSlot> ordered = new ArrayList<>(slots);

        ordered.sort(
                Comparator.comparingInt((ShiftSlot slot) -> getDayPriority(slot.getDay()))
                        .thenComparing(slot -> slot.getRange().getStart())
        );

        return ordered;
    }

    /**
     * Assigns the best available employee to the given slot.
     * If no candidate is found, the slot remains unassigned.
     */
    public void assignBestEmployee(ShiftSlot slot, WeeklySchedule schedule, List<Employee> employees) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");
        Objects.requireNonNull(employees, "Employees cannot be null");

        List<Employee> candidates = findEligibleEmployees(slot, schedule, employees);

        if (candidates.isEmpty()) {
            LOGGER.warning("No eligible employee found for slot: " + slot);
            return;
        }

        Optional<Employee> selected = selectBestEmployee(slot, candidates, schedule);

        if (selected.isPresent()) {
            Employee employee = selected.get();
            schedule.assign(slot, employee);
            applyOpeningRule(slot, employee, schedule);
            LOGGER.info("Assigned " + employee.getName() + " to slot " + slot);
        } else {
            LOGGER.warning("No employee selected for slot: " + slot);
        }
    }

    /**
     * Returns all employees that can legally be assigned to the given slot.
     */
    public List<Employee> findEligibleEmployees(ShiftSlot slot, WeeklySchedule schedule, List<Employee> employees) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");
        Objects.requireNonNull(employees, "Employees cannot be null");

        List<Employee> eligible = new ArrayList<>();
        LocalDate slotDate = schedule.getDateFor(slot);

        for (Employee employee : employees) {
            if (!employee.isAssignableTo(slot, slotDate)) {
                continue;
            }

            if (schedule.hasOverlappingAssignment(employee, slot)) {
                continue;
            }

            if (schedule.getRemainingHours(employee) < slot.durationMinutes() / 60.0) {
                continue;
            }

            long assignedToday = schedule.getAssignedMinutesFor(employee, slot.getDay());
            if (assignedToday + slot.durationMinutes() > MAX_DAILY_MINUTES) {
                continue;
            }

            eligible.add(employee);
        }

        return eligible;
    }

    /**
     * Selects the best employee among the eligible candidates.
     * PRIORITY ORDER:
     * 1. Skill proficiency
     * 2. Employee priority
     * 3. Remaining weekly hours
     */
    public Optional<Employee> selectBestEmployee(ShiftSlot slot, List<Employee> candidates, WeeklySchedule schedule) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(candidates, "Candidates cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");

        return candidates.stream()
                .max(
                        Comparator
                                .comparingInt((Employee employee) -> getProficiencyScore(employee, slot.getRequiredSkill()))
                                .thenComparingInt(employee -> getPriorityScore(employee.getPriority()))
                                .thenComparingDouble(schedule::getRemainingHours)
                );
    }

    /**
     * Applies the OPENING business rule:
     * if the assigned slot is OPENING and the selected employee also has BAR,
     * then parallel BAR slots can be changed to WAITER.
     */
    public void applyOpeningRule(ShiftSlot assignedSlot, Employee assignedEmployee, WeeklySchedule schedule) {
        Objects.requireNonNull(assignedSlot, "Assigned slot cannot be null");
        Objects.requireNonNull(assignedEmployee, "Assigned employee cannot be null");
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        if (assignedSlot.getRequiredSkill() != Skill.OPENING) {
            return;
        }

        if (!assignedEmployee.hasSkill(Skill.BAR)) {
            return;
        }

        for (ShiftSlot slot : schedule.getSlots()) {
            if (slot == assignedSlot) {
                continue;
            }

            if (slot.getRequiredSkill() == Skill.BAR && slot.sameMomentAs(assignedSlot)) {
                slot.setRequiredSkill(Skill.WAITER);
            }
        }
    }

    private int getDayPriority(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> 1;
            case SUNDAY -> 2;
            case FRIDAY -> 3;
            case THURSDAY -> 4;
            case WEDNESDAY -> 5;
            case TUESDAY -> 6;
            case MONDAY -> 7;
        };
    }

    private int getProficiencyScore(Employee employee, Skill skill) {
        Proficiency proficiency = employee.getSkills().getProficiency(skill);
        return proficiency != null ? proficiency.ordinal() : -1;
    }

    private int getPriorityScore(Priority priority) {
        return priority.ordinal();
    }
}