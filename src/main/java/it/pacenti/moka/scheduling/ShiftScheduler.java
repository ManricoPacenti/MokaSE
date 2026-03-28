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
 * 1. assign first the hardest slots to cover
 * 2. prefer candidates with stronger proficiency on the required skill
 * 3. protect scarce / critical resources for future slots
 * 4. encourage continuity on the same day
 *
 * This is intentionally heuristic and readable:
 * no solver, no backtracking, no advanced optimization algorithm.
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
            assignBestEmployee(nextSlot, schedule, employees, remainingSlots);
            remainingSlots.remove(nextSlot);
        }

        LOGGER.info("Schedule generation completed");
        return schedule;
    }

    /**
     * Keeps a public deterministic sorting method available.
     * Useful for debugging, previews or tests.
     *
     * The real schedule generation uses dynamic slot selection instead.
     */
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

    /**
     * Selects the next slot to assign according to scheduling difficulty.
     *
     * Priority:
     * 1. fewer eligible employees first
     * 2. more critical business skill first
     * 3. more critical day first
     * 4. longer slot first
     * 5. earlier start first
     */
    private ShiftSlot selectNextSlot(List<ShiftSlot> remainingSlots,
                                     WeeklySchedule schedule,
                                     List<Employee> employees) {
        return remainingSlots.stream()
                .min(
                        Comparator.comparingInt((ShiftSlot slot) ->
                                        findEligibleEmployees(slot, schedule, employees).size())
                                .thenComparingInt(slot -> getBusinessSkillPriority(slot.getRequiredSkill()))
                                .thenComparingInt(slot -> getDayPriority(slot.getDay()))
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

        assignBestEmployee(slot, schedule, employees, schedule.getSlots());
    }

    /**
     * Internal assignment method aware of remaining slots.
     */
    private void assignBestEmployee(ShiftSlot slot,
                                    WeeklySchedule schedule,
                                    List<Employee> employees,
                                    List<ShiftSlot> remainingSlots) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");
        Objects.requireNonNull(employees, "Employees cannot be null");
        Objects.requireNonNull(remainingSlots, "Remaining slots cannot be null");

        List<Employee> candidates = findEligibleEmployees(slot, schedule, employees);

        if (candidates.isEmpty()) {
            LOGGER.warning("No eligible employee found for slot: " + slot);
            return;
        }

        Optional<Employee> selected = selectBestEmployee(slot, candidates, schedule, remainingSlots, employees);

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
     * Backward-compatible public selector.
     * Uses the simpler strategy if called externally.
     */
    public Optional<Employee> selectBestEmployee(ShiftSlot slot, List<Employee> candidates, WeeklySchedule schedule) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(candidates, "Candidates cannot be null");
        Objects.requireNonNull(schedule, "WeeklySchedule cannot be null");

        return candidates.stream()
                .max(
                        Comparator.comparingInt((Employee employee) ->
                                        getProficiencyScore(employee, slot.getRequiredSkill()))
                                .thenComparingInt(employee -> getEmployeePriorityScore(employee.getPriority()))
                                .thenComparingInt(employee -> getSameDayContinuityScore(employee, slot, schedule))
                                .thenComparingDouble(employee -> schedule.getRemainingHours(employee))                );
    }

    /**
     * Selects the best employee among the eligible candidates using
     * a richer heuristic.
     *
     * Priority order:
     * 1. skill proficiency on the requested skill
     * 2. employee priority
     * 3. same-day continuity
     * 4. preserve valuable resources for future critical/scarce slots
     * 5. remaining weekly hours
     */
    private Optional<Employee> selectBestEmployee(ShiftSlot slot,
                                                  List<Employee> candidates,
                                                  WeeklySchedule schedule,
                                                  List<ShiftSlot> remainingSlots,
                                                  List<Employee> allEmployees) {
        return candidates.stream()
                .max(
                        Comparator.comparingInt((Employee employee) ->
                                        getProficiencyScore(employee, slot.getRequiredSkill()))
                                .thenComparingInt(employee ->
                                        getEmployeePriorityScore(employee.getPriority()))
                                .thenComparingLong(employee ->
                                        schedule.getAssignedMinutesFor(employee, slot.getDay()))
                                .thenComparingInt(employee ->
                                        -getFuturePreservationPenalty(employee, slot, remainingSlots, schedule, allEmployees))
                                .thenComparingDouble(employee ->
                                        schedule.getRemainingHours(employee))
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
     * Measures continuity as already assigned minutes on the same day.
     * This is a simple and readable proxy:
     * it helps reduce unnecessary day fragmentation without introducing
     * complex adjacency logic.
     */
    private int getSameDayContinuityScore(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        return (int) schedule.getAssignedMinutesFor(employee, slot.getDay());
    }

    /**
     * Penalizes assigning a candidate to the current slot if that employee
     * is also likely needed for future slots that are:
     * - more critical from a business perspective
     * - harder to cover (fewer candidates)
     *
     * Higher penalty = employee should be preserved for future use.
     */
    private int getFuturePreservationPenalty(Employee employee,
                                             ShiftSlot currentSlot,
                                             List<ShiftSlot> remainingSlots,
                                             WeeklySchedule schedule,
                                             List<Employee> allEmployees) {
        int penalty = 0;

        for (ShiftSlot futureSlot : remainingSlots) {
            if (futureSlot == currentSlot) {
                continue;
            }

            if (!isMoreCriticalThanCurrent(futureSlot, currentSlot)) {
                continue;
            }

            if (!canStillCoverFutureSlotAfterCurrentAssignment(employee, currentSlot, futureSlot, schedule)) {
                continue;
            }

            int eligibleCount = findEligibleEmployees(futureSlot, schedule, allEmployees).size();
            int scarcityWeight = getScarcityWeight(eligibleCount);
            int proficiencyWeight = Math.max(1, getProficiencyScore(employee, futureSlot.getRequiredSkill()));

            penalty += scarcityWeight * proficiencyWeight;
        }

        return penalty;
    }

    private boolean isMoreCriticalThanCurrent(ShiftSlot futureSlot, ShiftSlot currentSlot) {
        return getBusinessSkillPriority(futureSlot.getRequiredSkill())
                < getBusinessSkillPriority(currentSlot.getRequiredSkill());
    }

    /**
     * Checks whether an employee would still be able to cover a future slot
     * after being assigned to the current one.
     */
    private boolean canStillCoverFutureSlotAfterCurrentAssignment(Employee employee,
                                                                  ShiftSlot currentSlot,
                                                                  ShiftSlot futureSlot,
                                                                  WeeklySchedule schedule) {
        LocalDate futureDate = schedule.getDateFor(futureSlot);

        if (!employee.isAssignableTo(futureSlot, futureDate)) {
            return false;
        }

        if (schedule.hasOverlappingAssignment(employee, futureSlot)) {
            return false;
        }

        if (currentSlot.overlaps(futureSlot)) {
            return false;
        }

        double remainingHoursAfterCurrent =
                schedule.getRemainingHours(employee) - (currentSlot.durationMinutes() / 60.0);

        if (remainingHoursAfterCurrent < futureSlot.durationMinutes() / 60.0) {
            return false;
        }

        long assignedFutureDay = schedule.getAssignedMinutesFor(employee, futureSlot.getDay());
        if (currentSlot.getDay().equals(futureSlot.getDay())) {
            assignedFutureDay += currentSlot.durationMinutes();
        }

        return assignedFutureDay + futureSlot.durationMinutes() <= MAX_DAILY_MINUTES;
    }

    private int getScarcityWeight(int eligibleCount) {
        return switch (eligibleCount) {
            case 0, 1 -> 4;
            case 2 -> 3;
            case 3 -> 2;
            default -> 1;
        };
    }

    /**
     * Business priority of skills.
     * Lower number = more important = assigned earlier.
     */
    private int getBusinessSkillPriority(Skill skill) {
        return switch (skill) {
            case RESP -> 1;
            case BAR -> 2;
            case OPENING -> 3;
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
}