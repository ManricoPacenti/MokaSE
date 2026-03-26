package it.pacenti.moka.persistence.json.model;

/**
 * JSON snapshot of an approved leave.
 */
public class LeaveData {

    private String date;
    private String type;
    private String start;
    private String end;
    private String note;

    public LeaveData() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getNote() {
        return note;
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