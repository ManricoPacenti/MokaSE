package it.pacenti.moka.employee;

import it.pacenti.moka.availability.LeaveCalendar;
import it.pacenti.moka.availability.WeeklyAvailability;

import java.util.Objects;

/**
 * Factory responsible for creating Employee instances
 * in a consistent initial state
 */
public final class EmployeeFactory {

    public Employee createEmployee(
            String name,
            Priority priority,
            int agreedHours,
            int hourlyCost
    ) {
        validate(name, priority, agreedHours, hourlyCost);

        EmployeeSkills skills = new EmployeeSkills();
        WeeklyAvailability availability = new WeeklyAvailability();
        LeaveCalendar leaveCalendar = new LeaveCalendar();

        return new Employee(
                name.trim(),
                skills,
                availability,
                leaveCalendar,
                priority,
                agreedHours,
                hourlyCost
        );
    }

    private void validate(
            String name,
            Priority priority,
            int agreedHours,
            int hourlyCost
    ) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(priority, "Priority cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        if (agreedHours < 0) {
            throw new IllegalArgumentException("Agreed hours cannot be less than zero");
        }

        if (hourlyCost < 0) {
            throw new IllegalArgumentException("Hourly cost cannot be less than zero");
        }
    }
}
