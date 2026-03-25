package it.pacenti.moka.repository;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryEmployeeRepositoryTest {

    private final EmployeeFactory factory = new EmployeeFactory();

    @Test
    void shouldSaveAndFindEmployeeByName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        // Act
        repository.save(employee);
        Optional<Employee> result = repository.findByName("Mario");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Mario", result.get().getName());
    }

    @Test
    void shouldFindEmployeeIgnoringCaseAndSpaces() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);
        repository.save(employee);

        // Act
        Optional<Employee> result = repository.findByName("   mArIo   ");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Mario", result.get().getName());
    }

    @Test
    void shouldReturnEmptyWhenEmployeeDoesNotExist() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act
        Optional<Employee> result = repository.findByName("Luigi");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnTrueWhenEmployeeExistsByName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);
        repository.save(employee);

        // Act
        boolean exists = repository.existsByName("mario");

        // Assert
        assertTrue(exists);
    }

    @Test
    void shouldReturnFalseWhenEmployeeDoesNotExistByName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act
        boolean exists = repository.existsByName("mario");

        // Assert
        assertFalse(exists);
    }

    @Test
    void shouldReturnAllSavedEmployees() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee e1 = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);
        Employee e2 = factory.createEmployee("Luigi", Priority.HIGH, 30, 15);

        repository.save(e1);
        repository.save(e2);

        // Act
        List<Employee> employees = repository.findAll();

        // Assert
        assertEquals(2, employees.size());
        assertTrue(employees.contains(e1));
        assertTrue(employees.contains(e2));
    }

    @Test
    void shouldReturnUnmodifiableListFromFindAll() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);
        repository.save(employee);

        // Act
        List<Employee> employees = repository.findAll();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> employees.add(employee));
    }

    @Test
    void shouldOverwriteEmployeeWhenSavingSameNormalizedName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee first = factory.createEmployee("Mario", Priority.MEDIUM, 40, 12);
        Employee second = factory.createEmployee("  mario  ", Priority.HIGH, 20, 18);

        // Act
        repository.save(first);
        repository.save(second);

        Optional<Employee> result = repository.findByName("MARIO");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(Priority.HIGH, result.get().getPriority());
        assertEquals(20, result.get().getAgreedHours());
        assertEquals(18, result.get().getHourlyCost());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldThrowExceptionWhenSavingNullEmployee() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act + Assert
        assertThrows(NullPointerException.class, () -> repository.save(null));
    }

    @Test
    void shouldThrowExceptionWhenFindingByNullName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act + Assert
        assertThrows(NullPointerException.class, () -> repository.findByName(null));
    }

    @Test
    void shouldThrowExceptionWhenCheckingExistenceByNullName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act + Assert
        assertThrows(NullPointerException.class, () -> repository.existsByName(null));
    }

    @Test
    void shouldThrowExceptionWhenFindingByBlankName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> repository.findByName("   "));
    }

    @Test
    void shouldThrowExceptionWhenCheckingExistenceByBlankName() {
        // Arrange
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> repository.existsByName("   "));
    }
}
