package it.pacenti.moka.service;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.InMemoryEmployeeRepository;
import it.pacenti.moka.scheduling.ShiftScheduler;
import it.pacenti.moka.scheduling.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagerServiceTest {

    private EmployeeRepository employeeRepository;
    private EmployeeFactory employeeFactory;
    private ShiftScheduler scheduler;
    private ManagerService managerService;

    @BeforeEach
    void setUp() {
        employeeRepository = new InMemoryEmployeeRepository();
        employeeFactory = new EmployeeFactory();
        scheduler = new ShiftScheduler();

        managerService = new ManagerService(
                employeeRepository,
                employeeFactory,
                scheduler
        );
    }

    @Test
    void shouldSubmitPendingLeaveRequest() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave = new Leave(
                LocalDate.of(2026, 4, 10),
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(14, 0)),
                LeaveType.VACATION,
                "Weekend trip"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, leave);

        assertNotNull(request);
        assertEquals(employee, request.getEmployee());
        assertEquals(leave, request.getLeave());
        assertEquals(RequestStatus.PENDING, request.getStatus());

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();
        assertEquals(1, pendingRequests.size());
        assertTrue(pendingRequests.contains(request));
    }

    @Test
    void shouldThrowWhenSubmittingLeaveRequestForUnmanagedEmployee() {
        Employee unmanagedEmployee = employeeFactory.createEmployee("Luigi", Priority.LOW, 30, 10);

        Leave leave = new Leave(
                LocalDate.of(2026, 4, 15),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)),
                LeaveType.PERSONAL,
                "Personal appointment"
        );

        assertThrows(IllegalArgumentException.class,
                () -> managerService.submitLeaveRequest(unmanagedEmployee, leave));
    }

    @Test
    void shouldApprovePendingLeaveRequest() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave = new Leave(
                LocalDate.of(2026, 5, 1),
                new TimeRange(LocalTime.of(12, 0), LocalTime.of(18, 0)),
                LeaveType.VACATION,
                "Day off"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, leave);

        managerService.approveLeaveRequest(request.getId());

        assertEquals(RequestStatus.APPROVED, request.getStatus());
        assertTrue(employee.getLeaveCalendar().getLeaves().contains(leave));
        assertTrue(managerService.getPendingRequests().isEmpty());
    }

    @Test
    void shouldRejectPendingLeaveRequest() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave = new Leave(
                LocalDate.of(2026, 6, 10),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                LeaveType.SICK,
                "Flu"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, leave);

        managerService.rejectLeaveRequest(request.getId());

        assertEquals(RequestStatus.REJECTED, request.getStatus());
        assertFalse(employee.getLeaveCalendar().getLeaves().contains(leave));
        assertTrue(managerService.getPendingRequests().isEmpty());
    }

    @Test
    void shouldThrowWhenApprovingUnknownRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> managerService.approveLeaveRequest(999));
    }

    @Test
    void shouldThrowWhenRejectingUnknownRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> managerService.rejectLeaveRequest(999));
    }

    @Test
    void shouldThrowWhenApprovingAlreadyApprovedRequest() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave = new Leave(
                LocalDate.of(2026, 7, 1),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)),
                LeaveType.VACATION,
                "Approved leave"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, leave);
        managerService.approveLeaveRequest(request.getId());

        assertThrows(IllegalStateException.class,
                () -> managerService.approveLeaveRequest(request.getId()));
    }

    @Test
    void shouldThrowWhenRejectingAlreadyRejectedRequest() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave = new Leave(
                LocalDate.of(2026, 8, 1),
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(14, 0)),
                LeaveType.PERSONAL,
                "Errand"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, leave);
        managerService.rejectLeaveRequest(request.getId());

        assertThrows(IllegalStateException.class,
                () -> managerService.rejectLeaveRequest(request.getId()));
    }

    @Test
    void shouldThrowWhenRejectingApprovedRequest() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave = new Leave(
                LocalDate.of(2026, 8, 10),
                new TimeRange(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                LeaveType.VACATION,
                "Summer leave"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, leave);
        managerService.approveLeaveRequest(request.getId());

        assertThrows(IllegalStateException.class,
                () -> managerService.rejectLeaveRequest(request.getId()));
    }

    @Test
    void shouldKeepRequestPendingIfApprovedLeaveConflictsWithExistingLeave() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave existingLeave = new Leave(
                LocalDate.of(2026, 9, 10),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)),
                LeaveType.VACATION,
                "Existing leave"
        );
        managerService.addLeave(employee, existingLeave);

        Leave conflictingLeave = new Leave(
                LocalDate.of(2026, 9, 10),
                new TimeRange(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                LeaveType.PERSONAL,
                "Conflicting leave"
        );

        LeaveRequest request = managerService.submitLeaveRequest(employee, conflictingLeave);

        /*
         * Questo test è corretto SOLO se LeaveCalendar.addLeave(...)
         * rifiuta leave sovrapposti con una IllegalArgumentException
         * (o altra eccezione coerente).
         */
        assertThrows(IllegalArgumentException.class,
                () -> managerService.approveLeaveRequest(request.getId()));

        assertEquals(RequestStatus.PENDING, request.getStatus());
        assertFalse(employee.getLeaveCalendar().getLeaves().contains(conflictingLeave));
        assertEquals(1, managerService.getPendingRequests().size());
        assertTrue(managerService.getPendingRequests().contains(request));
    }

    @Test
    void shouldReturnOnlyPendingRequests() {
        Employee employee = managerService.createEmployee("Mario", Priority.MEDIUM, 40, 12);

        Leave leave1 = new Leave(
                LocalDate.of(2026, 10, 1),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                LeaveType.VACATION,
                "Leave 1"
        );

        Leave leave2 = new Leave(
                LocalDate.of(2026, 10, 2),
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(13, 0)),
                LeaveType.SICK,
                "Leave 2"
        );

        Leave leave3 = new Leave(
                LocalDate.of(2026, 10, 3),
                new TimeRange(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                LeaveType.PERSONAL,
                "Leave 3"
        );

        LeaveRequest request1 = managerService.submitLeaveRequest(employee, leave1);
        LeaveRequest request2 = managerService.submitLeaveRequest(employee, leave2);
        LeaveRequest request3 = managerService.submitLeaveRequest(employee, leave3);

        managerService.approveLeaveRequest(request1.getId());
        managerService.rejectLeaveRequest(request2.getId());

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();

        assertEquals(1, pendingRequests.size());
        assertTrue(pendingRequests.contains(request3));
        assertFalse(pendingRequests.contains(request1));
        assertFalse(pendingRequests.contains(request2));
    }
}