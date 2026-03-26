package it.pacenti.moka.persistence.json.model;

/**
 * JSON snapshot of a time range.
 */
public class TimeRangeData {

    private String start;
    private String end;

    public TimeRangeData() {
    }

    public TimeRangeData(String start, String end) {
        this.start = start;
        this.end = end;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }
}