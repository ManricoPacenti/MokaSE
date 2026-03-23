package it.pacenti.moka.app;

import it.pacenti.moka.availability.LeaveCalendar;
import it.pacenti.moka.availability.WeeklyAvailability;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeSkills;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.scheduling.Assignment;
import it.pacenti.moka.scheduling.ShiftScheduler;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklySchedule;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;
import it.pacenti.moka.view.SchedulePrinter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ConsoleApp {

    public static void main(String[] args) {

        System.out.println("=== Moka manual scheduler test ===\n");

        // -------------------------
        // Employees
        // -------------------------

        EmployeeSkills marcoSkills = new EmployeeSkills();
        marcoSkills.addOrUpdate(Skill.BAR, Proficiency.HIGH);
        marcoSkills.addOrUpdate(Skill.WAITER, Proficiency.MID);

        Employee marco = new Employee(
                "Marco",
                marcoSkills,
                new WeeklyAvailability(),
                new LeaveCalendar(),
                Priority.HIGH,
                40,
                12
        );

        EmployeeSkills lucaSkills = new EmployeeSkills();
        lucaSkills.addOrUpdate(Skill.WAITER, Proficiency.HIGH);
        lucaSkills.addOrUpdate(Skill.RUNNER, Proficiency.MID);

        Employee luca = new Employee(
                "Luca",
                lucaSkills,
                new WeeklyAvailability(),
                new LeaveCalendar(),
                Priority.MEDIUM,
                30,
                11
        );

        EmployeeSkills annaSkills = new EmployeeSkills();
        annaSkills.addOrUpdate(Skill.KITCHEN, Proficiency.HIGH);

        Employee anna = new Employee(
                "Anna",
                annaSkills,
                new WeeklyAvailability(),
                new LeaveCalendar(),
                Priority.HIGH,
                24,
                13
        );

        List<Employee> employees = List.of(marco, luca, anna);

        // -------------------------
        // Template
        // -------------------------

        WeeklyScheduleTemplate template = new WeeklyScheduleTemplate(
                LocalDate.of(2026, 3, 16) // Monday
        );

        template.addSlot(new ShiftSlot(
                DayOfWeek.SATURDAY,
                new TimeRange(LocalTime.of(17, 0), LocalTime.of(1, 0)),
                Skill.BAR
        ));

        template.addSlot(new ShiftSlot(
                DayOfWeek.SATURDAY,
                new TimeRange(LocalTime.of(17, 0), LocalTime.of(1, 0)),
                Skill.WAITER
        ));

        template.addSlot(new ShiftSlot(
                DayOfWeek.SATURDAY,
                new TimeRange(LocalTime.of(17, 0), LocalTime.of(1, 0)),
                Skill.KITCHEN
        ));

        // Extra slot to test overlap / uncovered slot
        template.addSlot(new ShiftSlot(
                DayOfWeek.SATURDAY,
                new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                Skill.BAR
        ));

        // -------------------------
        // Generate schedule
        // -------------------------

        ShiftScheduler scheduler = new ShiftScheduler();
        WeeklySchedule schedule = scheduler.generateSchedule(template, employees);

        // -------------------------
        // Raw output
        // -------------------------

        System.out.println("=== ASSIGNMENTS ===");
        for (Assignment assignment : schedule.getAssignments()) {
            System.out.println(
                    assignment.getSlot().getDay() + " | " +
                            assignment.getSlot().getRange() + " | " +
                            assignment.getSlot().getRequiredSkill() + " -> " +
                            assignment.getEmployee().getName()
            );
        }

        System.out.println("\n=== UNASSIGNED SLOTS ===");
        for (ShiftSlot slot : schedule.getUnassignedSlots()) {
            System.out.println(
                    slot.getDay() + " | " +
                            slot.getRange() + " | " +
                            slot.getRequiredSkill()
            );
        }

        System.out.println("\n=== WEEKLY HOURS ===");
        for (Employee employee : employees) {
            System.out.println(
                    employee.getName() + ": " +
                            schedule.getAssignedHours(employee) + " h"
            );
        }

        System.out.println("\n=== SATURDAY HOURS ===");
        for (Employee employee : employees) {
            System.out.println(
                    employee.getName() + ": " +
                            schedule.getAssignedHoursFor(employee, DayOfWeek.SATURDAY) + " h"
            );
        }

        // -------------------------
        // Grid output
        // -------------------------

        System.out.println("\n=== GRID VIEW ===");
        SchedulePrinter printer = new SchedulePrinter();
        printer.printWeeklyGrid(schedule);
    }
}