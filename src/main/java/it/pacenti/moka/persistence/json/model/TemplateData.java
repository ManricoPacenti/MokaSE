package it.pacenti.moka.persistence.json.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TemplateData {

    private String name;
    private LocalDate weekStart;
    private List<TemplateSlotData> slots = new ArrayList<>();

    public TemplateData() {
    }

    public TemplateData(String name, LocalDate weekStart, List<TemplateSlotData> slots) {
        this.name = name;
        this.weekStart = weekStart;
        this.slots = slots;
    }

    public String getName() {
        return name;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public List<TemplateSlotData> getSlots() {
        return slots;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public void setSlots(List<TemplateSlotData> slots) {
        this.slots = slots;
    }
}