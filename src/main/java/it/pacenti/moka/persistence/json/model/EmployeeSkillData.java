package it.pacenti.moka.persistence.json.model;

/**
 * JSON snapshot of an employee skill.
 */
public class EmployeeSkillData {

    private String skill;
    private String proficiency;

    public EmployeeSkillData() {
    }

    public String getSkill() {
        return skill;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }

    public String getProficiency() {
        return proficiency;
    }

    public void setProficiency(String proficiency) {
        this.proficiency = proficiency;
    }
}