package it.pacenti.moka.employee;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveCalendar;
import it.pacenti.moka.availability.WeeklyAvailability;
import it.pacenti.moka.scheduling.ShiftSlot;

import java.util.Objects;

/**
 * Represents an employee
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
        this.name = Objects.requireNonNull(name);
        this.skills = Objects.requireNonNull(skills);
        this.availability = Objects.requireNonNull(availability);
        this.leaveCalendar = Objects.requireNonNull(leaveCalendar);
        this.priority = Objects.requireNonNull(priority);

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

    public EmployeeSkills getSkills() {
        return skills;
    }

    public boolean hasSkill(Skill skill){
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
        this.hourlyCost = hourlyCost;
    }

    /**
     * Checks if the employee can potentially be assigned
     * to a given shift slot
     */
    public boolean isAssignableTo(ShiftSlot slot) {

        if (!hasSkill(slot.getRequiredSkill())) {
            return false;
        }

        if (!availability.isAvailable(slot)) {
            return false;
        }

        if (leaveCalendar.isOnLeave(slot)) {
            return false;
        }

        return true;
    }

    public int getAgreedHours(){
        return agreedHours;
    }
}
