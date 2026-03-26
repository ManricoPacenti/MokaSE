package it.pacenti.moka.persistence.json.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON snapshot of an employee.
 */
public class EmployeeData {

    private String name;
    private String priority;
    private int agreedHours;
    private int hourlyCost;

    private List<EmployeeSkillData> skills;
    private Map<String, List<TimeRangeData>> weeklyTimeOff;
    private List<LeaveData> approvedLeaves;

    public EmployeeData() {
        this.skills = new ArrayList<>();
        this.weeklyTimeOff = new LinkedHashMap<>();
        this.approvedLeaves = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public int getAgreedHours() {
        return agreedHours;
    }

    public void setAgreedHours(int agreedHours) {
        this.agreedHours = agreedHours;
    }

    public int getHourlyCost() {
        return hourlyCost;
    }

    public void setHourlyCost(int hourlyCost) {
        this.hourlyCost = hourlyCost;
    }

    public List<EmployeeSkillData> getSkills() {
        return skills;
    }

    public void setSkills(List<EmployeeSkillData> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public Map<String, List<TimeRangeData>> getWeeklyTimeOff() {
        return weeklyTimeOff;
    }

    public void setWeeklyTimeOff(Map<String, List<TimeRangeData>> weeklyTimeOff) {
        this.weeklyTimeOff = weeklyTimeOff != null ? weeklyTimeOff : new LinkedHashMap<>();
    }

    public List<LeaveData> getApprovedLeaves() {
        return approvedLeaves;
    }

    public void setApprovedLeaves(List<LeaveData> approvedLeaves) {
        this.approvedLeaves = approvedLeaves != null ? approvedLeaves : new ArrayList<>();
    }
}