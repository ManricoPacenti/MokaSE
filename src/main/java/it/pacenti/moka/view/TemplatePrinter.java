package it.pacenti.moka.view;

import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Prints a weekly schedule template in a grid format:
 * rows = time slots
 * columns = required skills
 * cells = required skill label
 */
public class TemplatePrinter {

    private static final int SLOT_MINUTES = 30;

    public void printWeeklyGrid(WeeklyScheduleTemplate template) {
        Objects.requireNonNull(template, "Template cannot be null");

        if (template.isEmpty()) {
            System.out.println("Current template is empty.");
            return;
        }

        for (DayOfWeek day : DayOfWeek.values()) {
            printDayGrid(template, day);
            System.out.println();
        }
    }

    public void printDayGrid(WeeklyScheduleTemplate template, DayOfWeek day) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(day, "Day cannot be null");

        List<ShiftSlot> slots = template.getSlots()
                .stream()
                .filter(slot -> slot.getDay().equals(day))
                .toList();

        if (slots.isEmpty()) {
            return;
        }

        LocalDate date = resolveDate(template.getWeekStart(), day);

        List<Skill> skills = collectSkills(slots);
        int minMinute = findMinMinute(slots);
        int maxMinute = findMaxMinute(slots);

        System.out.println(day + " " + date);
        printHeader(skills);

        for (int minute = minMinute; minute < maxMinute; minute += SLOT_MINUTES) {
            StringBuilder row = new StringBuilder();
            row.append(String.format("%-6s | ", formatMinute(minute)));

            for (Skill skill : skills) {
                String label = findSkillLabel(slots, day, minute, skill);
                row.append(String.format("%-10s| ", label));
            }

            System.out.println(row);
        }
    }

    public void printIndexedList(WeeklyScheduleTemplate template) {
        Objects.requireNonNull(template, "Template cannot be null");

        List<ShiftSlot> slots = getSortedSlots(template);

        if (slots.isEmpty()) {
            System.out.println("Current template is empty.");
            return;
        }

        System.out.println("============================================================");
        System.out.println("CURRENT TEMPLATE SLOTS");
        System.out.println("============================================================");

        for (int i = 0; i < slots.size(); i++) {
            ShiftSlot slot = slots.get(i);
            System.out.printf(
                    "[%d] %s | %s - %s | %s%n",
                    i + 1,
                    slot.getDay(),
                    slot.getRange().getStart(),
                    slot.getRange().getEnd(),
                    slot.getRequiredSkill()
            );
        }

        System.out.println();
    }

    public List<ShiftSlot> getSortedSlots(WeeklyScheduleTemplate template) {
        Objects.requireNonNull(template, "Template cannot be null");

        return template.getSlots()
                .stream()
                .sorted(Comparator
                        .comparing(ShiftSlot::getDay)
                        .thenComparing(slot -> slot.getRange().getStart())
                        .thenComparing(slot -> slot.getRange().getEnd())
                        .thenComparing(slot -> slot.getRequiredSkill().name()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Skill> collectSkills(List<ShiftSlot> slots) {
        return slots.stream()
                .map(ShiftSlot::getRequiredSkill)
                .distinct()
                .collect(Collectors.toList());
    }

    private int findMinMinute(List<ShiftSlot> slots) {
        return slots.stream()
                .mapToInt(slot -> toMinute(slot.getRange().getStart()))
                .min()
                .orElse(0);
    }

    private int findMaxMinute(List<ShiftSlot> slots) {
        return slots.stream()
                .mapToInt(slot -> normalizedEndMinute(slot.getRange()))
                .max()
                .orElse(0);
    }

    private void printHeader(List<Skill> skills) {
        StringBuilder header = new StringBuilder();
        header.append(String.format("%-6s | ", "TIME"));

        for (Skill skill : skills) {
            header.append(String.format("%-10s| ", skill));
        }

        System.out.println(header);
        System.out.println("-".repeat(header.length()));
    }

    private String findSkillLabel(List<ShiftSlot> slots, DayOfWeek day, int minute, Skill skill) {
        for (ShiftSlot slot : slots) {
            if (!slot.getDay().equals(day)) {
                continue;
            }
            if (slot.getRequiredSkill() != skill) {
                continue;
            }
            if (containsMinute(slot.getRange(), minute)) {
                return skill.name();
            }
        }

        return "";
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
}