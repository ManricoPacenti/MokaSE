package it.pacenti.moka.availability;

import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stores all approved leaves for an employee
 */
public class LeaveCalendar {

    private final List<Leave> leaves;

    public LeaveCalendar() {
        this.leaves = new ArrayList<>();
    }

    public void addLeave(Leave leave) {
        leaves.add(Objects.requireNonNull(leave, "Leave cannot be null"));
    }

    public boolean isOnLeave(LocalDate date, TimeRange range) {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(range, "TimeRange cannot be null");

        for (Leave leave : leaves) {
            if(leave.overlaps(date, range)) {
                return true;
            }
        }
        return false;
    }

    //Follow an helper for the current scheduling model.
    public boolean isOnLeave(ShiftSlot slot) {
        Objects.requireNonNull(slot, "ShiftSlot cannot be null");
        return false;
    }

    public List<Leave> getLeaves() {
        return Collections.unmodifiableList(leaves);
    }

    @Override
    public String toString() {
        return "LeaveCalendar{" +
                "leaves=" + leaves +
                "}";
    }
}
