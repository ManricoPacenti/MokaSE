package it.pacenti.moka.persistence.json.model;

import it.pacenti.moka.employee.Skill;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class TemplateSlotData {

    private DayOfWeek day;
    private LocalTime start;
    private LocalTime end;
    private Skill requiredSkill;

    public TemplateSlotData() {
    }

    public TemplateSlotData(DayOfWeek day, LocalTime start, LocalTime end, Skill requiredSkill) {
        this.day = day;
        this.start = start;
        this.end = end;
        this.requiredSkill = requiredSkill;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public Skill getRequiredSkill() {
        return requiredSkill;
    }

    public void setDay(DayOfWeek day) {
        this.day = day;
    }

    public void setStart(LocalTime start) {
        this.start = start;
    }

    public void setEnd(LocalTime end) {
        this.end = end;
    }

    public void setRequiredSkill(Skill requiredSkill) {
        this.requiredSkill = requiredSkill;
    }
}