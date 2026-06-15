package it.pacenti.moka.scheduling;

import it.pacenti.moka.employee.Employee;

import java.util.Objects;

/**
 * Represent an assigned employee to a specific shift slot
 */
public class Assignment {

    private final ShiftSlot slot;
    private final Employee employee;

    /**
     * Creates a new assignment
     */
    public Assignment(ShiftSlot slot, Employee employee) {
        this.slot = Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        this.employee = Objects.requireNonNull(employee, "Employee cannot be null");
    }

    public ShiftSlot getSlot() {
        return slot;
    }

    public Employee getEmployee() {
        return employee;
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "slot=" + slot +
                ", employee=" + employee +
                "}";
    }
}
