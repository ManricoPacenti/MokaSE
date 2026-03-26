package it.pacenti.moka.persistence.mapper;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.persistence.dto.LeaveData;
import it.pacenti.moka.persistence.dto.LeaveRequestData;
import it.pacenti.moka.repository.EmployeeRepository;

import java.util.Objects;

public class LeaveRequestJsonMapper {

    public LeaveRequestData toData(LeaveRequest request) {
        Objects.requireNonNull(request, "Leave request cannot be null");

        Leave leave = request.getLeave();
        LeaveData leaveData = new LeaveData(
                leave.getDate(),
                leave.getRange().getStart(),
                leave.getRange().getEnd(),
                leave.getType().name()
        );

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
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot reconstruct LeaveRequest: employee not found: " + data.getEmployeeName()
                ));

        LeaveData leaveData = Objects.requireNonNull(data.getLeave(), "Leave data cannot be null");

        Leave leave = new Leave(
                leaveData.getDate(),
                new it.pacenti.moka.scheduling.TimeRange(
                        leaveData.getStartTime(),
                        leaveData.getEndTime()
                ),
                LeaveType.valueOf(leaveData.getType())
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