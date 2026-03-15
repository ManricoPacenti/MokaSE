package it.pacenti.moka.employee;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the full set of skills owned by an employee.
 */
public class EmployeeSkills {

    private final Map<Skill, EmployeeSkill> skills;

    /**
     * Creates an empty set of employee skills
     */
    public EmployeeSkills() {
        this.skills = new EnumMap<>(Skill.class);
    }

    /**checks if the employee has the given skill.
     *
     * @return true if the skill is present
     */
    public boolean hasSkill(Skill skill) {
        Objects.requireNonNull(skill, "Skill cannot be null");
        return skills.containsKey(skill);
    }

    /**
     * check the proficiency of a given skill
     *
     * @return the proficiency if the skill exist, null otherwise
     */
    public Proficiency getProficiency(Skill skill) {
        Objects.requireNonNull(skill, "Skill cannot be null");
        EmployeeSkill employeeSkill = skills.get(skill);
        return employeeSkill != null ? employeeSkill.getProficiency() : null;
    }

    /**
     * Adds a new skill or updates its proficiency
     *
     * @param skill the skill type
     * @param proficiency the proficiency level
     */
    public void addOrUpdate(Skill skill, Proficiency proficiency) {
        Objects.requireNonNull(skill, "Skill cannot be null");
        Objects.requireNonNull(proficiency, "Proficiency cannot be null");

        skills.put(skill, new EmployeeSkill(skill, proficiency));
    }

    /**
     * removes a skill from the employee
     */
    public void remove(Skill skill) {
        Objects.requireNonNull(skill, "skill cannot be null");
        skills.remove(skill);
    }

    /**
     * Returns all employee skills as an unmodifiable collection
     */
    public Collection<EmployeeSkill> asCollection() {
        return Collections.unmodifiableCollection(skills.values());
    }
}
