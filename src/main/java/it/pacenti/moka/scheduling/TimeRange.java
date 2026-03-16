package it.pacenti.moka.scheduling;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Represents a time interval within a single day.
 * Supports ranges that cross midnight (ex: 17:00 -> 01:00)
 *
 * Internally, the range is treated on an extended timeline in minutes.
 */
public class TimeRange {

    private static final int MINUTES_PER_DAY = 24*60;

    private final LocalTime start;
    private final LocalTime end;

    /**
     * Creates a new time range
     */
    public TimeRange(LocalTime start, LocalTime end) {
        this.start = Objects.requireNonNull(start, "Start time cannot be null");
        this.end = Objects.requireNonNull(end, "End time cannot be null");

        if (start.equals(end)) {
            throw new IllegalArgumentException("Start and end cannot be the same");
        }
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    /**
     * Checks if the time range crosses midnight
     */
    public boolean crossesMidnight() {
        return end.isBefore(start);
    }

    /**
     * Returns the duration of the range in minutes
     */
    public long durationMinutes() {
        return normalizedEndMinutes() - startMinutes();
        }

    /**
     * Checks whether the given time belongs to this range.
     */
    public boolean contains(LocalTime time) {
        Objects.requireNonNull(time, "Time cannot be null");

        int timeMinutes = time.toSecondOfDay() / 60;
        int startMinutes = startMinutes();
        int endMinutes = normalizedEndMinutes();

        if (!crossesMidnight()) {
            return timeMinutes >= startMinutes && timeMinutes < endMinutes;
        }

        return timeMinutes >= startMinutes || timeMinutes < end.toSecondOfDay() / 60;
    }

    /**
     * Checks overlaps with another range, let the scheduler to didn't assign
     * the same employee to two time range overlapped
     * @return true if the two ranges overlap
     */
    public boolean overlaps(TimeRange other) {
        Objects.requireNonNull(other, "Other TimeRange cannot be null");

        int thisStart = this.startMinutes();
        int thisEnd = this.normalizedEndMinutes();

        int otherStart = other.startMinutes();
        int otherEnd = other.normalizedEndMinutes();

        if (intervalsOverlap(thisStart, thisEnd, otherStart, otherEnd)) {
            return true;
        }

        return intervalsOverlap(thisStart,  thisEnd, otherStart+MINUTES_PER_DAY, otherEnd+MINUTES_PER_DAY)
            || intervalsOverlap(thisStart + MINUTES_PER_DAY, thisEnd+MINUTES_PER_DAY, otherStart, otherEnd);
    }

    private int startMinutes() {
        return start.toSecondOfDay()/60;
    }

    private int normalizedEndMinutes() {
        int endMinutes = end.toSecondOfDay() / 60;
        return crossesMidnight() ? endMinutes + MINUTES_PER_DAY : endMinutes;
    }

    private boolean intervalsOverlap(int start1, int end1, int start2, int end2) {
        return start1 < end2 && start2 < end1;
    }

    @Override
    public String toString() {
        return "TimeRange{" +
                "start=" + start +
                ", end=" + end +
                "}";
    }
}
