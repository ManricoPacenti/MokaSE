package it.pacenti.moka.scheduling;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a weekly schedule using a multi-phase assignment strategy.
 * The scheduler prioritizes critical roles, attempts to complete agreed hours
 * for high-priority employees and performs rebalancing.
 *
 * Strategy:
 * 1) Assign RESP first
 * 2) Assign remaining slots trying to favor critical HIGH employees
 * 3) First protective rebalance for critical HIGH employees
 * 4) Assign remaining standard slots
 * 5) Final rebalance for HIGH employees still under agreed hours
 */
public class ShiftScheduler {

    public static boolean DEBUG = false;

    private static final long MAX_DAILY_MINUTES = 8 * 60;

    private static final int SCORE_ROLE_HIGH = 420;
    private static final int SCORE_ROLE_MID = 280;
    private static final int SCORE_ROLE_LOW = 140;

    private static final int SCORE_PRIORITY_HIGH = 36;
    private static final int SCORE_PRIORITY_MEDIUM = 24;
    private static final int SCORE_PRIORITY_LOW = 12;

    private static final int BONUS_RESP_CRITICAL = 40;
    private static final int BONUS_CRITICAL_HIGH_PHASE = 30;
    private static final int BONUS_OPPORTUNITY_RESP = 24;
    private static final int BONUS_CONTINUITY = 6;

    private static final int PENALTY_PROTECT_CRITICAL = -20;
    private static final int PENALTY_RESP_USED_OUTSIDE_ROLE = -10;


    public WeeklySchedule generateSchedule(WeeklyScheduleTemplate template, List<Employee> employees) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(employees, "Employees cannot be null");

        WeeklySchedule schedule = new WeeklySchedule(template.getWeekStart());
        schedule.addSlots(template.getSlots());

        debugHeader("Generate Weekly Schedule");

        assignRespPhase(schedule, employees);
        assignCriticalHighPhase(schedule, employees);
        rebalanceCriticalHighPhase(schedule, employees);
        assignStandardPhase(schedule, employees);
        rebalanceResidualHighPhase(schedule, employees);

        if (DEBUG) {
            System.out.println("Schedule generated successfully.");
            System.out.println();
            printHoursSummary(schedule, employees);
        }

