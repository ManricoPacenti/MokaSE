package it.pacenti.moka.persistence.json.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmployeeData {

    private String name;
    private String priority;
    private int agreedHours;
    private int hourlyCost;

    private List<EmployeeSkillData> skills = new ArrayList<>();
    private Map<String, List<TimeRangeData>> weeklyAvailability = new LinkedHashMap<>();
    private List<LeaveData> approvedLeaves = new ArrayList<>();

    public EmployeeData() {
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

    public Map<String, List<TimeRangeData>> getWeeklyAvailability() {
        return weeklyAvailability;
    }

    public void setWeeklyAvailability(Map<String, List<TimeRangeData>> weeklyAvailability) {
        this.weeklyAvailability = weeklyAvailability != null ? weeklyAvailability : new LinkedHashMap<>();
    }

    public List<LeaveData> getApprovedLeaves() {
        return approvedLeaves;
    }

    public void setApprovedLeaves(List<LeaveData> approvedLeaves) {
        this.approvedLeaves = approvedLeaves != null ? approvedLeaves : new ArrayList<>();
    }
}