package it.pacenti.moka.service;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.exception.InvalidLeaveRequestStateException;
import it.pacenti.moka.persistence.json.JsonEmployeeRepository;
import it.pacenti.moka.persistence.json.JsonLeaveRequestRepository;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;
import it.pacenti.moka.scheduling.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ManagerServiceJsonIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateEmployeeAndLeaveRequestAndReloadEverythingFromJson() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Mario Rossi",
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
                () -> assertEquals("Mario Rossi", employee.getName()),
                () -> assertEquals(RequestStatus.PENDING, request.getStatus()),
                () -> assertEquals(1, request.getId())
        );

        EmployeeRepository reloadedEmployeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, reloadedEmployeeRepository);

        Optional<Employee> loadedEmployee = reloadedEmployeeRepository.findByName("Mario Rossi");
        Optional<LeaveRequest> loadedRequest = reloadedLeaveRequestRepository.findById(1);

        assertAll(
                () -> assertTrue(loadedEmployee.isPresent()),
                () -> assertEquals("Mario Rossi", loadedEmployee.get().getName()),
                () -> assertTrue(loadedRequest.isPresent()),
                () -> assertEquals(RequestStatus.PENDING, loadedRequest.get().getStatus()),
                () -> assertEquals("Mario Rossi", loadedRequest.get().getEmployee().getName()),
                () -> assertEquals(LocalDate.of(2026, 4, 10), loadedRequest.get().getLeave().getDate()),
                () -> assertEquals(LocalTime.of(10, 0), loadedRequest.get().getLeave().getRange().getStart()),
                () -> assertEquals(LocalTime.of(14, 0), loadedRequest.get().getLeave().getRange().getEnd()),
                () -> assertEquals(LeaveType.VACATION, loadedRequest.get().getLeave().getType()),
                () -> assertEquals("Weekend trip", loadedRequest.get().getLeave().getNote())
        );
    }

    @Test
    void shouldApproveRequestAndPersistBothEmployeeAndRequest() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Anna Verdi",
                Priority.HIGH,
                32,
                14
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 5, 5),
                new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                LeaveType.PERSONAL,
                "Family dinner"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);

        managerService.approveRequest(request.getId());

        EmployeeRepository reloadedEmployeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, reloadedEmployeeRepository);

        Employee reloadedEmployee = reloadedEmployeeRepository.findByName("Anna Verdi")
                .orElseThrow();

        LeaveRequest reloadedRequest = reloadedLeaveRequestRepository.findById(request.getId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(RequestStatus.APPROVED, reloadedRequest.getStatus()),
                () -> assertEquals("Anna Verdi", reloadedRequest.getEmployee().getName()),
                () -> assertEquals(1, reloadedEmployee.getLeaveCalendar().getLeaves().size()),
                () -> assertEquals(LocalDate.of(2026, 5, 5), reloadedEmployee.getLeaveCalendar().getLeaves().get(0).getDate()),
                () -> assertEquals(LeaveType.PERSONAL, reloadedEmployee.getLeaveCalendar().getLeaves().get(0).getType()),
                () -> assertEquals("Family dinner", reloadedEmployee.getLeaveCalendar().getLeaves().get(0).getNote())
        );
    }

    @Test
    void shouldRejectRequestAndPersistRejectedStateWithoutAddingLeave() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Luca Neri",
                Priority.LOW,
                24,
                10
        );

        Leave leave = new Leave(
                LocalDate.of(2026, 6, 12),
                new TimeRange(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                LeaveType.SICK,
                "Flu symptoms"
        );

        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);

        managerService.rejectRequest(request.getId());

        EmployeeRepository reloadedEmployeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, reloadedEmployeeRepository);

        Employee reloadedEmployee = reloadedEmployeeRepository.findByName("Luca Neri")
                .orElseThrow();

        LeaveRequest reloadedRequest = reloadedLeaveRequestRepository.findById(request.getId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(RequestStatus.REJECTED, reloadedRequest.getStatus()),
                () -> assertTrue(reloadedEmployee.getLeaveCalendar().getLeaves().isEmpty())
        );
    }

    @Test
    void shouldReturnOnlyPendingRequestsAfterApproveAndReject() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Giulia Bianchi",
                Priority.MEDIUM,
                30,
                12
        );

        LeaveRequest request1 = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 7, 1),
                        new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        LeaveType.VACATION,
                        "Leave 1"
                )
        );

        LeaveRequest request2 = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 7, 2),
                        new TimeRange(LocalTime.of(10, 0), LocalTime.of(13, 0)),
                        LeaveType.PERSONAL,
                        "Leave 2"
                )
        );

        LeaveRequest request3 = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 7, 3),
                        new TimeRange(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                        LeaveType.SICK,
                        "Leave 3"
                )
        );

        managerService.approveRequest(request1.getId());
        managerService.rejectRequest(request2.getId());

        EmployeeRepository reloadedEmployeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, reloadedEmployeeRepository);

        ManagerService reloadedManagerService = new ManagerService(
                reloadedEmployeeRepository,
                reloadedLeaveRequestRepository,
                new EmployeeFactory()
        );

        List<LeaveRequest> pendingRequests = reloadedManagerService.getPendingRequests();

        assertEquals(1, pendingRequests.size());
        assertEquals(request3.getId(), pendingRequests.get(0).getId());
        assertEquals(RequestStatus.PENDING, pendingRequests.get(0).getStatus());
    }

    @Test
    void shouldKeepRequestPendingWhenApprovalFailsBecauseOfLeaveConflict() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Paolo Gialli",
                Priority.HIGH,
                38,
                16
        );

        Leave existingLeave = new Leave(
                LocalDate.of(2026, 8, 10),
                new TimeRange(LocalTime.of(9, 0), LocalTime.of(13, 0)),
                LeaveType.VACATION,
                "Existing leave"
        );

        employee.addLeave(existingLeave);
        employeeRepository.save(employee);

        LeaveRequest conflictingRequest = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 8, 10),
                        new TimeRange(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                        LeaveType.PERSONAL,
                        "Conflicting request"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> managerService.approveRequest(conflictingRequest.getId())
        );

        EmployeeRepository reloadedEmployeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, reloadedEmployeeRepository);

        Employee reloadedEmployee = reloadedEmployeeRepository.findByName("Paolo Gialli")
                .orElseThrow();

        LeaveRequest reloadedRequest = reloadedLeaveRequestRepository.findById(conflictingRequest.getId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(1, reloadedEmployee.getLeaveCalendar().getLeaves().size()),
                () -> assertEquals("Existing leave", reloadedEmployee.getLeaveCalendar().getLeaves().get(0).getNote()),
                () -> assertEquals(RequestStatus.PENDING, reloadedRequest.getStatus())
        );
    }

    @Test
    void shouldThrowWhenApprovingAlreadyApprovedRequestAfterReload() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Sara Blu",
                Priority.MEDIUM,
                32,
                13
        );

        LeaveRequest request = managerService.createLeaveRequest(
                employee.getName(),
                new Leave(
                        LocalDate.of(2026, 9, 1),
                        new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                        LeaveType.VACATION,
                        "Trip"
                )
        );

        managerService.approveRequest(request.getId());

        EmployeeRepository reloadedEmployeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, reloadedEmployeeRepository);

        ManagerService reloadedManagerService = new ManagerService(
                reloadedEmployeeRepository,
                reloadedLeaveRequestRepository,
                new EmployeeFactory()
        );

        assertThrows(
                InvalidLeaveRequestStateException.class,
                () -> reloadedManagerService.approveRequest(request.getId())
        );
    }
}