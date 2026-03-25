package it.pacenti.moka.repository;

import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.RequestStatus;

import java.util.*;

public class InMemoryLeaveRequestRepository implements LeaveRequestRepository {

    private final Map<Integer, LeaveRequest> requestsById = new HashMap<>();

    @Override
    public LeaveRequest save(LeaveRequest request) {
        Objects.requireNonNull(request, "Leave request cannot be null");
        requestsById.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<LeaveRequest> findById(int id) {
        return Optional.ofNullable(requestsById.get(id));
    }

    @Override
    public List<LeaveRequest> findAll() {
        return new ArrayList<>(requestsById.values());
    }

    @Override
    public List<LeaveRequest> findPending() {
        List<LeaveRequest> pending = new ArrayList<>();
        for (LeaveRequest request : requestsById.values()) {
            if (request.getStatus() == RequestStatus.PENDING) {
                pending.add(request);
            }
        }
        return pending;
    }
}