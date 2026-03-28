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
 *
 * Scheduling strategy:
 * 1. assign critical slots first with this order:
 *    RESP -> OPENING -> BAR -> KITCHEN -> WAITER -> RUNNER
 * 2. for each slot, select the best legal employee by:
 *    - proficiency on required skill
 *    - weekly availability breadth
 *    - remaining agreed hours to complete
 *    - employee priority
 *    - RESP cross-role bonus on other skills
 *
 * This is intentionally heuristic, readable and explainable.
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

        List<ShiftSlot> remainingSlots = new ArrayList<>(template.getSlots());

        while (!remainingSlots.isEmpty()) {
            ShiftSlot nextSlot = selectNextSlot(remainingSlots, schedule, employees);
            assignBestEmployee(nextSlot, schedule, employees);
            remainingSlots.remove(nextSlot);
        }

        LOGGER.info("Schedule generation completed");
        return schedule;
    }

    /**
     * Public deterministic sorting method useful for previews/debug/tests.
     */
    public List<ShiftSlot> sortSlots(List<ShiftSlot> slots) {
        Objects.requireNonNull(slots, "Slots cannot be null");

        List<ShiftSlot> ordered = new ArrayList<>(slots);

        ordered.sort(
                Comparator.comparingInt((ShiftSlot slot) -> getBusinessSkillPriority(slot.getRequiredSkill()))
                        .thenComparingInt(slot -> getDayPriority(slot.getDay()))
                        .thenComparingInt(slot -> getEligibleCountForPreview(slot))
                        .thenComparingLong(slot -> -slot.durationMinutes())
                        .thenComparing(slot -> slot.getRange().getStart())
        );

        return ordered;
    }

    /**
     * Selects the next slot to assign.
     *
     * Primary rule:
     * 1. business-critical skill order first
     * 2. more critical day first
     * 3. fewer eligible employees first
     * 4. longer slot first
     * 5. earlier start first
     */
    private ShiftSlot selectNextSlot(List<ShiftSlot> remainingSlots,
                                     WeeklySchedule schedule,
                                     List<Employee> employees) {
        return remainingSlots.stream()
                .min(
                        Comparator.comparingInt((ShiftSlot slot) ->
                                        getBusinessSkillPriority(slot.getRequiredSkill()))
                                .thenComparingInt(slot -> getDayPriority(slot.getDay()))
                                .thenComparingInt(slot ->
                                        findEligibleEmployees(slot, schedule, employees).size())
                                .thenComparingLong(slot -> -slot.durationMinutes())
                                .thenComparing(slot -> slot.getRange().getStart())
                )
                .orElseThrow(() -> new IllegalStateException("No slot available for selection"));
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
     * Public selector used also externally.
     * Uses the main scoring strategy.
     */
    public Optional<Employee> selectBestEmployee(ShiftSlot slot, List<Employee> candidates, WeeklySchedule schedule) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(candidates, "Candidates cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");

        return candidates.stream()
                .max(
                        Comparator.comparingInt((Employee employee) ->
                                        calculateCandidateScore(employee, slot, schedule))
                                .thenComparingInt(employee ->
                                        getProficiencyScore(employee, slot.getRequiredSkill()))
                                .thenComparingInt(employee ->
                                        getEmployeePriorityScore(employee.getPriority()))
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

    /**
     * Main readable scoring function for candidate selection.
     *
     * Weights chosen to reflect the requested business logic:
     * - required skill proficiency is the strongest factor
     * - weekly availability breadth matters
     * - remaining agreed hours matters
     * - employee priority matters
     * - RESP gets a bonus on non-RESP roles
     * - slight continuity bonus on the same day
     */
    private int calculateCandidateScore(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        int proficiencyScore = getProficiencyScore(employee, slot.getRequiredSkill()) * 100;
        int weeklyAvailabilityScore = getWeeklyAvailabilityScarcityScore(employee) * 12;        int remainingHoursScore = getRemainingHoursScore(employee, schedule) * 10;
        int priorityScore = getEmployeePriorityScore(employee.getPriority()) * 15;
        int respCrossRoleBonus = getRespCrossRoleBonus(employee, slot);
        int sameDayContinuityBonus = getSameDayContinuityBonus(employee, slot, schedule);

        return proficiencyScore
                + weeklyAvailabilityScore
                + remainingHoursScore
                + priorityScore
                + respCrossRoleBonus
                + sameDayContinuityBonus;
    }

    /**
     * Rewards scarcity of weekly availability.
     *
     * Rationale:
     * an employee who can work on fewer days should be preferred
     * on the days when they are actually available, because employees
     * with broader availability can still be used elsewhere in the week.
     */
    private int getWeeklyAvailabilityScarcityScore(Employee employee) {
        int fullDaysOff = employee.getAvailability().getFullDaysOff().size();
        int availableDays = 7 - fullDaysOff;

        if (availableDays <= 0) {
            return 0;
        }

        return 8 - availableDays;
    }

    /**
     * Score based on how many agreed weekly hours are still missing.
     *
     * More remaining hours = higher incentive to assign the employee.
     * Capped to keep the heuristic stable and readable.
     */
    private int getRemainingHoursScore(Employee employee, WeeklySchedule schedule) {
        double remainingHours = schedule.getRemainingHours(employee);

        if (remainingHours <= 0) {
            return 0;
        }

        return Math.min(10, (int) Math.ceil(remainingHours));
    }

    /**
     * Gives RESP employees a transversal advantage on non-RESP roles.
     *
     * Rationale:
     * the responsible role is considered strategically valuable,
     * so employees capable of RESP are also rewarded when covering
     * other roles.
     *
     * The bonus is stronger on OPENING and BAR, lighter elsewhere.
     */
    private int getRespCrossRoleBonus(Employee employee, ShiftSlot slot) {
        if (slot.getRequiredSkill() == Skill.RESP) {
            return 0;
        }

        if (!employee.hasSkill(Skill.RESP)) {
            return 0;
        }

        int respProficiency = getProficiencyScore(employee, Skill.RESP);
        if (respProficiency == 0) {
            return 0;
        }

        return switch (slot.getRequiredSkill()) {
            case OPENING -> respProficiency * 30;
            case BAR -> respProficiency * 24;
            case KITCHEN -> respProficiency * 18;
            case WAITER -> respProficiency * 18;
            case RUNNER -> respProficiency * 15;
            default -> 0;
        };
    }

    /**
     * Small continuity bonus:
     * if the employee is already working that day,
     * we slightly prefer continuity.
     */
    private int getSameDayContinuityBonus(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        long assignedMinutes = schedule.getAssignedMinutesFor(employee, slot.getDay());

        if (assignedMinutes <= 0) {
            return 0;
        }

        return (int) Math.min(20, assignedMinutes / 60);
    }

    /**
     * Business priority of skills.
     * Lower number = more important = assigned earlier.
     *
     * Requested sequence:
     * RESP -> OPENING -> BAR -> others
     */
    private int getBusinessSkillPriority(Skill skill) {
        return switch (skill) {
            case RESP -> 1;
            case OPENING -> 2;
            case BAR -> 3;
            case KITCHEN -> 4;
            case WAITER -> 5;
            case RUNNER -> 6;
        };
    }

    /**
     * Business priority of days.
     * Lower number = more critical = assigned earlier.
     */
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

        if (proficiency == null) {
            return 0;
        }

        return switch (proficiency) {
            case LOW -> 1;
            case MID -> 2;
            case HIGH -> 3;
        };
    }

    private int getEmployeePriorityScore(Priority priority) {
        return switch (priority) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }

    /**
     * Preview helper used only by sortSlots.
     * There is no schedule context there, so we keep it neutral.
     */
    private int getEligibleCountForPreview(ShiftSlot slot) {
        return 0;
    }
}