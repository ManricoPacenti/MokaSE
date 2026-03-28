package it.pacenti.moka.view;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.scheduling.Assignment;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklySchedule;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Prints a weekly schedule in a grid format:
 * rows = time windows
 * columns = actual required positions
 * cells = assigned employee, or required skill when unassigned
 *
 * Column generation rule:
 * - slots with the same skill share the same column if they do not overlap
 * - overlapping slots with the same skill are placed on separate lanes
 *
 * Example:
 * KITCHEN 10:00-14:00
 * KITCHEN 14:00-18:00
 * -> one column: KITCHEN
 *
 * KITCHEN 10:00-14:00
 * KITCHEN 12:00-16:00
 * -> two columns: KITCHEN, KITCHEN#2
 */
public class SchedulePrinter {

    private static final int SLOT_MINUTES = 30;
    private static final int TIME_COLUMN_WIDTH = 13;
    private static final int CELL_WIDTH = 14;

    public void printWeeklyGrid(WeeklySchedule schedule) {
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        boolean printedSomething = false;

        for (DayOfWeek day : DayOfWeek.values()) {
            boolean printedDay = printDayGrid(schedule, day);
            if (printedDay) {
                System.out.println();
                printedSomething = true;
            }
        }

        if (!printedSomething) {
            System.out.println("Schedule contains no assignments.");
        }
    }

    public void printWeeklyGrid(WeeklySchedule schedule, WeeklyScheduleTemplate template) {
        Objects.requireNonNull(schedule, "Schedule cannot be null");
        Objects.requireNonNull(template, "Template cannot be null");

        boolean printedSomething = false;

        for (DayOfWeek day : DayOfWeek.values()) {
            boolean printedDay = printDayGrid(schedule, template, day);
            if (printedDay) {
                System.out.println();
                printedSomething = true;
            }
        }

        if (!printedSomething) {
            System.out.println("Template contains no slots.");
        }
    }

    public boolean printDayGrid(WeeklySchedule schedule, DayOfWeek day) {
        Objects.requireNonNull(schedule, "Schedule cannot be null");
        Objects.requireNonNull(day, "Day cannot be null");

        List<Assignment> assignments = schedule.getAssignmentsFor(day);

        if (assignments.isEmpty()) {
            return false;
        }

        List<ShiftSlot> daySlots = assignments.stream()
                .map(Assignment::getSlot)
                .sorted(slotComparator())
                .toList();

        IdentityHashMap<ShiftSlot, Integer> laneBySlot = assignLanes(daySlots);
        List<SkillColumn> columns = buildColumns(daySlots, laneBySlot);

        int minMinute = findMinMinuteFromAssignments(assignments);
        int maxMinute = findMaxMinuteFromAssignments(assignments);
        LocalDate date = resolveDate(schedule.getWeekStart(), day);

        System.out.println(day + " " + date);
        printHeader(columns);

        for (int minute = minMinute; minute < maxMinute; minute += SLOT_MINUTES) {
            StringBuilder row = new StringBuilder();
            row.append(padRight(formatTimeWindow(minute), TIME_COLUMN_WIDTH)).append(" | ");

            for (SkillColumn column : columns) {
                String employeeName = findEmployeeName(assignments, minute, column, laneBySlot);
                row.append(padRight(employeeName, CELL_WIDTH)).append(" | ");
            }

            System.out.println(row);
        }

        return true;
    }

    public boolean printDayGrid(WeeklySchedule schedule, WeeklyScheduleTemplate template, DayOfWeek day) {
        Objects.requireNonNull(schedule, "Schedule cannot be null");
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(day, "Day cannot be null");

        List<ShiftSlot> templateSlots = template.getSlots()
                .stream()
                .filter(slot -> slot.getDay().equals(day))
                .sorted(slotComparator())
                .toList();

        if (templateSlots.isEmpty()) {
            return false;
        }

        List<Assignment> assignments = schedule.getAssignmentsFor(day);
        IdentityHashMap<ShiftSlot, Integer> laneBySlot = assignLanes(templateSlots);
        List<SkillColumn> columns = buildColumns(templateSlots, laneBySlot);

        int minMinute = findMinMinuteFromTemplate(templateSlots);
        int maxMinute = findMaxMinuteFromTemplate(templateSlots);
        LocalDate date = resolveDate(template.getWeekStart(), day);

        System.out.println(day + " " + date);
        printHeader(columns);

        for (int minute = minMinute; minute < maxMinute; minute += SLOT_MINUTES) {
            StringBuilder row = new StringBuilder();
            row.append(padRight(formatTimeWindow(minute), TIME_COLUMN_WIDTH)).append(" | ");

            for (SkillColumn column : columns) {
                String cellValue = findCellValue(assignments, templateSlots, minute, column, laneBySlot);
                row.append(padRight(cellValue, CELL_WIDTH)).append(" | ");
            }

            System.out.println(row);
        }

        return true;
    }

