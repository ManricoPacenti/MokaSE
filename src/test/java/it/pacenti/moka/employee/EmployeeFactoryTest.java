package it.pacenti.moka.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeFactoryTest {

    @Test
    void shouldCreateEmployeeWhenInputIsValid() {
        EmployeeFactory factory = new EmployeeFactory();

        Employee employee = factory.createEmployee(
                "Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        assertNotNull(employee);
        assertEquals("Employee Test", employee.getName());
        assertEquals(Priority.MEDIUM, employee.getPriority());
        assertEquals(40, employee.getAgreedHours());
        assertEquals(12, employee.getHourlyCost());
        assertNotNull(employee.getSkills());
        assertNotNull(employee.getAvailability());
        assertNotNull(employee.getLeaveCalendar());
    }

    @Test
    void shouldTrimNameWhenCreatingEmployee() {
        EmployeeFactory factory = new EmployeeFactory();

        Employee employee = factory.createEmployee(
                "  Employee Test  ",
                Priority.MEDIUM,
                40,
                12
        );

        assertEquals("Employee Test", employee.getName());
    }

    @Test
    void shouldThrowExceptionWhenNameIsNull() {
        EmployeeFactory factory = new EmployeeFactory();

        assertThrows(NullPointerException.class, () ->
                factory.createEmployee(
                        null,
                        Priority.MEDIUM,
                        40,
                        12
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenPriorityIsNull() {
        EmployeeFactory factory = new EmployeeFactory();

        assertThrows(NullPointerException.class, () ->
                factory.createEmployee(
                        "Employee Test",
                        null,
                        40,
                        12
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenNameIsBlank() {
        EmployeeFactory factory = new EmployeeFactory();

        assertThrows(IllegalArgumentException.class, () ->
                factory.createEmployee(
                        " ",
                        Priority.MEDIUM,
                        40,
                        12
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenAgreedHoursIsNegative() {
        EmployeeFactory factory = new EmployeeFactory();

        assertThrows(IllegalArgumentException.class, () ->
                factory.createEmployee(
                        "Employee Test",
                        Priority.MEDIUM,
                        -1,
                        12
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenHourlyCostIsNegative() {
        EmployeeFactory factory = new EmployeeFactory();

        assertThrows(IllegalArgumentException.class, () ->
                factory.createEmployee(
                        "Employee Test",
                        Priority.MEDIUM,
                        40,
                        -1
                )
        );
    }
}