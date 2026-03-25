package it.pacenti.moka.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeFactoryTest {

    @Test
    void shouldCreateEmployeeWhenInputIsValid() {
        //Arrange
        EmployeeFactory factory = new EmployeeFactory();

        //Act
        Employee employee = factory.createEmployee(
                "Mario",
                Priority.MEDIUM,
                40,
                12
        );

        //Assert
        assertNotNull(employee);
        assertEquals("Mario", employee.getName());
        assertEquals(Priority.MEDIUM, employee.getPriority());
        assertEquals(40, employee.getAgreedHours());
        assertEquals(12, employee.getHourlyCost());

        assertNotNull(employee.getSkills());
        assertNotNull(employee.getAvailability());
        assertNotNull(employee.getLeaveCalendar());
    }

    @Test
    void shouldTrimNameWhenCreatingEmployee() {
        //Arrange
        EmployeeFactory factory = new EmployeeFactory();

        //Act
        Employee employee = factory.createEmployee(
                "  Mario  ",
                Priority.MEDIUM,
                40,
                12
        );

        //Assert
        assertEquals("Mario", employee.getName());
    }

    @Test
    void shouldThreowExceptionWhenNameIsNull() {
        //Arrange
        EmployeeFactory factory = new EmployeeFactory();

        //Act+ Assert
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
    void shouldThrowExceprionWhenPriorityIsNull() {
        //Arrange
        EmployeeFactory factory = new EmployeeFactory();

        //Act+Assert
        assertThrows(NullPointerException.class, () ->
                factory.createEmployee(
                        "Mario",
                        null,
                        40,
                        12
                )
        );
    }

    @Test
    void shouldThrowExceprionWhenNameIsBlank() {
        //Arrange
        EmployeeFactory factory = new EmployeeFactory();

        //Act+ Assert
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
        // Arrange
        EmployeeFactory factory = new EmployeeFactory();

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () ->
                factory.createEmployee(
                        "Mario",
                        Priority.MEDIUM,
                        -1,
                        12
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenHourlyCostIsNegative() {
        // Arrange
        EmployeeFactory factory = new EmployeeFactory();

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () ->
                factory.createEmployee(
                        "Mario",
                        Priority.MEDIUM,
                        40,
                        -1
                )
        );
    }
}

