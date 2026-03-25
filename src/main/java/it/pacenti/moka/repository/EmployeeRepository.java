package it.pacenti.moka.repository;

import it.pacenti.moka.employee.Employee;

import java.util.List;
import java.util.Optional;

/**
 * This interface defines the contract for storing and retrieving employee
 */
public interface EmployeeRepository {

    void save(Employee employee);

    Optional<Employee> findByName(String name);

    boolean existsByName(String name);

    List<Employee> findAll();
}


