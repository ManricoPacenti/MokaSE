package it.pacenti.moka.view;

import it.pacenti.moka.employee.EmployeeSkill;
import it.pacenti.moka.scheduling.*;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.Skill;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prints a weekly schedule in a grid format:
 * rows = time slots
 * columns = required skills
 * cells = assigned employee
 */
public class SchedulePrinter {

    private static final int SLOT_MINUTES = 30;

    public void printWeeklyGrid(WeeklySchedule schedule) {
        Objects.requireNonNull(schedule, "Schedule cannot be null");

        for (DayOfWeek day : DayOfWeek.values()) {
            printDayGrid(schedule, day);
            System.out.println();
        }
    }

    public void printDayGrid(WeeklySchedule schedule, DayOfWeek day) {
        Objects.requireNonNull(schedule, "Schedule cannot be null");
        Objects.requireNonNull(day, "Day cannot be null");

        List<Assignment> assignments = schedule.getAssignmentsFor(day);

        if (assignments.isEmpty()){
            return;
        }

        LocalDate date = resolveDate(schedule.getWeekStart(), day);

        List<Skill> skills = collectSkills(assignments);
        int minMinute = findMinMinute(assignments);
        int maxMinute = findMaxMinute(assignments);

        System.out.println(day + " " + date);
        printHeader(skills);

        for(int minute = minMinute; minute <maxMinute; minute += SLOT_MINUTES) {
            StringBuilder row = new StringBuilder();
            row.append(String.format("%-6s | ", formatMinute(minute)));

            for(Skill skill : skills) {
                String employeeName = findEmployeeName(assignments, day, minute, skill);
                row.append(String.format("%-10s| ", employeeName));
            }

            System.out.println(row);
        }
    }
    private List<Skill> collectSkills(List<Assignment> assignments) {
        return assignments.stream()
                .map(a -> a.getSlot().getRequiredSkill())
                .distinct()
                .collect(Collectors.toList());
    }

    private int findMinMinute(List<Assignment> assignments) {
        return assignments.stream()
                .mapToInt(a -> toMinute(a.getSlot().getRange().getStart()))
                .min().orElse(0);
    }

    private int findMaxMinute(List<Assignment> assignments) {
        return assignments.stream()
                .mapToInt(a-> normalizedEndMinute(a.getSlot().getRange()))
                .max().orElse(0);
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

    private String findEmployeeName(List<Assignment> assignments, DayOfWeek day, int minute, Skill skill) {
        for (Assignment assignment : assignments) {
            ShiftSlot slot = assignment.getSlot();

            if (!slot.getDay().equals(day)) {
                continue;
            }
            if (slot.getRequiredSkill() != skill) {
                continue;
            }
            if (containsMinute(slot.getRange(), minute)) {
                Employee employee = assignment.getEmployee();
                return employee.getName();
            }
        }

        return "";
    }

    private boolean containsMinute(TimeRange range, int minute) {
        int start = toMinute(range.getStart());
        int end = normalizedEndMinute(range);

        int adjustedMinute = minute;

        //nel caso i minuti superano mezzanotte
        if (range.crossesMidnight() && minute < toMinute(range.getEnd())) {
            adjustedMinute += 24*60;
        }
        return adjustedMinute >= start && adjustedMinute < end;
    }

    private int toMinute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private int normalizedEndMinute(TimeRange range) {
        int end = toMinute(range.getEnd());
        return range.crossesMidnight() ? end + 24*60 : end;
    }

    private String formatMinute(int minute) {
        int normalized = minute % (24*60);
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
