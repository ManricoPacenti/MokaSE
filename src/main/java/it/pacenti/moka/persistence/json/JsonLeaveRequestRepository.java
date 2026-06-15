package it.pacenti.moka.persistence.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.RequestStatus;
import it.pacenti.moka.persistence.json.mapper.LeaveRequestJsonMapper;
import it.pacenti.moka.persistence.json.model.LeaveRequestData;
import it.pacenti.moka.persistence.json.model.LeaveRequestDocument;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-based implementation of LeaveRequestRepository.
 * It persists leave requests and reconstructs employee references through EmployeeRepository.
 */
public class JsonLeaveRequestRepository implements LeaveRequestRepository {

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestJsonMapper mapper;
    private final Map<Integer, LeaveRequest> requestsById;

    public JsonLeaveRequestRepository(Path filePath, EmployeeRepository employeeRepository) {
        this(
                filePath,
                JsonObjectMapperFactory.create(),
                employeeRepository,
                new LeaveRequestJsonMapper()
        );
    }

    public JsonLeaveRequestRepository(
            Path filePath,
            ObjectMapper objectMapper,
            EmployeeRepository employeeRepository,
            LeaveRequestJsonMapper mapper
    ) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "Employee repository cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "LeaveRequestJsonMapper cannot be null");
        this.requestsById = new LinkedHashMap<>();

        loadFromDisk();
    }

    @Override
    public LeaveRequest save(LeaveRequest request) {
        Objects.requireNonNull(request, "Leave request cannot be null");

        requestsById.put(request.getId(), request);
        flushToDisk();

        return request;
    }

    @Override
    public Optional<LeaveRequest> findById(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Request id must be greater than zero");
        }
        return Optional.ofNullable(requestsById.get(id));
    }

    @Override
    public List<LeaveRequest> findAll() {
        return requestsById.values()
                .stream()
                .sorted(Comparator.comparingInt(LeaveRequest::getId))
                .toList();
    }

    @Override
    public List<LeaveRequest> findPending() {
        return requestsById.values()
                .stream()
                .filter(request -> request.getStatus() == RequestStatus.PENDING)
                .sorted(Comparator.comparingInt(LeaveRequest::getId))
                .toList();
    }

    @Override
    public int nextId() {
        return requestsById.keySet()
                .stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    private void loadFromDisk() {
        if (Files.notExists(filePath)) {
            return;
        }

        try {
            LeaveRequestDocument document = objectMapper.readValue(
                    filePath.toFile(),
                    LeaveRequestDocument.class
            );

            requestsById.clear();

            if (document.getRequests() == null) {
                return;
            }

            for (LeaveRequestData data : document.getRequests()) {
                if (data == null) {
                    continue;
                }

                LeaveRequest request = mapper.toDomain(data, employeeRepository);
                requestsById.put(request.getId(), request);
            }

        } catch (IOException e) {
            throw new JsonPersistenceException(
                    "Failed to load leave requests from JSON: " + filePath,
                    e
            );
        } catch (RuntimeException e) {
            throw new JsonPersistenceException(
                    "Failed to reconstruct leave requests from JSON: " + filePath,
                    e
            );
        }
    }

    private void flushToDisk() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            LeaveRequestDocument document = new LeaveRequestDocument();
            List<LeaveRequestData> requestData = new ArrayList<>();

            for (LeaveRequest request : findAll()) {
                requestData.add(mapper.toData(request));
            }

            document.setRequests(requestData);

            Path tempFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            objectMapper.writeValue(tempFile.toFile(), document);

            try {
                Files.move(
                        tempFile,
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicMoveFailure) {
                Files.move(
                        tempFile,
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new JsonPersistenceException(
                    "Failed to save leave requests to JSON: " + filePath,
                    e
            );
        }
    }
}