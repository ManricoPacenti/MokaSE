package it.pacenti.moka.scheduling;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates a weekly schedule from a weekly template and a list of employees.
 *
 * Strategy:
 * 1. assign slots greedily, prioritizing critical business roles
 * 2. apply opening/bar business rule with containment
 * 3. run a small, controlled rebalance focused on HIGH priority employees
 *
 * Business meaning of priority:
 * - HIGH   = contractual staff whose agreed hours should be respected as much as possible
 * - MEDIUM = intermediate pressure
 * - LOW    = more flexible / on-call staff
 *
 * Design goals:
 * - readable and explainable heuristics
 * - conservative rebalance
 * - avoid chaotic endless swaps
 */
public class ShiftScheduler {

    private static final Logger LOGGER = Logger.getLogger(ShiftScheduler.class.getName());

    private static final int MAX_DAILY_MINUTES = 8 * 60;
    private static final int MAX_REBALANCE_PASSES = 2;

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

        rebalanceHighPriorityCoverage(schedule, employees);

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
                                .thenComparingInt(employee -> getRoleFitScore(employee, slot))
                                .thenComparingInt(employee -> getHoursBalanceScore(employee, schedule, slot))
                                .thenComparingInt(employee -> getContractPriorityScore(employee, slot))
                                .thenComparingDouble(schedule::getRemainingHours)
                );
    }

    /**
     * Business rule:
     * if an OPENING employee also has BAR, any BAR slot fully contained
     * inside that OPENING slot can be downgraded to WAITER.
     *
     * This is more realistic than requiring exact same start/end.
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

            if (slot.getDay() != assignedSlot.getDay()) {
                continue;
            }

            if (slot.getRequiredSkill() != Skill.BAR) {
                continue;
            }

            if (isRangeContained(assignedSlot.getRange(), slot.getRange())) {
                slot.setRequiredSkill(Skill.WAITER);
            }
        }
    }

    // =========================================================
    // REBALANCE
    // =========================================================

    private void rebalanceHighPriorityCoverage(WeeklySchedule schedule, List<Employee> employees) {
        Set<ShiftSlot> lockedSlots = new HashSet<>();

        for (int pass = 0; pass < MAX_REBALANCE_PASSES; pass++) {
            boolean improved = false;

            List<Employee> highTargets = employees.stream()
                    .filter(employee -> employee.getPriority() == Priority.HIGH)
                    .filter(employee -> schedule.getRemainingHours(employee) > 0)
                    .sorted(
                            Comparator.comparingInt((Employee employee) -> getHighPriorityUrgency(employee, schedule))
                                    .reversed()
                                    .thenComparing(Employee::getName)
                    )
                    .toList();

            for (Employee target : highTargets) {
                if (tryImproveHighEmployee(target, schedule, lockedSlots)) {
                    improved = true;
                }
            }

            if (!improved) {
                return;
            }
        }
    }

    private boolean tryImproveHighEmployee(Employee target,
                                           WeeklySchedule schedule,
                                           Set<ShiftSlot> lockedSlots) {
        List<ShiftSlot> candidates = getRebalanceCandidateSlots(schedule);

        for (ShiftSlot desiredSlot : candidates) {
            if (lockedSlots.contains(desiredSlot)) {
                continue;
            }

            if (isAssignedTo(schedule, desiredSlot, target)) {
                continue;
            }

            if (!canAssignEmployeeToSlot(target, desiredSlot, schedule, Set.of())) {
                continue;
            }

            Optional<Assignment> currentAssignment = schedule.getAssignment(desiredSlot);

            if (currentAssignment.isEmpty()) {
                schedule.assign(desiredSlot, target);
                lockedSlots.add(desiredSlot);

                debugRebalance(
                        "REBALANCE DIRECT",
                        target.getName() + " takes free slot " + slotLabel(desiredSlot)
                );
                return true;
            }

            Employee incumbent = currentAssignment.get().getEmployee();

            if (incumbent.getPriority() == Priority.HIGH) {
                continue;
            }

            if (!isTakeoverWorthIt(target, incumbent, desiredSlot, schedule)) {
                continue;
            }

            if (tryMoveIncumbentToFreeSlot(target, incumbent, desiredSlot, schedule, lockedSlots)) {
                return true;
            }

            if (tryTakeSlotByDroppingIncumbent(target, incumbent, desiredSlot, schedule, lockedSlots)) {
                return true;
            }
        }

        return false;
    }

    private List<ShiftSlot> getRebalanceCandidateSlots(WeeklySchedule schedule) {
        List<ShiftSlot> ordered = new ArrayList<>(schedule.getSlots());

        ordered.sort(
                Comparator.comparingInt((ShiftSlot slot) -> getRebalancePreference(slot.getRequiredSkill()))
                        .thenComparingInt(slot -> getDayPriority(slot.getDay()))
                        .thenComparingLong(slot -> -slot.durationMinutes())
                        .thenComparing(slot -> slot.getRange().getStart())
        );

        return ordered;
    }

    private int getRebalancePreference(Skill skill) {
        return switch (skill) {
            case WAITER -> 1;
            case RUNNER -> 2;
            case OPENING -> 3;
            case BAR -> 4;
            case KITCHEN -> 5;
            case RESP -> 6;
        };
    }

    private boolean tryMoveIncumbentToFreeSlot(Employee target,
                                               Employee incumbent,
                                               ShiftSlot desiredSlot,
                                               WeeklySchedule schedule,
                                               Set<ShiftSlot> lockedSlots) {
        List<ShiftSlot> freeSlots = new ArrayList<>(schedule.getUnassignedSlots());

        freeSlots.sort(
                Comparator.comparingInt((ShiftSlot slot) -> getRelocationPreference(slot.getRequiredSkill()))
                        .thenComparingInt(slot -> getDayPriority(slot.getDay()))
                        .thenComparingLong(slot -> -slot.durationMinutes())
                        .thenComparing(slot -> slot.getRange().getStart())
        );

        for (ShiftSlot relocationSlot : freeSlots) {
            if (lockedSlots.contains(relocationSlot)) {
                continue;
            }

            if (!canAssignEmployeeToSlot(incumbent, relocationSlot, schedule, Set.of(desiredSlot))) {
                continue;
            }

            if (!isRelocationReasonable(incumbent, desiredSlot, relocationSlot)) {
                continue;
            }

            schedule.unassign(desiredSlot);

            boolean success = false;
            try {
                if (!canAssignEmployeeToSlot(target, desiredSlot, schedule, Set.of())) {
                    continue;
                }

                schedule.assign(desiredSlot, target);
                schedule.assign(relocationSlot, incumbent);

                lockedSlots.add(desiredSlot);
                lockedSlots.add(relocationSlot);

                debugRebalance(
                        "REBALANCE MOVE",
                        target.getName() + " takes " + slotLabel(desiredSlot)
                                + " | " + incumbent.getName() + " moved to " + slotLabel(relocationSlot)
                );

                success = true;
                return true;
            } finally {
                if (!success && !schedule.isAssigned(desiredSlot)) {
                    schedule.assign(desiredSlot, incumbent);
                }
            }
        }

        return false;
    }

    private boolean tryTakeSlotByDroppingIncumbent(Employee target,
                                                   Employee incumbent,
                                                   ShiftSlot desiredSlot,
                                                   WeeklySchedule schedule,
                                                   Set<ShiftSlot> lockedSlots) {
        if (incumbent.getPriority() == Priority.HIGH) {
            return false;
        }

        if (isVeryProtectedSkill(desiredSlot.getRequiredSkill())) {
            return false;
        }

        if (incumbent.getPriority() == Priority.MEDIUM && isCriticalSkill(desiredSlot.getRequiredSkill())) {
            return false;
        }

        schedule.unassign(desiredSlot);

        boolean success = false;
        try {
            if (!canAssignEmployeeToSlot(target, desiredSlot, schedule, Set.of())) {
                return false;
            }

            schedule.assign(desiredSlot, target);
            lockedSlots.add(desiredSlot);

            debugRebalance(
                    "REBALANCE TAKEOVER",
                    target.getName() + " takes " + slotLabel(desiredSlot)
                            + " from " + incumbent.getName()
            );

            success = true;
            return true;
        } finally {
            if (!success && !schedule.isAssigned(desiredSlot)) {
                schedule.assign(desiredSlot, incumbent);
            }
        }
    }

    private boolean isTakeoverWorthIt(Employee target,
                                      Employee incumbent,
                                      ShiftSlot slot,
                                      WeeklySchedule schedule) {
        if (target.getPriority() != Priority.HIGH) {
            return false;
        }

        if (incumbent.getPriority() == Priority.HIGH) {
            return false;
        }

        if (isVeryProtectedSkill(slot.getRequiredSkill())) {
            return false;
        }

        int targetRole = getRoleFitScore(target, slot);
        int incumbentRole = getRoleFitScore(incumbent, slot);

        double targetMissing = schedule.getRemainingHours(target);
        double incumbentMissing = schedule.getRemainingHours(incumbent);

        if (slot.getRequiredSkill() == Skill.WAITER) {
            if (schedule.getRemainingHours(target) <= 6.0) {
                return false;
            }

            return targetMissing > 0
                    && target.getPriority() == Priority.HIGH
                    && incumbent.getPriority() != Priority.HIGH
                    && targetRole > 0
                    && (
                    targetRole >= incumbentRole
                            || getOpportunityPressureScore(target, slot) >= 60
            )
                    && (targetMissing > incumbentMissing || incumbent.getPriority() == Priority.LOW);
        }

        if (slot.getRequiredSkill() == Skill.RUNNER) {
            if (schedule.getRemainingHours(target) <= 6.0) {
                return false;
            }

            return targetMissing > 0
                    && target.getPriority() == Priority.HIGH
                    && incumbent.getPriority() != Priority.HIGH
                    && targetRole >= incumbentRole
                    && (targetMissing > incumbentMissing || incumbent.getPriority() == Priority.LOW);
        }

        if (slot.getRequiredSkill() == Skill.OPENING || slot.getRequiredSkill() == Skill.BAR) {
            return targetRole >= incumbentRole
                    && targetMissing > 0
                    && incumbent.getPriority() != Priority.HIGH;
        }

        return false;
    }

    private boolean isRelocationReasonable(Employee incumbent,
                                           ShiftSlot originalSlot,
                                           ShiftSlot relocationSlot) {
        int originalPreference = getRelocationPreference(originalSlot.getRequiredSkill());
        int relocationPreference = getRelocationPreference(relocationSlot.getRequiredSkill());

        if (relocationPreference > originalPreference + 1) {
            return false;
        }

        return getRoleFitScore(incumbent, relocationSlot) > 0;
    }

    private int getRelocationPreference(Skill skill) {
        return switch (skill) {
            case WAITER -> 1;
            case RUNNER -> 2;
            case KITCHEN -> 3;
            case OPENING -> 4;
            case BAR -> 5;
            case RESP -> 6;
        };
    }

    private boolean isAssignedTo(WeeklySchedule schedule, ShiftSlot slot, Employee employee) {
        Optional<Assignment> assignment = schedule.getAssignment(slot);
        return assignment.isPresent() && assignment.get().getEmployee().equals(employee);
    }

    private boolean canAssignEmployeeToSlot(Employee employee,
                                            ShiftSlot slot,
                                            WeeklySchedule schedule,
                                            Collection<ShiftSlot> ignoredOwnedSlots) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        Objects.requireNonNull(slot, "Shift slot cannot be null");
        Objects.requireNonNull(schedule, "Schedule cannot be null");
        Objects.requireNonNull(ignoredOwnedSlots, "Ignored owned slots cannot be null");

        LocalDate slotDate = schedule.getDateFor(slot);

        if (!employee.isAssignableTo(slot, slotDate)) {
            return false;
        }

        double remainingHours = schedule.getRemainingHours(employee);
        long assignedToday = schedule.getAssignedMinutesFor(employee, slot.getDay());

        for (ShiftSlot ignored : ignoredOwnedSlots) {
            Optional<Assignment> assignment = schedule.getAssignment(ignored);
            if (assignment.isPresent() && assignment.get().getEmployee().equals(employee)) {
                remainingHours += ignored.durationMinutes() / 60.0;

                if (ignored.getDay() == slot.getDay()) {
                    assignedToday -= ignored.durationMinutes();
                }
            }
        }

        if (remainingHours < slot.durationMinutes() / 60.0) {
            return false;
        }

        if (assignedToday + slot.durationMinutes() > MAX_DAILY_MINUTES) {
            return false;
        }

        for (Assignment assignment : schedule.getAssignmentsFor(employee)) {
            ShiftSlot current = assignment.getSlot();

            if (ignoredOwnedSlots.contains(current)) {
                continue;
            }

            if (current.overlaps(slot)) {
                return false;
            }
        }

        return true;
    }

    // =========================================================
    // SCORING
    // =========================================================

    private int calculateCandidateScore(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        int roleFitScore = getRoleFitScore(employee, slot);
        int hoursBalanceScore = getHoursBalanceScore(employee, schedule, slot);
        int contractPriorityScore = getContractPriorityScore(employee, slot);
        int scarcityScore = getWeightedScarcityScore(employee, slot);
        int opportunityScore = getOpportunityPressureScore(employee, slot);
        int sameDayContinuityBonus = getSameDayContinuityBonus(employee, slot, schedule);
        int criticalSkillProtectionPenalty = getCriticalSkillProtectionPenalty(employee, slot);

        return roleFitScore
                + hoursBalanceScore
                + contractPriorityScore
                + scarcityScore
                + opportunityScore
                + sameDayContinuityBonus
                + criticalSkillProtectionPenalty;
    }

    /**
     * Role fit dominates critical slots.
     */
    private int getRoleFitScore(Employee employee, ShiftSlot slot) {
        int proficiency = getProficiencyScore(employee, slot.getRequiredSkill());

        if (proficiency == 0) {
            return 0;
        }

        if (slot.getRequiredSkill() == Skill.RESP) {
            return proficiency * 140;
        }

        if (slot.getRequiredSkill() == Skill.OPENING || slot.getRequiredSkill() == Skill.BAR) {
            return proficiency * 140;
        }

        if (slot.getRequiredSkill() == Skill.WAITER || slot.getRequiredSkill() == Skill.RUNNER) {
            return proficiency * 85;
        }

        return proficiency * 85;
    }

    /**
     * Hours score is stronger on non-critical roles because those are
     * the main compensation area for contractual staff.
     */
    private int getHoursBalanceScore(Employee employee, WeeklySchedule schedule, ShiftSlot slot) {
        double remainingHours = Math.max(0.0, schedule.getRemainingHours(employee));
        double agreedHours = Math.max(1.0, employee.getAgreedHours());

        int relativeScore = (int) Math.round((remainingHours / agreedHours) * 100.0);
        int absoluteScore = (int) Math.min(80, Math.round(remainingHours * 5.0));

        if (slot.getRequiredSkill() == Skill.WAITER || slot.getRequiredSkill() == Skill.RUNNER) {
            return relativeScore + absoluteScore;
        }

        if (slot.getRequiredSkill() == Skill.OPENING || slot.getRequiredSkill() == Skill.BAR) {
            return relativeScore + (absoluteScore / 2);
        }

        if (slot.getRequiredSkill() == Skill.RESP) {
            return relativeScore + (absoluteScore / 3);
        }

        return relativeScore + (absoluteScore / 2);
    }

    /**
     * HIGH priority means contractual pressure.
     * Stronger on non-critical roles, where recovery should happen.
     */
    private int getContractPriorityScore(Employee employee, ShiftSlot slot) {
        int base = switch (employee.getPriority()) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };

        if (slot.getRequiredSkill() == Skill.WAITER || slot.getRequiredSkill() == Skill.RUNNER) {
            return base * 12;
        }

        if (slot.getRequiredSkill() == Skill.OPENING || slot.getRequiredSkill() == Skill.BAR) {
            return base * 6;
        }

        if (slot.getRequiredSkill() == Skill.RESP) {
            return base * 6;
        }

        return base * 4;
    }

    /**
     * General weekly scarcity.
     */
    private int getWeightedScarcityScore(Employee employee, ShiftSlot slot) {
        int scarcity = getWeeklyAvailabilityScarcityScore(employee);

        if (slot.getRequiredSkill() == Skill.WAITER || slot.getRequiredSkill() == Skill.RUNNER) {
            return scarcity * 6;
        }

        return scarcity * 2;
    }

    /**
     * Strong boost when a HIGH employee has very few useful remaining days,
     * especially on Fri/Sat/Sun and especially on non-critical roles.
     */
    private int getOpportunityPressureScore(Employee employee, ShiftSlot slot) {
        if (employee.getPriority() != Priority.HIGH) {
            return 0;
        }

        int availableDays = getAvailableDays(employee);
        if (availableDays > 3) {
            return 0;
        }

        boolean weekendLike = slot.getDay() == DayOfWeek.FRIDAY
                || slot.getDay() == DayOfWeek.SATURDAY
                || slot.getDay() == DayOfWeek.SUNDAY;

        if (!weekendLike) {
            return 0;
        }

        return switch (slot.getRequiredSkill()) {
            case WAITER, RUNNER -> 60;
            case RESP -> 45;
            case OPENING, BAR -> 30;
            case KITCHEN -> 0;
        };
    }

    private int getSameDayContinuityBonus(Employee employee, ShiftSlot slot, WeeklySchedule schedule) {
        long assignedMinutes = schedule.getAssignedMinutesFor(employee, slot.getDay());

        if (assignedMinutes <= 0) {
            return 0;
        }

        if (slot.getRequiredSkill() == Skill.WAITER || slot.getRequiredSkill() == Skill.RUNNER) {
            return (int) Math.min(15, assignedMinutes / 60);
        }

        return (int) Math.min(8, assignedMinutes / 120);
    }

    /**
     * Protect RESP employees from being overused on non-critical roles.
     * However if they are HIGH and heavily under hours, the penalty is softened.
     */
    private int getCriticalSkillProtectionPenalty(Employee employee, ShiftSlot slot) {
        Skill requiredSkill = slot.getRequiredSkill();

        if (requiredSkill == Skill.RESP) {
            return 0;
        }

        if (requiredSkill == Skill.OPENING || requiredSkill == Skill.BAR) {
            return 0;
        }

        if (!employee.hasSkill(Skill.RESP)) {
            return 0;
        }

        if (employee.getPriority() == Priority.HIGH) {
            return -30;
        }

        return -60;
    }

    private int getWeeklyAvailabilityScarcityScore(Employee employee) {
        int availableDays = getAvailableDays(employee);
        return Math.max(0, 7 - availableDays);
    }

    private int getAvailableDays(Employee employee) {
        int fullDaysOff = employee.getAvailability().getFullDaysOff().size();
        return Math.max(0, 7 - fullDaysOff);
    }

    private int getHighPriorityUrgency(Employee employee, WeeklySchedule schedule) {
        int missing = (int) Math.round(schedule.getRemainingHours(employee) * 10.0);
        int scarcity = getWeeklyAvailabilityScarcityScore(employee) * 10;
        return missing + scarcity;
    }

    private boolean isCriticalSkill(Skill skill) {
        return skill == Skill.RESP
                || skill == Skill.OPENING
                || skill == Skill.BAR;
    }

    private boolean isVeryProtectedSkill(Skill skill) {
        return skill == Skill.RESP;
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
            case FRIDAY -> 2;
            case SUNDAY -> 3;
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

    // =========================================================
    // TIME HELPERS
    // =========================================================

    private boolean isRangeContained(TimeRange outer, TimeRange inner) {
        int outerStart = toMinute(outer.getStart());
        int outerEnd = normalizedEndMinute(outer);

        int innerStart = toMinute(inner.getStart());
        int innerEnd = normalizedEndMinute(inner);

        if (inner.crossesMidnight() && !outer.crossesMidnight()) {
            return false;
        }

        if (outer.crossesMidnight() && !inner.crossesMidnight() && innerStart < toMinute(outer.getEnd())) {
            innerStart += 24 * 60;
            innerEnd += 24 * 60;
        }

        return innerStart >= outerStart && innerEnd <= outerEnd;
    }

    private int toMinute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private int normalizedEndMinute(TimeRange range) {
        int end = toMinute(range.getEnd());
        return range.crossesMidnight() ? end + 24 * 60 : end;
    }

    // =========================================================
    // DEBUG
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
            int roleFitScore = getRoleFitScore(employee, slot);
            int hoursBalanceScore = getHoursBalanceScore(employee, schedule, slot);
            int contractPriorityScore = getContractPriorityScore(employee, slot);
            int scarcityScore = getWeightedScarcityScore(employee, slot);
            int opportunityScore = getOpportunityPressureScore(employee, slot);
            int sameDayContinuityBonus = getSameDayContinuityBonus(employee, slot, schedule);
            int criticalSkillProtectionPenalty = getCriticalSkillProtectionPenalty(employee, slot);

            int total = roleFitScore
                    + hoursBalanceScore
                    + contractPriorityScore
                    + scarcityScore
                    + opportunityScore
                    + sameDayContinuityBonus
                    + criticalSkillProtectionPenalty;

            double assignedHours = schedule.getAssignedHours(employee);
            double remainingHours = schedule.getRemainingHours(employee);
            int availableDays = getAvailableDays(employee);

            System.out.printf(
                    "- %-15s total=%4d | role=%3d | hrs=%3d | pri=%2d | scarce=%2d | opp=%3d | cont=%2d | protect=%4d | assigned=%4.1f | missing=%4.1f | availDays=%d%n",
                    employee.getName(),
                    total,
                    roleFitScore,
                    hoursBalanceScore,
                    contractPriorityScore,
                    scarcityScore,
                    opportunityScore,
                    sameDayContinuityBonus,
                    criticalSkillProtectionPenalty,
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

    private void debugRebalance(String title, String message) {
        if (!DEBUG) {
            return;
        }

        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println(title);
        System.out.println("------------------------------------------------------------");
        System.out.println(message);
    }

    private String slotLabel(ShiftSlot slot) {
        return slot.getDay()
                + " " + slot.getRange().getStart()
                + "-" + slot.getRange().getEnd()
                + " [" + slot.getRequiredSkill() + "]";
    }
}