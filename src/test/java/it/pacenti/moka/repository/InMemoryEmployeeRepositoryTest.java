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
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);

        repository.save(employee);
        Optional<Employee> result = repository.findByName("Barista Test");

        assertTrue(result.isPresent());
        assertEquals("Barista Test", result.get().getName());
    }

    @Test
    void shouldFindEmployeeIgnoringCaseAndSpaces() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);
        repository.save(employee);

        Optional<Employee> result = repository.findByName("   bArIsTa TeSt   ");

        assertTrue(result.isPresent());
        assertEquals("Barista Test", result.get().getName());
    }

    @Test
    void shouldReturnEmptyWhenEmployeeDoesNotExist() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        Optional<Employee> result = repository.findByName("Missing Employee Test");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnTrueWhenEmployeeExistsByName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);
        repository.save(employee);

        boolean exists = repository.existsByName("barista test");

        assertTrue(exists);
    }

    @Test
    void shouldReturnFalseWhenEmployeeDoesNotExistByName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        boolean exists = repository.existsByName("missing employee test");

        assertFalse(exists);
    }

    @Test
    void shouldReturnAllSavedEmployees() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee firstEmployee = factory.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);
        Employee secondEmployee = factory.createEmployee("Responsabile Test", Priority.HIGH, 30, 15);

        repository.save(firstEmployee);
        repository.save(secondEmployee);

        List<Employee> employees = repository.findAll();

        assertEquals(2, employees.size());
        assertTrue(employees.contains(firstEmployee));
        assertTrue(employees.contains(secondEmployee));
    }

    @Test
    void shouldReturnUnmodifiableListFromFindAll() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee employee = factory.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);
        repository.save(employee);

        List<Employee> employees = repository.findAll();

        assertThrows(UnsupportedOperationException.class, () -> employees.add(employee));
    }

    @Test
    void shouldOverwriteEmployeeWhenSavingSameNormalizedName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Employee firstEmployee = factory.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);
        Employee secondEmployee = factory.createEmployee("  barista test  ", Priority.HIGH, 20, 18);

        repository.save(firstEmployee);
        repository.save(secondEmployee);

        Optional<Employee> result = repository.findByName("BARISTA TEST");

        assertTrue(result.isPresent());
        assertEquals(Priority.HIGH, result.get().getPriority());
        assertEquals(20, result.get().getAgreedHours());
        assertEquals(18, result.get().getHourlyCost());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldThrowExceptionWhenSavingNullEmployee() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        assertThrows(NullPointerException.class, () -> repository.save(null));
    }

    @Test
    void shouldThrowExceptionWhenFindingByNullName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        assertThrows(NullPointerException.class, () -> repository.findByName(null));
    }

    @Test
    void shouldThrowExceptionWhenCheckingExistenceByNullName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        assertThrows(NullPointerException.class, () -> repository.existsByName(null));
    }

    @Test
    void shouldThrowExceptionWhenFindingByBlankName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        assertThrows(IllegalArgumentException.class, () -> repository.findByName("   "));
    }

    @Test
    void shouldThrowExceptionWhenCheckingExistenceByBlankName() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();

        assertThrows(IllegalArgumentException.class, () -> repository.existsByName("   "));
    }
}