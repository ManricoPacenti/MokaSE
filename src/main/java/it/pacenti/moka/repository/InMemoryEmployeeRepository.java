package it.pacenti.moka.repository;

import  it.pacenti.moka.employee.Employee;

import java.util.*;

/**
 * This repository stores employees in a map using a normalized
 * version of the employee name as key
  */
public class InMemoryEmployeeRepository implements EmployeeRepository {

    private final Map<String, Employee> employeesByName;

    public InMemoryEmployeeRepository() {
        this.employeesByName = new HashMap<>();
    }

    @Override
    public void save(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");

        String normalizedName = normalizeName(employee.getName());
        employeesByName.put(normalizedName, employee);
    }

    @Override
    public Optional<Employee> findByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalizedName = normalizeName(name);
        return Optional.ofNullable(employeesByName.get(normalizedName));
    }

    @Override
    public boolean existsByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalizedName = normalizeName(name);
        return employeesByName.containsKey(normalizedName);
    }

    @Override
    public List<Employee> findAll() {
        return List.copyOf(employeesByName.values());
    }

    /**
     * Normalization currently trims leading/trailing spaces
     * and converts to lowercase
     */
    private String normalizeName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalized = name.trim().toLowerCase();

        if(normalized.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        return normalized;
    }
}
