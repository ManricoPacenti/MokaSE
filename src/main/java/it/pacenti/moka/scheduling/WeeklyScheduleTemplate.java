package it.pacenti.moka.scheduling;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents the template of a weekly schedule.
 * it defines which shift slots must be covered in a given week.
 * To modify a specific slot in a template its necessary to cancel it and rewrite a new slot
 */
public class WeeklyScheduleTemplate {

    private final LocalDate weekStart;
    private final List<ShiftSlot> slots;

    public WeeklyScheduleTemplate(LocalDate weekStart) {
        this.weekStart = Objects.requireNonNull(weekStart, "Week start cannot be null");
        this.slots = new ArrayList<>();
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    /**
     * Adds a single slot to the template
     */
    public void addSlot(ShiftSlot slot) {
        slots.add(Objects.requireNonNull(slot, "ShiftSlot cannot be null"));
    }

    /**
     * Adds multiple slots to the template.
     */
    public  void addSlots(Collection<ShiftSlot> slots) {
        Objects.requireNonNull(slots, "Slots collection cannot be null");
        for (ShiftSlot slot : slots) {
            addSlot(slot);
        }
    }

    /**
     * Remove a slot form the template
     */
    public boolean removeSlot(ShiftSlot slot) {
        return slots.remove(Objects.requireNonNull(slot, "ShiftSlot cannot be null"));
    }

    /**
     * Checks if the template already contains a slot
     */
    public boolean continsSlot(ShiftSlot slot) {
        return slots.contains(Objects.requireNonNull(slot, "ShiftSlot cannot be null"));
    }

    /**
     * @return the slots in the template (unmodifiable)
     */
    public List<ShiftSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * Clean the templates
     */
    public void clear() {
        slots.clear();
    }

    @Override
    public String toString() {
        return "WeeklyScheduleTemplate{" +
                "weekStart=" + weekStart +
                ", slots=" + slots +
                '}';
    }
}
