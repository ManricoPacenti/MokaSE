package it.pacenti.moka.scheduling;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the template of a weekly schedule.
 * It defines which shift slots must be covered in a given week.
 * To modify a specific slot in a template, it is necessary
 * to remove it and add a new one.
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
     * Adds a single slot to the template.
     */
    public void addSlot(ShiftSlot slot) {
        slots.add(Objects.requireNonNull(slot, "Shift slot cannot be null"));
    }

    /**
     * Adds multiple slots to the template.
     */
    public void addSlots(Collection<ShiftSlot> slots) {
        Objects.requireNonNull(slots, "Slots collection cannot be null");
        for (ShiftSlot slot : slots) {
            addSlot(slot);
        }
    }

    /**
     * Removes a slot from the template.
     *
     * @return true if the slot was removed
     */
    public boolean removeSlot(ShiftSlot slot) {
        return slots.remove(Objects.requireNonNull(slot, "Shift slot cannot be null"));
    }

    /**
     * Removes a slot by index.
     *
     * @param index slot index
     * @return the removed slot
     */
    public ShiftSlot removeSlotAt(int index) {
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException("Invalid slot index: " + index);
        }
        return slots.remove(index);
    }

    /**
     * Checks if the template already contains a slot.
     */
    public boolean containsSlot(ShiftSlot slot) {
        return slots.contains(Objects.requireNonNull(slot, "Shift slot cannot be null"));
    }

    /**
     * Returns the slots in the template as an unmodifiable list.
     */
    public List<ShiftSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * @return true if the template has no slots
     */
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * Clears the template.
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