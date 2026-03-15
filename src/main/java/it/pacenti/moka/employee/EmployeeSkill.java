package it.pacenti.moka.employee;

import java.util.Objects;

/**
 * Represents a specific skill possessed by an employee
 * together with the level of proficiency.
 */
public class EmployeeSkill {

    private final Skill skill;
    private Proficiency proficiency;

    /**
     * Creates a new EmployeeSkill
     *
     * @param skill the skill type
     * @param proficiency the proficiency level
     */
    public EmployeeSkill(Skill skill, Proficiency proficiency) {
        this.skill = Objects.requireNonNull(skill, "Skill cannot be null");
        this.proficiency = Objects.requireNonNull(proficiency, "Proficiency cannot be null");
    }

    public Skill getSkill() {
        return skill;
    }

    public Proficiency getProficiency() {
        return proficiency;
    }

    /**
     * Updates the proficiency level
     */
    public void setProficiency(Proficiency proficiency) {
        this.proficiency = Objects.requireNonNull(proficiency, "Proficiency cannot be null");
    }

    /**
     * Compares the proficiency level of this skill
     * with another EmployeeSkill.
     *
     * @return true if this skill has a higher proficiency
     * than the other.
     */
    public boolean isHigherThan(EmployeeSkill other) {
        return this.proficiency.ordinal() > other.proficiency.ordinal();
    }

}
