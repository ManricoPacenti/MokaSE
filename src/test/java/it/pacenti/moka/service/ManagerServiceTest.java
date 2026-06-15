package it.pacenti.moka.service;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.exception.DuplicateEmployeeException;
import it.pacenti.moka.exception.EmployeeNotFoundException;
import it.pacenti.moka.exception.InvalidLeaveRequestStateException;
import it.pacenti.moka.exception.LeaveRequestNotFoundException;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.InMemoryEmployeeRepository;
import it.pacenti.moka.repository.InMemoryLeaveRequestRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;
import it.pacenti.moka.scheduling.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ManagerServiceTest {

    private EmployeeRepository employeeRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private EmployeeFactory employeeFactory;
    private ManagerService managerService;

    @BeforeEach
    void setUp() {
        employeeRepository = new InMemoryEmployeeRepository();
        leaveRequestRepository = new InMemoryLeaveRequestRepository();
        employeeFactory = new EmployeeFactory();

        managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                employeeFactory
        );
    }

    @Test
    void shouldCreateEmployeeAndPersistIt() {
        Employee employee = managerService.createEmployee(
                "Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        assertAll(
                () -> assertNotNull(employee),
                () -> assertEquals("Employee Test", employee.getName()),
                () -> assertEquals(Priority.MEDIUM, employee.getPriority()),
                () -> assertEquals(40, employee.getAgreedHours()),
                () -> assertEquals(12, employee.getHourlyCost()),
                () -> assertTrue(employeeRepository.existsByName("Employee Test"))
        );
    }

    @Test
    void shouldThrowWhenCreatingDuplicateEmployee() {
        managerService.createEmployee("Employee Test", Priority.MEDIUM, 40, 12);

        assertThrows(
                DuplicateEmployeeException.class,
                () -> managerService.createEmployee("  Employee Test  ", Priority.HIGH, 30, 15)
        );
    }

    @Test
    void shouldFindEmployeeByName() {
        Employee created = managerService.createEmployee(
                "Search Employee Test",
                Priority.HIGH,
                32,
                14
        );

        Optional<Employee> found = managerService.findEmployee("  search employee test ");

        assertTrue(found.isPresent());
        assertEquals(created.getName(), found.get().getName());
    }

    @Test
    void shouldReturnEmptyWhenEmployeeIsNotFound() {
        Optional<Employee> found = managerService.findEmployee("Missing Employee Test");

        assertTrue(found.isEmpty());
    }

    @Test
    void shouldGetEmployeeByName() {
        Employee created = managerService.createEmployee(
                "Search Employee Test",
                Priority.HIGH,
                32,
                14
        );

        Employee found = managerService.getEmployeeByName(" search employee test ");

        assertEquals(created.getName(), found.getName());
    }

    @Test
    void shouldThrowWhenGettingUnknownEmployeeByName() {
        assertThrows(
                EmployeeNotFoundException.class,
                () -> managerService.getEmployeeByName("Missing Employee Test")
        );
    }

    @Test
    void shouldReturnAllEmployees() {
        Employee first = managerService.createEmployee("Barista Test", Priority.MEDIUM, 40, 12);
        Employee second = managerService.createEmployee("Responsabile Test", Priority.HIGH, 30, 15);

        List<Employee> employees = managerService.getAllEmployees();

        assertEquals(2, employees.size());
        assertTrue(employees.contains(first));
        assertTrue(employees.contains(second));
    }

    @Test
    void shouldReturnUnmodifiableEmployeesList() {
        managerService.createEmployee("Employee Test", Priority.MEDIUM, 40, 12);

        List<Employee> employees = managerService.getAllEmployees();

        assertThrows(UnsupportedOperationException.class, () ->
                employees.add(employeeFactory.createEmployee("Fake Employee Test", Priority.LOW, 10, 8))
        );
    }

    @Test
    void shouldChangeEmployeePriority() {
        Employee employee = managerService.createEmployee(
                "Priority Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        managerService.changeEmployeePriority(" priority employee test ", Priority.HIGH);

        assertAll(
                () -> assertEquals(Priority.HIGH, employee.getPriority()),
                () -> assertEquals(
                        Priority.HIGH,
                        employeeRepository.findByName("Priority Employee Test").orElseThrow().getPriority()
                )
        );
    }

    @Test
    void shouldThrowWhenChangingPriorityOfUnknownEmployee() {
        assertThrows(
                EmployeeNotFoundException.class,
                () -> managerService.changeEmployeePriority("Missing Employee Test", Priority.HIGH)
        );
    }

    @Test
    void shouldDeleteEmployee() {
        managerService.createEmployee("Delete Employee Test", Priority.MEDIUM, 40, 12);

        managerService.deleteEmployee(" delete employee test ");

        assertAll(
                () -> assertTrue(managerService.findEmployee("Delete Employee Test").isEmpty()),
                () -> assertFalse(employeeRepository.existsByName("Delete Employee Test")),
                () -> assertTrue(managerService.getAllEmployees().isEmpty())
        );
    }

    @Test
    void shouldThrowWhenDeletingUnknownEmployee() {
        assertThrows(
                EmployeeNotFoundException.class,
                () -> managerService.deleteEmployee("Missing Employee Test")
        );
    }

    @Test
    void shouldCreatePendingLeaveRequestForExistingEmployee() {
        Employee employee = managerService.createEmployee(
                "Leave Request Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 4, 10),
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(14, 0)),
                LeaveType.VACATION,
                "Weekend trip"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);

        assertAll(
                () -> assertNotNull(request),
                () -> assertEquals(1, request.getId()),
                () -> assertEquals(employee, request.getEmployee()),
                () -> assertEquals(leave, request.getLeave()),
                () -> assertEquals(RequestStatus.PENDING, request.getStatus())
        );
    }

    @Test
    void shouldThrowWhenCreatingLeaveRequestForUnknownEmployee() {
        Leave leave = new Leave(
                LocalDate.of(2026, 4, 15),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)),
                LeaveType.PERSONAL,
                "Personal appointment"
        );

        assertThrows(
                EmployeeNotFoundException.class,
                () -> managerService.createLeaveRequest("Missing Employee Test", leave)
        );
    }

    @Test
    void shouldReturnOnlyPendingRequests() {
        Employee employee = managerService.createEmployee(
                "Pending Request Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        LeaveRequest request1 = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 5, 1),
                        new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        LeaveType.VACATION,
                        "Leave 1"
                )
        );

        LeaveRequest request2 = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 5, 2),
                        new TimeRange(LocalTime.of(10, 0), LocalTime.of(13, 0)),
                        LeaveType.SICK,
                        "Leave 2"
                )
        );

        LeaveRequest request3 = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 5, 3),
                        new TimeRange(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                        LeaveType.PERSONAL,
                        "Leave 3"
                )
        );

        managerService.approveRequest(request1.getId());
        managerService.rejectRequest(request2.getId());

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();

        assertEquals(1, pendingRequests.size());
        assertTrue(pendingRequests.contains(request3));
        assertFalse(pendingRequests.contains(request1));
        assertFalse(pendingRequests.contains(request2));
    }

    @Test
    void shouldApprovePendingRequestAndPersistApprovedLeave() {
        Employee employee = managerService.createEmployee(
                "Approved Leave Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 6, 1),
                new TimeRange(LocalTime.of(12, 0), LocalTime.of(18, 0)),
                LeaveType.VACATION,
                "Day off"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);

        managerService.approveRequest(request.getId());

        assertAll(
                () -> assertEquals(RequestStatus.APPROVED, request.getStatus()),
                () -> assertTrue(employee.getLeaveCalendar().getLeaves().contains(leave)),
                () -> assertTrue(managerService.getPendingRequests().isEmpty())
        );
    }

    @Test
    void shouldRejectPendingRequestWithoutAddingLeaveToEmployee() {
        Employee employee = managerService.createEmployee(
                "Rejected Leave Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 6, 10),
                new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                LeaveType.SICK,
                "Flu"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);

        managerService.rejectRequest(request.getId());

        assertAll(
                () -> assertEquals(RequestStatus.REJECTED, request.getStatus()),
                () -> assertFalse(employee.getLeaveCalendar().getLeaves().contains(leave)),
                () -> assertTrue(managerService.getPendingRequests().isEmpty())
        );
    }

    @Test
    void shouldThrowWhenApprovingUnknownRequest() {
        assertThrows(
                LeaveRequestNotFoundException.class,
                () -> managerService.approveRequest(999)
        );
    }

    @Test
    void shouldThrowWhenRejectingUnknownRequest() {
        assertThrows(
                LeaveRequestNotFoundException.class,
                () -> managerService.rejectRequest(999)
        );
    }

    @Test
    void shouldThrowWhenApprovingAlreadyApprovedRequest() {
        Employee employee = managerService.createEmployee(
                "Already Approved Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 7, 1),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)),
                LeaveType.VACATION,
                "Approved leave"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);
        managerService.approveRequest(request.getId());

        assertThrows(
                InvalidLeaveRequestStateException.class,
                () -> managerService.approveRequest(request.getId())
        );
    }

    @Test
    void shouldThrowWhenRejectingAlreadyRejectedRequest() {
        Employee employee = managerService.createEmployee(
                "Already Rejected Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 8, 1),
                new TimeRange(LocalTime.of(10, 0), LocalTime.of(14, 0)),
                LeaveType.PERSONAL,
                "Errand"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);
        managerService.rejectRequest(request.getId());

        assertThrows(
                InvalidLeaveRequestStateException.class,
                () -> managerService.rejectRequest(request.getId())
        );
    }

    @Test
    void shouldThrowWhenRejectingApprovedRequest() {
        Employee employee = managerService.createEmployee(
                "Approved Then Rejected Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 8, 10),
                new TimeRange(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                LeaveType.VACATION,
                "Summer leave"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);
        managerService.approveRequest(request.getId());

        assertThrows(
                InvalidLeaveRequestStateException.class,
                () -> managerService.rejectRequest(request.getId())
        );
    }

    @Test
    void shouldKeepRequestPendingWhenApprovedLeaveConflictsWithExistingLeave() {
        Employee employee = managerService.createEmployee(
                "Conflicting Leave Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave existingLeave = new Leave(
                LocalDate.of(2026, 9, 10),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)),
                LeaveType.VACATION,
                "Existing leave"
        );

        employee.addLeave(existingLeave);
        employeeRepository.save(employee);

        Leave conflictingLeave = new Leave(
                LocalDate.of(2026, 9, 10),
                new TimeRange(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                LeaveType.PERSONAL,
                "Conflicting leave"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), conflictingLeave);

        assertThrows(
                IllegalArgumentException.class,
                () -> managerService.approveRequest(request.getId())
        );

        assertAll(
                () -> assertEquals(RequestStatus.PENDING, request.getStatus()),
                () -> assertFalse(employee.getLeaveCalendar().getLeaves().contains(conflictingLeave)),
                () -> assertEquals(1, managerService.getPendingRequests().size()),
                () -> assertTrue(managerService.getPendingRequests().contains(request))
        );
    }

    @Test
    void shouldGenerateProgressiveIdsForLeaveRequests() {
        Employee employee = managerService.createEmployee(
                "Progressive Id Employee Test",
                Priority.MEDIUM,
                40,
                12
        );

        Leave firstLeave = new Leave(
                LocalDate.of(2026, 10, 1),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                LeaveType.PERSONAL,
                "First"
        );

        Leave secondLeave = new Leave(
                LocalDate.of(2026, 10, 2),
                new TimeRange(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                LeaveType.VACATION,
                "Second"
        );

        LeaveRequest firstRequest = managerService.createLeaveRequest(employee.getName(), firstLeave);
        LeaveRequest secondRequest = managerService.createLeaveRequest(employee.getName(), secondLeave);

        assertEquals(1, firstRequest.getId());
        assertEquals(2, secondRequest.getId());
    }
}