    private IdentityHashMap<ShiftSlot, Integer> assignLanes(List<ShiftSlot> slots) {
        IdentityHashMap<ShiftSlot, Integer> laneBySlot = new IdentityHashMap<>();

        for (ShiftSlot slot : slots) {
            int lane = findFirstAvailableLane(slot, laneBySlot);
            laneBySlot.put(slot, lane);
        }

        return laneBySlot;
    }

    private int findFirstAvailableLane(ShiftSlot target, IdentityHashMap<ShiftSlot, Integer> laneBySlot) {
        int lane = 0;

        while (true) {
            boolean occupied = false;

            for (ShiftSlot existing : laneBySlot.keySet()) {
                if (existing.getRequiredSkill() != target.getRequiredSkill()) {
                    continue;
                }

                if (laneBySlot.get(existing) != lane) {
                    continue;
                }

                if (existing.overlaps(target)) {
                    occupied = true;
                    break;
                }
            }

            if (!occupied) {
                return lane;
            }

            lane++;
        }
    }

    private List<SkillColumn> buildColumns(List<ShiftSlot> slots, IdentityHashMap<ShiftSlot, Integer> laneBySlot) {
        List<SkillColumn> columns = new ArrayList<>();

        for (Skill skill : collectOrderedSkills(slots)) {
            int laneCount = findLaneCountForSkill(skill, slots, laneBySlot);

            for (int lane = 0; lane < laneCount; lane++) {
                columns.add(new SkillColumn(skill, lane));
            }
        }

        return columns;
    }

    private List<Skill> collectOrderedSkills(List<ShiftSlot> slots) {
        List<Skill> orderedSkills = new ArrayList<>();

        for (ShiftSlot slot : slots) {
            if (!orderedSkills.contains(slot.getRequiredSkill())) {
                orderedSkills.add(slot.getRequiredSkill());
            }
        }

        return orderedSkills;
    }

    private int findLaneCountForSkill(Skill skill,
                                      List<ShiftSlot> slots,
                                      IdentityHashMap<ShiftSlot, Integer> laneBySlot) {
        int maxLane = -1;

        for (ShiftSlot slot : slots) {
            if (slot.getRequiredSkill() == skill) {
                maxLane = Math.max(maxLane, laneBySlot.get(slot));
            }
        }

        return maxLane + 1;
    }

    private void printHeader(List<SkillColumn> columns) {
        StringBuilder header = new StringBuilder();
        header.append(padRight("TIME", TIME_COLUMN_WIDTH)).append(" | ");

        for (SkillColumn column : columns) {
            header.append(padRight(column.label(), CELL_WIDTH)).append(" | ");
        }

        System.out.println(header);
        System.out.println("-".repeat(header.length()));
    }

    private String findEmployeeName(List<Assignment> assignments,
                                    int minute,
                                    SkillColumn column,
                                    IdentityHashMap<ShiftSlot, Integer> laneBySlot) {
        for (Assignment assignment : assignments) {
            ShiftSlot slot = assignment.getSlot();

            if (slot.getRequiredSkill() != column.skill()) {
                continue;
            }

            Integer lane = laneBySlot.get(slot);
            if (lane == null || lane != column.laneIndex()) {
                continue;
            }

            if (!containsMinute(slot.getRange(), minute)) {
                continue;
            }

            Employee employee = assignment.getEmployee();
            return shorten(employee.getName(), CELL_WIDTH);
        }

        return "";
    }

