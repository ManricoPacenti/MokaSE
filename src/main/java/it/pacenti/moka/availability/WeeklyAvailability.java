package it.pacenti.moka.availability;

import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Represents recurring weekly unavailability for an employee.
 *
 * Unavailability can be expressed in two ways:
 * - full day off
 * - partial time ranges inside a day
 */
public class WeeklyAvailability {

    private final Map<DayOfWeek, List<TimeRange>> timeOff;
    private final Set<DayOfWeek> fullDaysOff;

    public WeeklyAvailability() {
        this.timeOff = new EnumMap<>(DayOfWeek.class);
        this.fullDaysOff = EnumSet.noneOf(DayOfWeek.class);
    }

    public void addTimeOff(DayOfWeek day, TimeRange range) {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "TimeRange cannot be null");

        timeOff.computeIfAbsent(day, d -> new ArrayList<>()).add(range);
    }

    public void removeTimeOff(DayOfWeek day, TimeRange range) {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "TimeRange cannot be null");

        List<TimeRange> ranges = timeOff.get(day);
        if (ranges != null) {
            ranges.remove(range);
            if (ranges.isEmpty()) {
                timeOff.remove(day);
            }
        }
    }

    public void addFullDayOff(DayOfWeek day) {
        Objects.requireNonNull(day, "Day cannot be null");
        fullDaysOff.add(day);
    }

    public void removeFullDayOff(DayOfWeek day) {
        Objects.requireNonNull(day, "Day cannot be null");
        fullDaysOff.remove(day);
    }

    public boolean isFullDayOff(DayOfWeek day) {
        Objects.requireNonNull(day, "Day cannot be null");
        return fullDaysOff.contains(day);
    }

    public boolean isAvailable(ShiftSlot slot) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        return isAvailable(slot.getDay(), slot.getRange());
    }

    public boolean isAvailable(DayOfWeek day, TimeRange range) {
        Objects.requireNonNull(day, "Day cannot be null");
        Objects.requireNonNull(range, "TimeRange cannot be null");

        if (fullDaysOff.contains(day)) {
            return false;
        }

        List<TimeRange> ranges = timeOff.getOrDefault(day, Collections.emptyList());

        for (TimeRange blocked : ranges) {
            if (blocked.overlaps(range)) {
                return false;
            }
        }

        return true;
    }

    public Map<DayOfWeek, List<TimeRange>> getTimeOff() {
        return Collections.unmodifiableMap(timeOff);
    }

    public Set<DayOfWeek> getFullDaysOff() {
        return Collections.unmodifiableSet(fullDaysOff);
    }

    @Override
    public String toString() {
        return "WeeklyAvailability{" +
                "timeOff=" + timeOff +
                ", fullDaysOff=" + fullDaysOff +
                '}';
    }
}