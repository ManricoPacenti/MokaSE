package it.pacenti.moka.persistence.json.mapper;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.persistence.json.JsonPersistenceException;
import it.pacenti.moka.persistence.json.model.LeaveData;
import it.pacenti.moka.persistence.json.model.LeaveRequestData;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.scheduling.TimeRange;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Converts LeaveRequest domain objects
 * to JSON persistence DTOs and vice versa.
 */
public class LeaveRequestJsonMapper {

    public LeaveRequestData toData(LeaveRequest request) {
        Objects.requireNonNull(request, "Leave request cannot be null");

        Leave leave = request.getLeave();

        LeaveData leaveData = new LeaveData();
        leaveData.setDate(leave.getDate().toString());
        leaveData.setType(leave.getType().name());
        leaveData.setStart(leave.getRange().getStart().toString());
        leaveData.setEnd(leave.getRange().getEnd().toString());
        leaveData.setNote(leave.getNote());

        return new LeaveRequestData(
                request.getId(),
                request.getEmployee().getName(),
                leaveData,
                request.getStatus().name()
        );
    }

    public LeaveRequest toDomain(LeaveRequestData data, EmployeeRepository employeeRepository) {
        Objects.requireNonNull(data, "LeaveRequestData cannot be null");
        Objects.requireNonNull(employeeRepository, "EmployeeRepository cannot be null");

        Employee employee = employeeRepository.findByName(data.getEmployeeName())
                .orElseThrow(() -> new JsonPersistenceException(
                        "Cannot reconstruct LeaveRequest: employee not found: " + data.getEmployeeName()
                ));

        LeaveData leaveData = Objects.requireNonNull(data.getLeave(), "Leave data cannot be null");

        Leave leave = new Leave(
                LocalDate.parse(leaveData.getDate()),
                new TimeRange(
                        LocalTime.parse(leaveData.getStart()),
                        LocalTime.parse(leaveData.getEnd())
                ),
                LeaveType.valueOf(leaveData.getType()),
                leaveData.getNote()
        );

        RequestStatus status = RequestStatus.valueOf(data.getStatus());

        return LeaveRequest.reconstitute(
                data.getId(),
                employee,
                leave,
                status
        );
    }
}