package it.pacenti.moka.availability;

import it.pacenti.moka.employee.Employee;

import java.util.Objects;

/**
 * Represents a leave request submitted by an employee
 */
public class LeaveRequest {

    private final int id;
    private final Employee employee;
    private final Leave leave;
    private RequestStatus status;

;
    public LeaveRequest(int id, Employee employee, Leave leave) {
        this.id = id;
        this.employee = Objects.requireNonNull(employee, "Employee cannot be null")
        this.leave = Objects.requireNonNull(leave, "Leave cannot be null");
        this.status = RequestStatus.PENDING;
    }

    public int getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Leave getLeave() {
        return leave;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void approve() {
        this.status = RequestStatus.APPROVED;
    }

    public void reject() {
        this.status = RequestStatus.REJECTED;
    }

    @Override
    public String toString() {
        return "LeaveRequest{" +
                "id=" + id +
                ", employee=" + employee.getName() +
                ", leave=" + leave +
                ", status=" + status +
                '}';
    }

}
