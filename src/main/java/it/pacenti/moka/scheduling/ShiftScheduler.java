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
 *    - scarcity of weekly availability
 *    - remaining agreed hours to complete
 *    - employee priority
 *    - RESP cross-role bonus on other skills
 *    - slight same-day continuity
 *
 * This is intentionally heuristic, readable and explainable.
 */
public class ShiftScheduler {

    private static final Logger LOGGER = Logger.getLogger(ShiftScheduler.class.getName());
    private static final int MAX_DAILY_MINUTES = 8 * 60;

    /**
     * Set to false if you want silence.
     */
    private static final boolean DEBUG = true;

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

    public List<ShiftSlot> sortSlots(List<ShiftSlot> slots) {
        Objects.requireNonNull(slots, "Slots cannot be null");

        List<ShiftSlot> ordered = new ArrayList<>(slots);

        ordered.sort(
                Comparator.comparingInt((ShiftSlot slot) -> getBusinessSkillPriority(slot.getRequiredSkill()))
                        .thenComparingInt(slot -> getDayPriority(slot.getDay()))
                        .thenComparingLong(slot -> -slot.durationMinutes())
                        .thenComparing(slot -> slot.getRange().getStart())
        );

        return ordered;
    }

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

    public void assignBestEmployee(ShiftSlot slot, WeeklySchedule schedule, List<Employee> employees) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");
        Objects.requireNonNull(employees, "Employees cannot be null");

        List<Employee> candidates = findEligibleEmployees(slot, schedule, employees);

        debugSlotHeader(slot, schedule, candidates);

        if (candidates.isEmpty()) {
            LOGGER.warning("No eligible employee found for slot: " + slot);
            debugNoCandidates(slot);
            return;
        }

        debugCandidateBreakdown(slot, schedule, candidates);

        Optional<Employee> selected = selectBestEmployee(slot, candidates, schedule);

