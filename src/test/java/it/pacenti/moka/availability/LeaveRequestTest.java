package it.pacenti.moka.availability;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.scheduling.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class LeaveRequestTest {

    private Employee employee;
    private Leave leave;

    @BeforeEach
    void setUp() {
        EmployeeFactory employeeFactory = new EmployeeFactory();

        employee = employeeFactory.createEmployee(
                "Employee On Leave Test",
                Priority.MEDIUM,
                40,
                12
        );

        leave = new Leave(
                LocalDate.of(2026, 3, 25),
                new TimeRange(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                LeaveType.VACATION,
                "Personal leave"
        );
    }

    @Test
    void shouldCreatePendingLeaveRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);

        assertAll(
                () -> assertEquals(1, request.getId()),
                () -> assertEquals(employee, request.getEmployee()),
                () -> assertEquals(leave, request.getLeave()),
                () -> assertEquals(RequestStatus.PENDING, request.getStatus())
        );
    }

    @Test
    void shouldThrowExceptionWhenIdIsZeroOrNegative() {
        IllegalArgumentException zeroIdException = assertThrows(
                IllegalArgumentException.class,
                () -> new LeaveRequest(0, employee, leave)
        );

        IllegalArgumentException negativeIdException = assertThrows(
                IllegalArgumentException.class,
                () -> new LeaveRequest(-1, employee, leave)
        );

        assertEquals("Request id must be greater than zero", zeroIdException.getMessage());
        assertEquals("Request id must be greater than zero", negativeIdException.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenEmployeeIsNull() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new LeaveRequest(1, null, leave)
        );

        assertEquals("Employee cannot be null", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenLeaveIsNull() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new LeaveRequest(1, employee, null)
        );

        assertEquals("Leave cannot be null", exception.getMessage());
    }

    @Test
    void shouldApprovePendingRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);

        request.approve();

        assertEquals(RequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void shouldRejectPendingRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);

        request.reject();

        assertEquals(RequestStatus.REJECTED, request.getStatus());
    }

    @Test
    void shouldNotApproveAlreadyApprovedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.approve();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                request::approve
        );

        assertEquals("Only pending requests can change status", exception.getMessage());
    }

    @Test
    void shouldNotRejectAlreadyApprovedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.approve();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                request::reject
        );

        assertEquals("Only pending requests can change status", exception.getMessage());
    }

    @Test
    void shouldNotRejectAlreadyRejectedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.reject();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                request::reject
        );

        assertEquals("Only pending requests can change status", exception.getMessage());
    }

    @Test
    void shouldNotApproveAlreadyRejectedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.reject();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                request::approve
        );

        assertEquals("Only pending requests can change status", exception.getMessage());
    }
}