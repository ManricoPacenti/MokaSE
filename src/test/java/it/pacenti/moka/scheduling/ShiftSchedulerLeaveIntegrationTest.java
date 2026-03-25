package it.pacenti.moka.scheduling;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftSchedulerLeaveIntegrationTest {

    @Test
    void shouldNotAssignEmployeeWhenApprovedLeaveOverlapsSlot() {
        // Arrange
        LocalDate weekStart = LocalDate.of(2026, 3, 30); // Monday

        TimeRange range = new TimeRange(LocalTime.of(10, 0), LocalTime.of(14, 0));
        ShiftSlot slot = new ShiftSlot(DayOfWeek.MONDAY, range, Skill.BAR);

        WeeklyScheduleTemplate template = new WeeklyScheduleTemplate(weekStart);
        template.addSlot(slot);

        EmployeeFactory factory = new EmployeeFactory();
        Employee employee = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        employee.getSkills().addOrUpdate(Skill.BAR, Proficiency.HIGH);

        Leave leave = new Leave(
                weekStart,
                range,
                LeaveType.VACATION,
                "Approved leave"
        );
        employee.addLeave(leave);

        ShiftScheduler scheduler = new ShiftScheduler();

        // Act
        WeeklySchedule schedule = scheduler.generateSchedule(template, List.of(employee));

        // Assert
        assertTrue(schedule.getAssignment(slot).isEmpty());
        assertTrue(schedule.getUnassignedSlots().contains(slot));
    }

    @Test
    void shouldAssignEmployeeWhenNoLeaveOverlapsSlot() {
        LocalDate weekStart = LocalDate.of(2026, 3, 30); // Monday

        TimeRange range = new TimeRange(LocalTime.of(10, 0), LocalTime.of(14, 0));
        ShiftSlot slot = new ShiftSlot(DayOfWeek.MONDAY, range, Skill.BAR);

        WeeklyScheduleTemplate template = new WeeklyScheduleTemplate(weekStart);
        template.addSlot(slot);

        EmployeeFactory factory = new EmployeeFactory();
        Employee employee = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);
        employee.getSkills().addOrUpdate(Skill.BAR, Proficiency.HIGH);

        ShiftScheduler scheduler = new ShiftScheduler();

        WeeklySchedule schedule = scheduler.generateSchedule(template, List.of(employee));

        assertTrue(schedule.getAssignment(slot).isPresent());
    }
}