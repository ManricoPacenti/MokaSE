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
                "Integration Employee Test",
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

        EmployeeRepository reloadedEmployeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(
                        requestsFile,
                        reloadedEmployeeRepository
                );

        Optional<Employee> loadedEmployee =
                reloadedEmployeeRepository.findByName("Integration Employee Test");

        Optional<LeaveRequest> loadedRequest =
                reloadedLeaveRequestRepository.findById(1);

        assertAll(
                () -> assertTrue(loadedEmployee.isPresent()),
                () -> assertTrue(loadedRequest.isPresent()),
                () -> assertEquals("Integration Employee Test", loadedEmployee.get().getName()),
                () -> assertEquals(RequestStatus.PENDING, loadedRequest.get().getStatus())
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
                "Approved Leave Employee Test",
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

        LeaveRequest request =
                managerService.createLeaveRequest(employee.getName(), leave);

        managerService.approveRequest(request.getId());

        EmployeeRepository reloadedEmployeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(
                        requestsFile,
                        reloadedEmployeeRepository
                );

        Employee reloadedEmployee =
                reloadedEmployeeRepository
                        .findByName("Approved Leave Employee Test")
                        .orElseThrow();

        LeaveRequest reloadedRequest =
                reloadedLeaveRequestRepository
                        .findById(request.getId())
                        .orElseThrow();

        assertAll(
                () -> assertEquals(RequestStatus.APPROVED, reloadedRequest.getStatus()),
                () -> assertEquals(
                        "Approved Leave Employee Test",
                        reloadedRequest.getEmployee().getName()
                ),
                () -> assertEquals(
                        1,
                        reloadedEmployee.getLeaveCalendar().getLeaves().size()
                )
        );
    }

    @Test
    void shouldRejectRequestAndPersistRejectedStateWithoutAddingLeave() {

        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Rejected Leave Employee Test",
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

        LeaveRequest request =
                managerService.createLeaveRequest(employee.getName(), leave);

        managerService.rejectRequest(request.getId());

        EmployeeRepository reloadedEmployeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(
                        requestsFile,
                        reloadedEmployeeRepository
                );

        Employee reloadedEmployee =
                reloadedEmployeeRepository
                        .findByName("Rejected Leave Employee Test")
                        .orElseThrow();

        LeaveRequest reloadedRequest =
                reloadedLeaveRequestRepository
                        .findById(request.getId())
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

        EmployeeRepository employeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Pending Requests Employee Test",
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

        EmployeeRepository reloadedEmployeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(
                        requestsFile,
                        reloadedEmployeeRepository
                );

        ManagerService reloadedManagerService = new ManagerService(
                reloadedEmployeeRepository,
                reloadedLeaveRequestRepository,
                new EmployeeFactory()
        );

        List<LeaveRequest> pendingRequests =
                reloadedManagerService.getPendingRequests();

        assertEquals(1, pendingRequests.size());
        assertEquals(request3.getId(), pendingRequests.get(0).getId());
        assertEquals(RequestStatus.PENDING, pendingRequests.get(0).getStatus());
    }

    @Test
    void shouldKeepRequestPendingWhenApprovalFailsBecauseOfLeaveConflict() {

        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Conflicting Leave Employee Test",
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

        LeaveRequest conflictingRequest =
                managerService.createLeaveRequest(
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

        EmployeeRepository reloadedEmployeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(
                        requestsFile,
                        reloadedEmployeeRepository
                );

        Employee reloadedEmployee =
                reloadedEmployeeRepository
                        .findByName("Conflicting Leave Employee Test")
                        .orElseThrow();

        LeaveRequest reloadedRequest =
                reloadedLeaveRequestRepository
                        .findById(conflictingRequest.getId())
                        .orElseThrow();

        assertAll(
                () -> assertEquals(
                        1,
                        reloadedEmployee.getLeaveCalendar().getLeaves().size()
                ),
                () -> assertEquals(
                        RequestStatus.PENDING,
                        reloadedRequest.getStatus()
                )
        );
    }

    @Test
    void shouldThrowWhenApprovingAlreadyApprovedRequestAfterReload() {

        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                new EmployeeFactory()
        );

        Employee employee = managerService.createEmployee(
                "Already Approved Employee Test",
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

        EmployeeRepository reloadedEmployeeRepository =
                new JsonEmployeeRepository(employeesFile);

        LeaveRequestRepository reloadedLeaveRequestRepository =
                new JsonLeaveRequestRepository(
                        requestsFile,
                        reloadedEmployeeRepository
                );

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