    private String findCellValue(List<Assignment> assignments,
                                 List<ShiftSlot> templateSlots,
                                 int minute,
                                 SkillColumn column,
                                 IdentityHashMap<ShiftSlot, Integer> laneBySlot) {
        for (Assignment assignment : assignments) {
            ShiftSlot slot = assignment.getSlot();

            if (slot.getRequiredSkill() != column.skill()) {
                continue;
            }

            Integer lane = laneBySlot.get(slot);
            if (lane == null || lane != column.laneIndex()) {
                continue;
            }

            if (!containsMinute(slot.getRange(), minute)) {
                continue;
            }

            return shorten(assignment.getEmployee().getName(), CELL_WIDTH);
        }

        for (ShiftSlot slot : templateSlots) {
            if (slot.getRequiredSkill() != column.skill()) {
                continue;
            }

            Integer lane = laneBySlot.get(slot);
            if (lane == null || lane != column.laneIndex()) {
                continue;
            }

            if (!containsMinute(slot.getRange(), minute)) {
                continue;
            }

            return shorten(column.skill().name(), CELL_WIDTH);
        }

        return "";
    }

    private int findMinMinuteFromAssignments(List<Assignment> assignments) {
        return assignments.stream()
                .mapToInt(a -> toMinute(a.getSlot().getRange().getStart()))
                .min()
                .orElse(0);
    }

    private int findMaxMinuteFromAssignments(List<Assignment> assignments) {
        return assignments.stream()
                .mapToInt(a -> normalizedEndMinute(a.getSlot().getRange()))
                .max()
                .orElse(0);
    }

    private int findMinMinuteFromTemplate(List<ShiftSlot> slots) {
        return slots.stream()
                .mapToInt(slot -> toMinute(slot.getRange().getStart()))
                .min()
                .orElse(0);
    }

    private int findMaxMinuteFromTemplate(List<ShiftSlot> slots) {
        return slots.stream()
                .mapToInt(slot -> normalizedEndMinute(slot.getRange()))
                .max()
                .orElse(0);
    }

    private Comparator<ShiftSlot> slotComparator() {
        return Comparator
                .comparing(ShiftSlot::getDay)
                .thenComparing(slot -> slot.getRange().getStart())
                .thenComparing(slot -> slot.getRange().getEnd())
                .thenComparing(slot -> slot.getRequiredSkill().name());
    }

    private boolean containsMinute(TimeRange range, int minute) {
        int start = toMinute(range.getStart());
        int end = normalizedEndMinute(range);

        int adjustedMinute = minute;

        if (range.crossesMidnight() && minute < toMinute(range.getEnd())) {
            adjustedMinute += 24 * 60;
        }

        return adjustedMinute >= start && adjustedMinute < end;
    }

    private int toMinute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private int normalizedEndMinute(TimeRange range) {
        int end = toMinute(range.getEnd());
        return range.crossesMidnight() ? end + 24 * 60 : end;
    }

    private String formatTimeWindow(int minute) {
        return formatMinute(minute) + "-" + formatMinute(minute + SLOT_MINUTES);
    }

    private String formatMinute(int minute) {
        int normalized = minute % (24 * 60);
        int hour = normalized / 60;
        int min = normalized % 60;
        return String.format("%02d:%02d", hour, min);
    }

    private LocalDate resolveDate(LocalDate weekStart, DayOfWeek targetDay) {
        int startDayValue = weekStart.getDayOfWeek().getValue();
        int targetValue = targetDay.getValue();
        int offset = targetValue - startDayValue;
        return weekStart.plusDays(offset);
    }

    private String padRight(String text, int width) {
        String safe = text == null ? "" : text;
        if (safe.length() > width) {
            safe = shorten(safe, width);
        }
        return String.format("%-" + width + "s", safe);
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }

        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Represents one visible column in the grid.
     * laneIndex = 0 means the primary lane for that skill.
     * laneIndex > 0 means an additional overlapping lane.
     */
    private static final class SkillColumn {
        private final Skill skill;
        private final int laneIndex;

        private SkillColumn(Skill skill, int laneIndex) {
            this.skill = Objects.requireNonNull(skill, "Skill cannot be null");
            this.laneIndex = laneIndex;
        }

        public Skill skill() {
            return skill;
        }

        public int laneIndex() {
            return laneIndex;
        }

        public String label() {
            return laneIndex == 0 ? skill.name() : skill.name() + "#" + (laneIndex + 1);
        }
    }
}