        return schedule;
    }


    private void assignRespPhase(WeeklySchedule schedule, List<Employee> employees) {
        List<ShiftSlot> respSlots = schedule.getUnassignedSlots().stream()
                .filter(slot -> slot.getRequiredSkill() == Skill.RESP)
                .sorted(respSlotComparator())
                .toList();

        for (ShiftSlot slot : respSlots) {
            assignBestCandidate(schedule, employees, slot, AssignmentPhase.RESP);
        }
    }


    private void assignCriticalHighPhase(WeeklySchedule schedule, List<Employee> employees) {
        List<Employee> criticalHighEmployees = employees.stream()
                .filter(this::isHigh)
                .filter(this::isCriticalHighEmployee)
                .toList();

        List<ShiftSlot> slots = schedule.getUnassignedSlots().stream()
                .sorted(generalSlotComparator())
                .toList();

        for (ShiftSlot slot : slots) {
            assignBestCandidate(schedule, criticalHighEmployees, slot, AssignmentPhase.CRITICAL_HIGH);
        }
    }


    private void rebalanceCriticalHighPhase(WeeklySchedule schedule, List<Employee> employees) {
        List<Employee> targets = employees.stream()
                .filter(this::isHigh)
                .filter(this::isCriticalHighEmployee)
                .filter(employee -> getMissingMinutes(schedule, employee) > 0)
                .sorted(Comparator.comparingLong((Employee e) -> getMissingMinutes(schedule, e)).reversed())
                .toList();

        for (Employee target : targets) {
            boolean improved = true;

            while (improved && getMissingMinutes(schedule, target) > 0) {
                improved = attemptTakeover(schedule, target, true);
            }
        }
    }


    private void assignStandardPhase(WeeklySchedule schedule, List<Employee> employees) {
        List<ShiftSlot> remaining = schedule.getUnassignedSlots().stream()
                .sorted(generalSlotComparator())
                .toList();

        for (ShiftSlot slot : remaining) {
            assignBestCandidate(schedule, employees, slot, AssignmentPhase.STANDARD);
        }
    }


    private void rebalanceResidualHighPhase(WeeklySchedule schedule, List<Employee> employees) {
        List<Employee> targets = employees.stream()
                .filter(this::isHigh)
                .filter(employee -> getMissingMinutes(schedule, employee) > 0)
                .sorted(Comparator
                        .comparing((Employee e) -> isRespHigh(e) ? 0 : 1)
                        .thenComparing((Employee e) -> isCriticalHighEmployee(e) ? 0 : 1)
                        .thenComparing(Comparator.comparingLong((Employee e) -> getMissingMinutes(schedule, e)).reversed()))
                .toList();

        for (Employee target : targets) {
            boolean improved = true;

            while (improved && getMissingMinutes(schedule, target) > 0) {
                improved = attemptTakeover(schedule, target, false);
            }
        }
    }


    private void assignBestCandidate(WeeklySchedule schedule,
                                     List<Employee> employees,
                                     ShiftSlot slot,
                                     AssignmentPhase phase) {
        List<CandidateScore> candidates = employees.stream()
                .filter(employee -> canAssign(schedule, employee, slot))
                .map(employee -> scoreCandidate(schedule, employee, slot, phase))
                .sorted(Comparator.reverseOrder())
                .toList();

        if (DEBUG) {
            printDebugBlock(schedule, slot, candidates);
        }

        if (candidates.isEmpty()) {
            if (DEBUG) {
                System.out.println("No eligible candidates for slot "
                        + slot.getDay() + " "
                        + schedule.getDateFor(slot) + " "
                        + slot.getRange() + " ["
                        + slot.getRequiredSkill() + "]");
                System.out.println();
            }
            return;
        }

        Employee selected = candidates.get(0).employee();
        assign(schedule, slot, selected);

        if (DEBUG) {
            long assignedMinutes = schedule.getAssignedMinutes(selected);
            long remainingMinutes = Math.max(0, getAgreedMinutes(selected) - assignedMinutes);

            System.out.println("Selected: " + selected.getName()
                    + " for " + slot.getDay()
                    + " " + slot.getRange()
                    + " [" + slot.getRequiredSkill() + "]"
                    + " | assigned now: " + formatHours(assignedMinutes)
                    + " | remaining: " + formatHours(remainingMinutes));
            System.out.println();
        }
    }

    private CandidateScore scoreCandidate(WeeklySchedule schedule,
                                          Employee employee,
                                          ShiftSlot slot,
                                          AssignmentPhase phase) {
        Skill requiredSkill = slot.getRequiredSkill();

        int roleScore = scoreRole(employee, requiredSkill, phase);
        int hoursScore = scoreHours(schedule, employee);
        int priorityScore = scorePriority(employee);
        int opportunityScore = scoreOpportunity(employee, requiredSkill, phase);
        int continuityScore = scoreContinuity(schedule, employee, slot);
        int protectScore = scoreProtect(employee, requiredSkill, phase);
        int lateScore = scoreLate(slot);
        int futureScore = scoreFutureCriticalProtection(schedule, employee, slot, phase);
        int flexScore = scoreFlex(employee, phase);

        int total = roleScore
                + hoursScore
                + priorityScore
                + opportunityScore
                + continuityScore
                + protectScore
                + lateScore
                + futureScore
                + flexScore;

        return new CandidateScore(
                employee,
                total,
                roleScore,
                hoursScore,
                priorityScore,
                opportunityScore,
                continuityScore,
                protectScore,
                lateScore,
                futureScore,
                flexScore
        );
    }


    private boolean attemptTakeover(WeeklySchedule schedule,
                                    Employee target,
                                    boolean protectiveMode) {
        List<Assignment> assignments = new ArrayList<>(schedule.getAssignments());

        assignments.sort(
                Comparator.comparing((Assignment a) -> takeoverSlotPriority(a.getSlot()))
                        .thenComparing(a -> schedule.getAssignedMinutes(a.getEmployee()), Comparator.reverseOrder())
        );

        for (Assignment assignment : assignments) {
            Employee donor = assignment.getEmployee();
            ShiftSlot slot = assignment.getSlot();

            if (donor.equals(target)) {
                continue;
            }

            if (!isTakeoverSlot(slot)) {
                continue;
            }

            if (!canTakeOver(schedule, target, donor, slot, protectiveMode)) {
                continue;
            }

            schedule.unassign(slot);
            assign(schedule, slot, target);

            if (DEBUG) {
                System.out.println("------------------------------------------------------------");
                System.out.println(protectiveMode ? "REBALANCE TAKEOVER" : "REBALANCE RESIDUAL HIGH");
                System.out.println("------------------------------------------------------------");
                System.out.println(target.getName()
                        + " takes "
                        + slot.getDay() + " "
                        + slot.getRange()
                        + " [" + slot.getRequiredSkill() + "] from "
                        + donor.getName());
                System.out.println();
            }

            return true;
        }

        return false;
    }

    private boolean canTakeOver(WeeklySchedule schedule,
                                Employee target,
                                Employee donor,
                                ShiftSlot slot,
                                boolean protectiveMode) {
        if (!canAssign(schedule, target, slot)) {
            return false;
        }

        long targetMissing = getMissingMinutes(schedule, target);
        if (targetMissing <= 0) {
            return false;
        }

        if (slot.getRequiredSkill() == Skill.RESP) {
            return false;
        }

        if (protectiveMode) {
            if (isHigh(donor) && isCriticalHighEmployee(donor)) {
                return false;
            }
        } else {
            if (isRespHigh(donor)) {
                return false;
            }
        }

        return true;
    }


    private void assign(WeeklySchedule schedule, ShiftSlot slot, Employee employee) {
        schedule.assign(slot, employee);
        applyOpeningRule(schedule, employee, slot);
    }

    private void applyOpeningRule(WeeklySchedule schedule, Employee employee, ShiftSlot slot) {
        if (slot.getRequiredSkill() != Skill.OPENING) {
            return;
        }

        if (!employee.hasSkill(Skill.BAR)) {
            return;
        }

        for (ShiftSlot other : schedule.getSlots()) {
            if (other == slot) {
                continue;
            }

            if (other.getRequiredSkill() == Skill.BAR
                    && other.getDay().equals(slot.getDay())
                    && other.sameMomentAs(slot)) {
                other.setRequiredSkill(Skill.WAITER);
            }
        }
    }


    private int scoreRole(Employee employee, Skill requiredSkill, AssignmentPhase phase) {
        if (!employee.hasSkill(requiredSkill)) {
            return 0;
        }

        Proficiency proficiency = employee.getProficiency(requiredSkill);

        int base = switch (proficiency) {
            case HIGH -> SCORE_ROLE_HIGH;
            case MID -> SCORE_ROLE_MID;
            case LOW -> SCORE_ROLE_LOW;
        };

        if (phase == AssignmentPhase.RESP && requiredSkill == Skill.RESP && isHigh(employee)) {
            base += BONUS_RESP_CRITICAL;
        }

        if (phase == AssignmentPhase.CRITICAL_HIGH && isHigh(employee) && isCriticalHighEmployee(employee)) {
            base += BONUS_CRITICAL_HIGH_PHASE;
        }

        return base;
    }

    private int scoreHours(WeeklySchedule schedule, Employee employee) {
        return (int) Math.max(0, getMissingMinutes(schedule, employee) / 4);
    }

    private int scorePriority(Employee employee) {
        return switch (employee.getPriority()) {
            case HIGH -> SCORE_PRIORITY_HIGH;
            case MEDIUM -> SCORE_PRIORITY_MEDIUM;
            case LOW -> SCORE_PRIORITY_LOW;
        };
    }

    private int scoreOpportunity(Employee employee, Skill requiredSkill, AssignmentPhase phase) {
        int score = 0;

        if ((phase == AssignmentPhase.CRITICAL_HIGH || phase == AssignmentPhase.REBALANCE_HIGH)
                && isRespEmployee(employee)
                && requiredSkill != Skill.RESP) {
            score += BONUS_OPPORTUNITY_RESP;
        }

        return score;
    }

    private int scoreContinuity(WeeklySchedule schedule, Employee employee, ShiftSlot slot) {
        List<Assignment> dayAssignments = schedule.getAssignmentsFor(employee).stream()
                .filter(a -> a.getSlot().getDay().equals(slot.getDay()))
                .toList();

        for (Assignment assignment : dayAssignments) {
            ShiftSlot existing = assignment.getSlot();

            if (existing.getRange().getEnd().equals(slot.getRange().getStart())
                    || slot.getRange().getEnd().equals(existing.getRange().getStart())) {
                return BONUS_CONTINUITY;
            }
        }

        return 0;
    }

    private int scoreProtect(Employee employee, Skill requiredSkill, AssignmentPhase phase) {
        if (phase == AssignmentPhase.STANDARD && isHigh(employee) && isCriticalHighEmployee(employee)) {
            return PENALTY_PROTECT_CRITICAL;
        }

        if (phase == AssignmentPhase.CRITICAL_HIGH && isRespEmployee(employee) && requiredSkill != Skill.RESP) {
            return PENALTY_RESP_USED_OUTSIDE_ROLE;
        }

        return 0;
    }

    private int scoreLate(ShiftSlot slot) {
        String end = slot.getRange().getEnd().toString();

        if ("00:00".equals(end) || "00:30".equals(end) || "01:00".equals(end)) {
            return 84;
        }
        if ("23:00".equals(end) || "23:30".equals(end)) {
            return 64;
        }
        if ("22:00".equals(end) || "22:30".equals(end)) {
            return 40;
        }
        return 0;
    }

    private int scoreFutureCriticalProtection(WeeklySchedule schedule,
                                              Employee employee,
                                              ShiftSlot currentSlot,
                                              AssignmentPhase phase) {
        if (phase != AssignmentPhase.RESP && phase != AssignmentPhase.CRITICAL_HIGH) {
            return 0;
        }

        int count = 0;

        for (ShiftSlot slot : schedule.getUnassignedSlots()) {
            if (slot == currentSlot) {
                continue;
            }

            if (dayPriority(slot.getDay()) >= dayPriority(currentSlot.getDay())) {
                continue;
            }

            Skill skill = slot.getRequiredSkill();
            if ((skill == Skill.RESP || skill == Skill.OPENING || skill == Skill.BAR)
                    && employee.hasSkill(skill)) {
                count++;
            }
        }

        return count * 10;
    }

    private int scoreFlex(Employee employee, AssignmentPhase phase) {
        if (phase == AssignmentPhase.RESP) {
            return 0;
        }

        return switch (employee.getPriority()) {
            case HIGH -> 20;
            case MEDIUM -> 12;
            case LOW -> 6;
        };
    }

    private boolean canAssign(WeeklySchedule schedule, Employee employee, ShiftSlot slot) {
        LocalDate date = schedule.getDateFor(slot);

        if (!employee.isAssignableTo(slot, date)) {
            return false;
        }

        if (schedule.hasOverlappingAssignment(employee, slot)) {
            return false;
        }

        long currentDayMinutes = schedule.getAssignedMinutesFor(employee, slot.getDay());
        long newTotal = currentDayMinutes + slot.durationMinutes();

        return newTotal <= MAX_DAILY_MINUTES;
    }

    private boolean isHigh(Employee employee) {
        return employee.getPriority() == Priority.HIGH;
    }

    private boolean isRespEmployee(Employee employee) {
        return employee.hasSkill(Skill.RESP);
    }

    private boolean isRespHigh(Employee employee) {
        return isHigh(employee) && isRespEmployee(employee);
    }

    private boolean isCriticalHighEmployee(Employee employee) {
        return employee.hasSkill(Skill.RESP)
                || employee.hasSkill(Skill.OPENING)
                || employee.hasSkill(Skill.BAR);
    }

    private boolean isTakeoverSlot(ShiftSlot slot) {
        return slot.getRequiredSkill() != Skill.RESP;
    }

    private long getAgreedMinutes(Employee employee) {
        return employee.getAgreedHours() * 60L;
    }

    private long getMissingMinutes(WeeklySchedule schedule, Employee employee) {
        return Math.max(0, getAgreedMinutes(employee) - schedule.getAssignedMinutes(employee));
    }


    private Comparator<ShiftSlot> respSlotComparator() {
        return Comparator
                .comparingInt((ShiftSlot slot) -> dayPriority(slot.getDay()))
                .thenComparingInt(slot -> criticalWindowPriority(slot))
                .thenComparingLong(ShiftSlot::durationMinutes)
                .reversed();
    }

    private Comparator<ShiftSlot> generalSlotComparator() {
        return Comparator
                .comparingInt((ShiftSlot slot) -> skillPriority(slot.getRequiredSkill()))
                .thenComparingInt(slot -> dayPriority(slot.getDay()))
                .thenComparingLong(ShiftSlot::durationMinutes)
                .reversed();
    }

    private int skillPriority(Skill skill) {
        return switch (skill) {
            case RESP -> 600;
            case OPENING -> 500;
            case BAR -> 400;
            case KITCHEN -> 300;
            case WAITER -> 200;
            case RUNNER -> 100;
        };
    }

    private int dayPriority(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> 700;
            case SUNDAY -> 600;
            case FRIDAY -> 500;
            case THURSDAY -> 400;
            case WEDNESDAY -> 300;
            case TUESDAY -> 200;
            case MONDAY -> 100;
        };
    }

    private int criticalWindowPriority(ShiftSlot slot) {
        String start = slot.getRange().getStart().toString();

        if (start.compareTo("16:00") >= 0) {
            return 100;
        }
        if (start.compareTo("11:00") >= 0) {
            return 60;
        }
        return 20;
    }

    private int takeoverSlotPriority(ShiftSlot slot) {
        return switch (slot.getRequiredSkill()) {
            case WAITER -> 1;
            case RUNNER -> 2;
            case BAR -> 3;
            case OPENING -> 4;
            case KITCHEN -> 5;
            case RESP -> 99;
        };
    }

    private void debugHeader(String title) {
        if (!DEBUG) {
            return;
        }

        System.out.println("------------------------------------------------------------");
        System.out.println(title);
        System.out.println("------------------------------------------------------------");
        System.out.println();
    }

    private void printDebugBlock(WeeklySchedule schedule,
                                 ShiftSlot slot,
                                 List<CandidateScore> candidates) {
        System.out.println("============================================================");
        System.out.println("SCHEDULER DEBUG");
        System.out.println("============================================================");
        System.out.println("Slot: " + slot.getDay()
                + " " + schedule.getDateFor(slot)
                + " " + slot.getRange()
                + " [" + slot.getRequiredSkill() + "]"
                + " | candidates: " + candidates.size());
        System.out.println("Candidates:");

        for (CandidateScore c : candidates) {
            Employee e = c.employee();
            long assigned = schedule.getAssignedMinutes(e);
            long missing = getMissingMinutes(schedule, e);

            System.out.printf(
                    Locale.US,
                    "- %-15s total=%4d | role=%3d | hrs=%3d | pri=%2d | opp=%3d | cont=%2d | protect=%4d | late=%3d | future=%3d | flex=%3d | assigned=%5s | missing=%5s%n",
                    e.getName(),
                    c.total(),
                    c.role(),
                    c.hours(),
                    c.priority(),
                    c.opportunity(),
                    c.continuity(),
                    c.protect(),
                    c.late(),
                    c.future(),
                    c.flex(),
                    formatHours(assigned),
                    formatHours(missing)
            );
        }
    }

    private void printHoursSummary(WeeklySchedule schedule, List<Employee> employees) {
        System.out.println("------------------------------------------------------------");
        System.out.println("HOURS SUMMARY");
        System.out.println("------------------------------------------------------------");

        List<Employee> sorted = employees.stream()
                .sorted(Comparator.comparing(Employee::getName))
                .collect(Collectors.toList());

        for (Employee employee : sorted) {
            long assigned = schedule.getAssignedMinutes(employee);
            long agreed = getAgreedMinutes(employee);
            long missing = Math.max(0, agreed - assigned);

            System.out.printf(
                    Locale.US,
                    "%-15s assigned: %6s   agreed: %6s   missing: %6s%n",
                    employee.getName(),
                    formatHours(assigned),
                    formatHours(agreed),
                    formatHours(missing)
            );
        }

        System.out.println();
    }

    private String formatHours(long minutes) {
        long hours = minutes / 60;
        long rem = minutes % 60;
        return String.format(Locale.US, "%d,%01dh", hours, rem == 0 ? 0 : 5);
    }

    private enum AssignmentPhase {
        RESP,
        CRITICAL_HIGH,
        STANDARD,
        REBALANCE_HIGH
    }

    private record CandidateScore(
            Employee employee,
            int total,
            int role,
            int hours,
            int priority,
            int opportunity,
            int continuity,
            int protect,
            int late,
            int future,
            int flex
    ) implements Comparable<CandidateScore> {
        @Override
        public int compareTo(CandidateScore other) {
            return Integer.compare(this.total, other.total);
        }
    }
}