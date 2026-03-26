package it.pacenti.moka.persistence;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.scheduling.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonEmployeeRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndReloadEmployeeFromJsonFile() {
        Path filePath = tempDir.resolve("employees.json");

        JsonEmployeeRepositoryTest repository = new JsonEmployeeRepositoryTest(filePath);
        EmployeeFactory employeeFactory = new EmployeeFactory();

        Employee employee = employeeFactory.createEmployee(
                "Luca Rossi",
                Priority.HIGH,
                40,
                14
        );

        employee.getSkills().addOrUpdate(Skill.KITCHEN, Proficiency.HIGH);
        employee.getSkills().addOrUpdate(Skill.WAITER, Proficiency.MID);

        TimeRange mondayLunchOff = new TimeRange(
                LocalTime.of(12, 0),
                LocalTime.of(15, 0)
        );
        employee.getAvailability().addTimeOff(DayOfWeek.MONDAY, mondayLunchOff);

        Leave leave = new Leave(
                LocalDate.of(2026, 3, 28),
                new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                it.pacenti.moka.availability.LeaveType.VACATION,
                "Family trip"
        );
        employee.addLeave(leave);

        repository.save(employee);

        JsonEmployeeRepositoryTest reloadedRepository = new JsonEmployeeRepositoryTest(filePath);

        Optional<Employee> loadedOptional = reloadedRepository.findByName("Luca Rossi");

        assertTrue(loadedOptional.isPresent(), "Employee should be found after reload");

        Employee loaded = loadedOptional.get();

        assertEquals("Luca Rossi", loaded.getName());
        assertEquals(Priority.HIGH, loaded.getPriority());
        assertEquals(40, loaded.getAgreedHours());
        assertEquals(14, loaded.getHourlyCost());

        assertTrue(loaded.hasSkill(Skill.KITCHEN));
        assertTrue(loaded.hasSkill(Skill.WAITER));
        assertEquals(Proficiency.HIGH, loaded.getProficiency(Skill.KITCHEN));
        assertEquals(Proficiency.MID, loaded.getProficiency(Skill.WAITER));

        assertFalse(
                loaded.getAvailability().isAvailable(DayOfWeek.MONDAY, mondayLunchOff),
                "Employee should remain unavailable in the persisted Monday lunch slot"
        );

        assertTrue(
                loaded.getLeaveCalendar().isOnLeave(
                        LocalDate.of(2026, 3, 28),
                        new TimeRange(LocalTime.of(19, 0), LocalTime.of(22, 0))
                ),
                "Employee should remain on leave after reload"
        );

        assertEquals(1, loaded.getLeaveCalendar().getLeaves().size());
        assertEquals("Family trip", loaded.getLeaveCalendar().getLeaves().get(0).getNote());
    }
}