        if (selected.isPresent()) {
            Employee employee = selected.get();
            schedule.assign(slot, employee);
            applyOpeningRule(slot, employee, schedule);

            LOGGER.info("Assigned " + employee.getName() + " to slot " + slot);
            debugSelectedEmployee(slot, employee, schedule);
        } else {
            LOGGER.warning("No employee selected for slot: " + slot);
            debugNoSelection(slot);
        }
    }

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

    private int calculateCandidateScore(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        int proficiencyScore = getProficiencyScore(employee, slot.getRequiredSkill()) * 100;
        int weeklyAvailabilityScarcityScore = getWeeklyAvailabilityScarcityScore(employee) * 12;
        int remainingHoursScore = getRemainingHoursScore(employee, schedule) * 18;
        int priorityScore = getEmployeePriorityScore(employee.getPriority()) * 15;
        int respCrossRoleBonus = getRespCrossRoleBonus(employee, slot);
        int sameDayContinuityBonus = getSameDayContinuityBonus(employee, slot, schedule);

        return proficiencyScore
                + weeklyAvailabilityScarcityScore
                + remainingHoursScore
                + priorityScore
                + respCrossRoleBonus
                + sameDayContinuityBonus;
    }

    /**
     * Rewards scarcity of weekly availability.
     *
     * Example:
     * - available 3 days => higher score
     * - available 7 days => lower score
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
     * More missing agreed hours => stronger incentive to assign.
     */
    private int getRemainingHoursScore(Employee employee, WeeklySchedule schedule) {
        double remainingHours = schedule.getRemainingHours(employee);

        if (remainingHours <= 0) {
            return 0;
        }

        return Math.min(12, (int) Math.ceil(remainingHours));
    }

    /**
     * Gives RESP employees a transversal advantage on non-RESP roles.
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
            case OPENING -> respProficiency * 24;
            case BAR -> respProficiency * 20;
            case KITCHEN -> respProficiency * 12;
            case WAITER -> respProficiency * 16;
            case RUNNER -> respProficiency * 10;
            default -> 0;
        };
    }

    /**
     * Slight preference for continuing work on the same day.
     */
    private int getSameDayContinuityBonus(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        long assignedMinutes = schedule.getAssignedMinutesFor(employee, slot.getDay());

        if (assignedMinutes <= 0) {
            return 0;
        }

        return (int) Math.min(20, assignedMinutes / 60);
    }

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

    // =========================================================
    // DEBUG BLOCK
    // =========================================================

    private void debugSlotHeader(ShiftSlot slot, WeeklySchedule schedule, List<Employee> candidates) {
        if (!DEBUG) {
            return;
        }

        LocalDate date = schedule.getDateFor(slot);

        System.out.println();
        System.out.println("============================================================");
        System.out.println("SCHEDULER DEBUG");
        System.out.println("============================================================");
        System.out.printf(
                "Slot: %s %s %s-%s [%s] | candidates: %d%n",
                slot.getDay(),
                date,
                slot.getRange().getStart(),
                slot.getRange().getEnd(),
                slot.getRequiredSkill(),
                candidates.size()
        );
    }

    private void debugCandidateBreakdown(ShiftSlot slot, WeeklySchedule schedule, List<Employee> candidates) {
        if (!DEBUG) {
            return;
        }

        List<Employee> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt((Employee employee) -> calculateCandidateScore(employee, slot, schedule))
                .reversed()
                .thenComparing(Employee::getName));

        System.out.println("Candidates:");

        for (Employee employee : ordered) {
            int proficiencyScore = getProficiencyScore(employee, slot.getRequiredSkill()) * 100;
            int weeklyAvailabilityScarcityScore = getWeeklyAvailabilityScarcityScore(employee) * 12;
            int remainingHoursScore = getRemainingHoursScore(employee, schedule) * 18;
            int priorityScore = getEmployeePriorityScore(employee.getPriority()) * 15;
            int respCrossRoleBonus = getRespCrossRoleBonus(employee, slot);
            int sameDayContinuityBonus = getSameDayContinuityBonus(employee, slot, schedule);
            int total = proficiencyScore
                    + weeklyAvailabilityScarcityScore
                    + remainingHoursScore
                    + priorityScore
                    + respCrossRoleBonus
                    + sameDayContinuityBonus;

            double assignedHours = schedule.getAssignedHours(employee);
            double remainingHours = schedule.getRemainingHours(employee);
            int fullDaysOff = employee.getAvailability().getFullDaysOff().size();
            int availableDays = 7 - fullDaysOff;

            System.out.printf(
                    "- %-15s total=%4d | prof=%3d | scarce=%3d | rem=%3d | pri=%2d | resp=%3d | cont=%2d | assigned=%4.1f | missing=%4.1f | availDays=%d%n",
                    employee.getName(),
                    total,
                    proficiencyScore,
                    weeklyAvailabilityScarcityScore,
                    remainingHoursScore,
                    priorityScore,
                    respCrossRoleBonus,
                    sameDayContinuityBonus,
                    assignedHours,
                    remainingHours,
                    availableDays
            );
        }
    }

    private void debugSelectedEmployee(ShiftSlot slot, Employee employee, WeeklySchedule schedule) {
        if (!DEBUG) {
            return;
        }

        System.out.printf(
                "Selected: %s for %s %s-%s [%s] | assigned now: %.1fh | remaining: %.1fh%n",
                employee.getName(),
                slot.getDay(),
                slot.getRange().getStart(),
                slot.getRange().getEnd(),
                slot.getRequiredSkill(),
                schedule.getAssignedHours(employee),
                schedule.getRemainingHours(employee)
        );
    }

    private void debugNoCandidates(ShiftSlot slot) {
        if (!DEBUG) {
            return;
        }

        System.out.printf(
                "No eligible candidates for slot %s %s-%s [%s]%n",
                slot.getDay(),
                slot.getRange().getStart(),
                slot.getRange().getEnd(),
                slot.getRequiredSkill()
        );
    }

    private void debugNoSelection(ShiftSlot slot) {
        if (!DEBUG) {
            return;
        }

        System.out.printf(
                "No selection produced for slot %s %s-%s [%s]%n",
                slot.getDay(),
                slot.getRange().getStart(),
                slot.getRange().getEnd(),
                slot.getRequiredSkill()
        );
    }
}