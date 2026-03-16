package it.pacenti.moka.scheduling;

import it.pacenti.moka.employee.Skill;

import java.time.DayOfWeek;
import java.util.Objects;

/**
 * Represents a work slot in the weekly schedule.
 */
public class ShiftSlot {

    private final DayOfWeek day;
    private final TimeRange range;
    private Skill requiredSkill;

    /**
     * Creates a new shift slot:
     */
    public ShiftSlot(DayOfWeek day, TimeRange range, Skill requiredSkill) {
        this.day = Objects.requireNonNull(day, "Day cannot be null");
        this.range = Objects.requireNonNull(range, "Time range cannot be null");
        this.requiredSkill = Objects.requireNonNull(requiredSkill, "Required Skill cannot be null");
    }

    public DayOfWeek getDay() {
        return day;
    }

    public TimeRange getRange() {
        return range;
    }

    public Skill getRequiredSkill() {
        return requiredSkill;
    }

    public void setRequiredSkill(Skill requiredSkill) {
        this.requiredSkill = Objects.requireNonNull(requiredSkill, "Required Skill cannot be null" );
    }

    /**
     * Returns the duration of the slot in minutes
     */
    public long durationMinutes() {
        return range.durationMinutes();
    }

    /**
     * Checks whether this slot overlaps another slot
     */
    public boolean overlaps(ShiftSlot other) {
        Objects.requireNonNull(other, "Other ShiftSlot cannot be null");
        return this.day.equals(other.day) && this.range.overlaps(other.range);
    }

    /**
     * Checks whether this slot occurs at the same day and time
     * as another slot, regardless of required skill
     */
    public boolean sameMomentAs(ShiftSlot other) {
        Objects.requireNonNull(other, "Other ShiftSlot cannot be null");
        return this.day.equals(other.day)
                && this.range.getStart().equals(other.range.getStart())
                && this.range.getEnd().equals(other.range.getEnd());
    }

    @Override
    public String toString() {
        return "ShiftSlot{" +
                "day=" + day +
                ", range=" + range +
                ", requiredSkill=" + requiredSkill +
                "}";
    }
}
