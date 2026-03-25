package it.pacenti.moka.availability;

import it.pacenti.moka.scheduling.TimeRange;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents an approved leave for a specific date and time.
 */
public class Leave {

    private final LocalDate date;
    private final TimeRange range;
    private final LeaveType type;
    private final String note;

    public Leave(LocalDate date, TimeRange range, LeaveType type, String note) {
        this.date = Objects.requireNonNull(date, "Date cannot be null");
        this.range = Objects.requireNonNull(range, "Time range cannot be null");
        this.type = Objects.requireNonNull(type, "Leave type cannot be null");
        this.note = note;
    }

    public LocalDate getDate() {
        return date;
    }

    public TimeRange getRange() {
        return range;
    }

    public LeaveType getType() {
        return type;
    }

    public String getNote() {
        return note;
    }

    public boolean overlaps(LocalDate otherDate, TimeRange otherRange) {
        Objects.requireNonNull(otherDate, "Date cannot be null");
        Objects.requireNonNull(otherRange, "Time range cannot be null");

        return this.date.equals(otherDate) && this.range.overlaps(otherRange);
    }

    @Override
    public String toString() {
        return "Leave{" +
                "date=" + date +
                ", range=" + range +
                ", type=" + type +
                ", note='" + note + '\'' +
                '}';
    }
}
