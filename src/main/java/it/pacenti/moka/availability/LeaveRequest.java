package it.pacenti.moka.availability;

import it.pacenti.moka.employee.Employee;

import java.util.Objects;

/**
 * Represents a leave request submitted by an employee.
 */
public class LeaveRequest {

    private final int id;
    private final Employee employee;
    private final Leave leave;
    private RequestStatus status;

    public LeaveRequest(int id, Employee employee, Leave leave) {
        if (id <= 0) {
            throw new IllegalArgumentException("Request id must be greater than zero");
        }

        this.id = id;
        this.employee = Objects.requireNonNull(employee, "Employee cannot be null");
        this.leave = Objects.requireNonNull(leave, "Leave cannot be null");
        this.status = RequestStatus.PENDING;
    }

    public static LeaveRequest reconstitute(int id,
                                            Employee employee,
                                            Leave leave,
                                            RequestStatus status) {
        LeaveRequest request = new LeaveRequest(id, employee, leave);
        request.status = Objects.requireNonNull(status, "Status cannot be null");
        return request;
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
        ensurePending();
        this.status = RequestStatus.APPROVED;
    }

    public void reject() {
        ensurePending();
        this.status = RequestStatus.REJECTED;
    }

    private void ensurePending() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can change status");
        }
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