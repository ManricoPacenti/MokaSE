package it.pacenti.moka.availability;

import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.scheduling.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class LeaveRequestTest {

    private EmployeeFactory employeeFactory;
    private Employee employee;
    private Leave leave;

    @BeforeEach
    void setUp() {
        employeeFactory = new EmployeeFactory();
        employee = employeeFactory.createEmployee(
                "Mario Rossi",
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
    void shouldThrowWhenIdIsZeroOrNegative() {
        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> new LeaveRequest(0, employee, leave)
        );

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> new LeaveRequest(-1, employee, leave)
        );

        assertEquals("Request id must be greater than zero", ex1.getMessage());
        assertEquals("Request id must be greater than zero", ex2.getMessage());
    }

    @Test
    void shouldThrowWhenEmployeeIsNull() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> new LeaveRequest(1, null, leave)
        );

        assertEquals("Employee cannot be null", ex.getMessage());
    }

    @Test
    void shouldThrowWhenLeaveIsNull() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> new LeaveRequest(1, employee, null)
        );

        assertEquals("Leave cannot be null", ex.getMessage());
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

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                request::approve
        );

        assertEquals("Only pending requests can change status", ex.getMessage());
    }

    @Test
    void shouldNotRejectAlreadyApprovedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.approve();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                request::reject
        );

        assertEquals("Only pending requests can change status", ex.getMessage());
    }

    @Test
    void shouldNotRejectAlreadyRejectedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.reject();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                request::reject
        );

        assertEquals("Only pending requests can change status", ex.getMessage());
    }

    @Test
    void shouldNotApproveAlreadyRejectedRequest() {
        LeaveRequest request = new LeaveRequest(1, employee, leave);
        request.reject();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                request::approve
        );

        assertEquals("Only pending requests can change status", ex.getMessage());
    }
}