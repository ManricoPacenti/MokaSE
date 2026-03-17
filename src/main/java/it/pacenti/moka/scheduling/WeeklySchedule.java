package it.pacenti.moka.scheduling;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.exception.SlotAlreadyAssignedException;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.*;

/**
 * Represents the final weekly schedule with all the shift slots
 * and their corresponding employee assignments.
 */
public class WeeklySchedule {

    private final LocalDate weekStart;
    private final List<ShiftSlot> slots;
    private final Map<ShiftSlot, Assignment> assignmentsBySlot;

    /**
     * Creates an empty weekly schedule for the given week
     */
    public WeeklySchedule(LocalDate weekStart) {
        this.weekStart = Objects.requireNonNull(weekStart, "weekStart cannot be null");
        this.slots = new ArrayList<>();
        this.assignmentsBySlot = new HashMap<>();
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void addSlot(ShiftSlot slot) {
        slots.add(Objects.requireNonNull(slot, "ShiftSlot cannot be null"));
    }

    public void addSlots(Collection<ShiftSlot> slots) {
        Objects.requireNonNull(slots, "Slots collection cannot be null");
        for (ShiftSlot slot: slots) {
            addSlot(slot);
        }
    }

    public boolean containsSlot(ShiftSlot slot) {
        return slots.contains(Objects.requireNonNull(slot, "slot cannot be null"));
    }

    public List<ShiftSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * Assigns an employee to a slot.
     * @throws SlotAlreadyAssignedException if the slot is already assigned
     */
    public void assign(ShiftSlot slot, Employee employee){
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        Objects.requireNonNull(employee, "Employee cannot be null");

        if (assignmentsBySlot.containsKey(slot)) {
            throw new SlotAlreadyAssignedException("Slot is altready assigned");
        }

        assignmentsBySlot.put(slot, new Assignment(slot, employee));
    }

    public void unassign(ShiftSlot slot) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        assignmentsBySlot.remove(slot);
    }

    public boolean isAssigned(ShiftSlot slot) {
        return assignmentsBySlot.containsKey(Objects.requireNonNull(slot, "ShiftSlot cannot be null"));
    }

    public Optional<Assignment> getAssignment(ShiftSlot slot) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        return Optional.ofNullable(assignmentsBySlot.get(slot));
    }

    public Collection<Assignment> getAssignments() {
        return Collections.unmodifiableCollection(assignmentsBySlot.values());
    }

    public List<Assignment> getAssignmentsFor(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");

        List<Assignment> result = new ArrayList<>();
        for (Assignment assignment : assignmentsBySlot.values()) {
            if (assignment.getEmployee().equals(employee)) {
                result.add(assignment);
            }
        }
        return result;
    }

    public List<Assignment> getAssignmentsFor(DayOfWeek day) {
        Objects.requireNonNull(day, "Day cannot be null");

        List<Assignment> result = new ArrayList<>();
        for (Assignment assignment : assignmentsBySlot.values()) {
            if (assignment.getSlot().getDay().equals(day)) {
                result.add(assignment);
            }
        }
        return result;
    }

    /**
     * returns the total assigned minutes for the employee.
     */
    public long getAssignedMinutes(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");

        long total = 0;
        for (Assignment assignment : getAssignmentsFor(employee)) {
            total += assignment.getSlot().durationMinutes();
        }
        return total;
    }

    /**
     * returns the total assigned hours for the employee
     */
    public double getAssignedHours(Employee employee) {
        return getAssignedMinutes(employee) / 60.0;
    }

    /**
     * returns remaining assignable hours based on agreed weekly hours
     */
    public double getRemainingHours(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        return employee.getAgreedHours() - getAssignedHours(employee);
    }

    /**
     * returns all slots without an employee assignment
     */
    public List<ShiftSlot> getUnassignedSlots() {
        List<ShiftSlot> result = new ArrayList<> ();
        for (ShiftSlot slot: slots) {
            if (!assignmentsBySlot.containsKey(slot)) {
                result.add(slot);
            }
        }
        return result;
    }

    /**
     * Checks whether the employee already has an overlapping assignment.
     */
    public boolean hasOverlappingAssignment(Employee employee, ShiftSlot slot) {
        Objects.requireNonNull(employee, "Employee cannot be null");
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");

        for (Assignment assignment : getAssignmentsFor(employee)) {
            if (assignment.getSlot().overlaps(slot)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "WeeklySchedule{" +
                "weekStart=" + weekStart +
                ", slots=" + slots +
                ", assignmentsBySlot=" + assignmentsBySlot +
                '}';
    }
}
