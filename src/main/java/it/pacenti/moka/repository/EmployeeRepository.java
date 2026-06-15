package it.pacenti.moka.repository;

import it.pacenti.moka.employee.Employee;

import java.util.List;
import java.util.Optional;

/**
 * Contract for storing and retrieving employees.
 */
public interface EmployeeRepository {

    void save(Employee employee);

    Optional<Employee> findByName(String name);

    boolean existsByName(String name);

    List<Employee> findAll();

    void deleteByName(String name);
}