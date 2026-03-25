package it.pacenti.moka.persistence.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.persistence.json.model.EmployeeData;
import it.pacenti.moka.persistence.json.model.EmployeeDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JsonEmployeeRepository implements EmployeeRepository {

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final Map<String, Employee> employeesByName;

    public JsonEmployeeRepository(Path filePath) {
        this(filePath, JsonObjectMapperFactory.create());
    }

    public JsonEmployeeRepository(Path filePath, ObjectMapper objectMapper) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.employeesByName = new LinkedHashMap<>();
        loadFromDisk();
    }

    @Override
    public void save(Employee employee) {
        Objects.requireNonNull(employee, "Employee cannot be null");

        employeesByName.put(normalizeName(employee.getName()), employee);
        flushToDisk();
    }

    @Override
    public Optional<Employee> findByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return Optional.ofNullable(employeesByName.get(normalizeName(name)));
    }

    @Override
    public boolean existsByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return employeesByName.containsKey(normalizeName(name));
    }

    @Override
    public List<Employee> findAll() {
        return new ArrayList<>(employeesByName.values());
    }

    private void loadFromDisk() {
        if (Files.notExists(filePath)) {
            return;
        }

        try {
            EmployeeDocument document = objectMapper.readValue(filePath.toFile(), EmployeeDocument.class);
            employeesByName.clear();

            if (document.getEmployees() == null) {
                return;
            }

            for (EmployeeData data : document.getEmployees()) {
                Employee employee = toDomain(data);
                employeesByName.put(normalizeName(employee.getName()), employee);
            }

        } catch (IOException e) {
            throw new JsonPersistenceException("Failed to load employees from JSON: " + filePath, e);
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(filePath.getParent());

            EmployeeDocument document = new EmployeeDocument();
            document.setEmployees(
                    employeesByName.values()
                            .stream()
                            .map(this::toData)
                            .toList()
            );

            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
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
            throw new JsonPersistenceException("Failed to save employees to JSON: " + filePath, e);
        }
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase();
    }

    private EmployeeData toData(Employee employee) {
        EmployeeData data = new EmployeeData();

        data.setName(employee.getName());
        data.setPriority(employee.getPriority().name());
        data.setAgreedHours(employee.getAgreedHours());
        data.setHourlyCost(employee.getHourlyCost());

        // TODO: mappare skills
        // TODO: mappare weekly availability
        // TODO: mappare approved leaves

        return data;
    }

    private Employee toDomain(EmployeeData data) {
        // TODO: usare EmployeeFactory per ricostruire l'aggregate root
        // TODO: ripristinare skills
        // TODO: ripristinare weekly availability
        // TODO: ripristinare approved leaves

        throw new UnsupportedOperationException("Employee JSON mapping not completed yet");
    }
}