package it.pacenti.moka.persistence.json.model;

public class LeaveRequestData {

    private int id;
    private String employeeName;
    private LeaveData leave;
    private String status;

    public LeaveRequestData() {
    }

    public LeaveRequestData(int id, String employeeName, LeaveData leave, String status) {
        this.id = id;
        this.employeeName = employeeName;
        this.leave = leave;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public LeaveData getLeave() {
        return leave;
    }

    public void setLeave(LeaveData leave) {
        this.leave = leave;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}