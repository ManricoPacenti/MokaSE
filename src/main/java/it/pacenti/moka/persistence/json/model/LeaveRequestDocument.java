package it.pacenti.moka.persistence.json.model;

import java.util.ArrayList;
import java.util.List;

public class LeaveRequestDocument {

    private List<LeaveRequestData> requests = new ArrayList<>();

    public LeaveRequestDocument() {
    }

    public List<LeaveRequestData> getRequests() {
        return requests;
    }

    public void setRequests(List<LeaveRequestData> requests) {
        this.requests = (requests != null) ? requests : new ArrayList<>();
    }
}