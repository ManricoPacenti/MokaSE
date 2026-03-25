package it.pacenti.moka.employee;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveCalendar;
import it.pacenti.moka.availability.WeeklyAvailability;
import it.pacenti.moka.scheduling.ShiftSlot;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents an employee.
 * The entity protects its own invariants after creation.
 */
public class Employee {

    private final String name;
    private final EmployeeSkills skills;
    private final WeeklyAvailability availability;
    private final LeaveCalendar leaveCalendar;
    private final Priority priority;

    private final int agreedHours;
    private int hourlyCost;

    public Employee(
            String name,
            EmployeeSkills skills,
            WeeklyAvailability availability,
            LeaveCalendar leaveCalendar,
            Priority priority,
            int agreedHours,
            int hourlyCost
    ) {
        Objects.requireNonNull(name, "Name cannot be null");
        this.name = name.trim();

        if (this.name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        this.skills = Objects.requireNonNull(skills, "Skills cannot be null");
        this.availability = Objects.requireNonNull(availability, "Availability cannot be null");
        this.leaveCalendar = Objects.requireNonNull(leaveCalendar, "Leave calendar cannot be null");
        this.priority = Objects.requireNonNull(priority, "Priority cannot be null");

        if (agreedHours < 0) {
            throw new IllegalArgumentException("Agreed hours cannot be negative");
        }

        if (hourlyCost < 0) {
            throw new IllegalArgumentException("Hourly cost cannot be negative");
        }

        this.agreedHours = agreedHours;
        this.hourlyCost = hourlyCost;
    }

    public String getName() {
        return name;
    }

    public Priority getPriority() {
        return priority;
    }

    public EmployeeSkills getSkills() {
        return skills;
    }

    public Proficiency getProficiency(Skill skill) {
        return skills.getProficiency(skill);
    }

    public boolean hasSkill(Skill skill) {
        return skills.hasSkill(skill);
    }

    public WeeklyAvailability getAvailability() {
        return availability;
    }

    public LeaveCalendar getLeaveCalendar() {
        return leaveCalendar;
    }

    public int getHourlyCost() {
        return hourlyCost;
    }

    public void setHourlyCost(int hourlyCost) {
        if (hourlyCost < 0) {
            throw new IllegalArgumentException("Hourly cost cannot be less than zero");
        }
        this.hourlyCost = hourlyCost;
    }

    public int getAgreedHours() {
        return agreedHours;
    }

    public void addLeave(Leave leave) {
        leaveCalendar.addLeave(Objects.requireNonNull(leave, "Leave cannot be null"));
    }

    /**
     * Checks if the employee can potentially be assigned
     * to a given shift slot on a specific date.
     */
    public boolean isAssignableTo(ShiftSlot slot, LocalDate slotDate) {
        Objects.requireNonNull(slot, "Shift slot cannot be null");
        Objects.requireNonNull(slotDate, "Slot date cannot be null");

        if (!hasSkill(slot.getRequiredSkill())) {
            return false;
        }

        if (!availability.isAvailable(slot.getDay(), slot.getRange())) {
            return false;
        }

        if (leaveCalendar.isOnLeave(slotDate, slot.getRange())) {
            return false;
        }

        return true;
    }
}