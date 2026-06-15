package it.pacenti.moka.persistence;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.persistence.json.JsonEmployeeRepository;
import it.pacenti.moka.persistence.json.JsonLeaveRequestRepository;
import it.pacenti.moka.persistence.json.JsonPersistenceException;
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

class JsonLeaveRequestRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndReloadLeaveRequestFromJsonFile() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository requestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        EmployeeFactory factory = new EmployeeFactory();
        Employee employee = factory.createEmployee(
                "Leave Request Employee Test",
                Priority.HIGH,
                40,
                14
        );

        employeeRepository.save(employee);

        Leave leave = new Leave(
                LocalDate.of(2026, 4, 2),
                new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                LeaveType.VACATION,
                "Weekend trip"
        );

        LeaveRequest request = new LeaveRequest(1, employee, leave);
        requestRepository.save(request);

        LeaveRequestRepository reloadedRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        Optional<LeaveRequest> loadedOptional = reloadedRepository.findById(1);

        assertTrue(loadedOptional.isPresent(), "Leave request should be found after reload");

        LeaveRequest loaded = loadedOptional.get();

        assertEquals(1, loaded.getId());
        assertEquals("Leave Request Employee Test", loaded.getEmployee().getName());
        assertEquals(RequestStatus.PENDING, loaded.getStatus());
        assertEquals(LocalDate.of(2026, 4, 2), loaded.getLeave().getDate());
        assertEquals(LocalTime.of(18, 0), loaded.getLeave().getRange().getStart());
        assertEquals(LocalTime.of(23, 0), loaded.getLeave().getRange().getEnd());
        assertEquals(LeaveType.VACATION, loaded.getLeave().getType());
        assertEquals("Weekend trip", loaded.getLeave().getNote());
    }

    @Test
    void shouldUpdateExistingLeaveRequestWhenSavingSameId() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository requestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        EmployeeFactory factory = new EmployeeFactory();
        Employee employee = factory.createEmployee(
                "Leave Update Employee Test",
                Priority.MEDIUM,
                30,
                12
        );

        employeeRepository.save(employee);

        Leave originalLeave = new Leave(
                LocalDate.of(2026, 4, 10),
                new TimeRange(LocalTime.of(12, 0), LocalTime.of(15, 0)),
                LeaveType.PERSONAL,
                "Medical visit"
        );

        LeaveRequest originalRequest = new LeaveRequest(7, employee, originalLeave);
        requestRepository.save(originalRequest);

        Leave updatedLeave = new Leave(
                LocalDate.of(2026, 4, 11),
                new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                LeaveType.VACATION,
                "Updated trip"
        );

        LeaveRequest updatedRequest = LeaveRequest.reconstitute(
                7,
                employee,
                updatedLeave,
                RequestStatus.APPROVED
        );

        requestRepository.save(updatedRequest);

        LeaveRequestRepository reloadedRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        List<LeaveRequest> all = reloadedRepository.findAll();

        assertEquals(1, all.size(), "Repository should contain only one request after update");

        LeaveRequest loaded = all.get(0);

        assertEquals(7, loaded.getId());
        assertEquals(RequestStatus.APPROVED, loaded.getStatus());
        assertEquals(LocalDate.of(2026, 4, 11), loaded.getLeave().getDate());
        assertEquals(LocalTime.of(18, 0), loaded.getLeave().getRange().getStart());
        assertEquals(LocalTime.of(23, 0), loaded.getLeave().getRange().getEnd());
        assertEquals(LeaveType.VACATION, loaded.getLeave().getType());
        assertEquals("Updated trip", loaded.getLeave().getNote());
    }

    @Test
    void shouldReturnOnlyPendingRequests() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository requestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        EmployeeFactory factory = new EmployeeFactory();

        Employee pendingEmployee =
                factory.createEmployee("Pending Leave Employee Test", Priority.HIGH, 40, 15);

        Employee approvedEmployee =
                factory.createEmployee("Approved Leave Employee Test", Priority.MEDIUM, 24, 11);

        Employee rejectedEmployee =
                factory.createEmployee("Rejected Leave Employee Test", Priority.LOW, 20, 10);

        employeeRepository.save(pendingEmployee);
        employeeRepository.save(approvedEmployee);
        employeeRepository.save(rejectedEmployee);

        LeaveRequest pendingRequest = new LeaveRequest(
                1,
                pendingEmployee,
                new Leave(
                        LocalDate.of(2026, 4, 5),
                        new TimeRange(LocalTime.of(12, 0), LocalTime.of(15, 0)),
                        LeaveType.PERSONAL,
                        "Pending request"
                )
        );

        LeaveRequest approvedRequest = LeaveRequest.reconstitute(
                2,
                approvedEmployee,
                new Leave(
                        LocalDate.of(2026, 4, 6),
                        new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                        LeaveType.VACATION,
                        "Approved request"
                ),
                RequestStatus.APPROVED
        );

        LeaveRequest rejectedRequest = LeaveRequest.reconstitute(
                3,
                rejectedEmployee,
                new Leave(
                        LocalDate.of(2026, 4, 7),
                        new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                        LeaveType.SICK,
                        "Rejected request"
                ),
                RequestStatus.REJECTED
        );

        requestRepository.save(pendingRequest);
        requestRepository.save(approvedRequest);
        requestRepository.save(rejectedRequest);

        LeaveRequestRepository reloadedRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        List<LeaveRequest> pending = reloadedRepository.findPending();

        assertEquals(1, pending.size());
        assertEquals(1, pending.get(0).getId());
        assertEquals(RequestStatus.PENDING, pending.get(0).getStatus());
        assertEquals("Pending Leave Employee Test", pending.get(0).getEmployee().getName());
    }

    @Test
    void shouldGenerateNextIdBasedOnPersistedRequests() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository requestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        EmployeeFactory factory = new EmployeeFactory();
        Employee employee = factory.createEmployee(
                "Next Id Employee Test",
                Priority.MEDIUM,
                32,
                13
        );

        employeeRepository.save(employee);

        assertEquals(1, requestRepository.nextId());

        requestRepository.save(new LeaveRequest(
                requestRepository.nextId(),
                employee,
                new Leave(
                        LocalDate.of(2026, 4, 12),
                        new TimeRange(LocalTime.of(12, 0), LocalTime.of(15, 0)),
                        LeaveType.PERSONAL,
                        "First request"
                )
        ));

        assertEquals(2, requestRepository.nextId());

        requestRepository.save(new LeaveRequest(
                requestRepository.nextId(),
                employee,
                new Leave(
                        LocalDate.of(2026, 4, 13),
                        new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                        LeaveType.VACATION,
                        "Second request"
                )
        ));

        LeaveRequestRepository reloadedRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        assertEquals(3, reloadedRepository.nextId());
    }

    @Test
    void shouldFailToLoadLeaveRequestIfReferencedEmployeeDoesNotExist() {
        Path employeesFile = tempDir.resolve("employees.json");
        Path requestsFile = tempDir.resolve("leave-requests.json");

        EmployeeRepository employeeRepository = new JsonEmployeeRepository(employeesFile);
        LeaveRequestRepository requestRepository =
                new JsonLeaveRequestRepository(requestsFile, employeeRepository);

        EmployeeFactory factory = new EmployeeFactory();
        Employee employee = factory.createEmployee(
                "Broken Reference Employee Test",
                Priority.HIGH,
                38,
                16
        );

        employeeRepository.save(employee);

        LeaveRequest request = new LeaveRequest(
                1,
                employee,
                new Leave(
                        LocalDate.of(2026, 4, 20),
                        new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0)),
                        LeaveType.VACATION,
                        "Will break after employee removal"
                )
        );

        requestRepository.save(request);

        Path otherEmployeesFile = tempDir.resolve("other-employees.json");
        EmployeeRepository emptyEmployeeRepository =
                new JsonEmployeeRepository(otherEmployeesFile);

        assertThrows(
                JsonPersistenceException.class,
                () -> new JsonLeaveRequestRepository(requestsFile, emptyEmployeeRepository)
        );
    }
}