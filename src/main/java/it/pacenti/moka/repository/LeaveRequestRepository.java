package it.pacenti.moka.repository;

import it.pacenti.moka.availability.LeaveRequest;

import java.util.List;
import java.util.Optional;

/**
 * Contract for storing and retrieving leave requests
 */
public interface LeaveRequestRepository {

    LeaveRequest save(LeaveRequest request);

    Optional<LeaveRequest> findById(int id);

    List<LeaveRequest> findAll();

    List<LeaveRequest> findPending();

    int nextId();
}