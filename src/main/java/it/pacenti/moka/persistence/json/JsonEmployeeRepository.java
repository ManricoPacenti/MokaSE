package it.pacenti.moka.persistence.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.availability.WeeklyAvailability;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.employee.EmployeeSkill;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.persistence.json.model.EmployeeData;
import it.pacenti.moka.persistence.json.model.EmployeeDocument;
import it.pacenti.moka.persistence.json.model.EmployeeSkillData;
import it.pacenti.moka.persistence.json.model.LeaveData;
import it.pacenti.moka.persistence.json.model.TimeRangeData;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.scheduling.TimeRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-based implementation of EmployeeRepository.
 * It keeps employees in memory and synchronizes changes to disk.
 */
public class JsonEmployeeRepository implements EmployeeRepository {

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final EmployeeFactory employeeFactory;
    private final Map<String, Employee> employeesByName;

    public JsonEmployeeRepository(Path filePath) {
        this(filePath, JsonObjectMapperFactory.create(), new EmployeeFactory());
    }

    public JsonEmployeeRepository(
            Path filePath,
            ObjectMapper objectMapper,
            EmployeeFactory employeeFactory
    ) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.employeeFactory = Objects.requireNonNull(employeeFactory, "EmployeeFactory cannot be null");
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
        return List.copyOf(employeesByName.values());
    }

    @Override
    public void deleteByName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalizedName = normalizeName(name);
        employeesByName.remove(normalizedName);
        flushToDisk();
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
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            EmployeeDocument document = new EmployeeDocument();
            document.setEmployees(
                    employeesByName.values()
                            .stream()
                            .map(this::toData)
                            .toList()
            );

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
            throw new JsonPersistenceException("Failed to save employees to JSON: " + filePath, e);
        }
    }

    private EmployeeData toData(Employee employee) {
        EmployeeData data = new EmployeeData();

        data.setName(employee.getName());
        data.setPriority(employee.getPriority().name());
        data.setAgreedHours(employee.getAgreedHours());
        data.setHourlyCost(employee.getHourlyCost());

        data.setSkills(mapSkills(employee));
        data.setWeeklyTimeOff(mapWeeklyTimeOff(employee.getAvailability()));
        data.setFullDaysOff(mapFullDaysOff(employee.getAvailability()));
        data.setApprovedLeaves(mapApprovedLeaves(employee));

        return data;
    }

    private Employee toDomain(EmployeeData data) {
        validateEmployeeData(data);

        Employee employee = employeeFactory.createEmployee(
                data.getName(),
                Priority.valueOf(data.getPriority()),
                data.getAgreedHours(),
                data.getHourlyCost()
        );

        restoreSkills(employee, data.getSkills());
        restoreWeeklyTimeOff(employee, data.getWeeklyTimeOff());
        restoreFullDaysOff(employee, data.getFullDaysOff());
        restoreApprovedLeaves(employee, data.getApprovedLeaves());

        return employee;
    }

    private List<EmployeeSkillData> mapSkills(Employee employee) {
        List<EmployeeSkillData> result = new ArrayList<>();

        for (EmployeeSkill skill : employee.getSkills().asCollection()) {
            EmployeeSkillData skillData = new EmployeeSkillData();
            skillData.setSkill(skill.getSkill().name());
            skillData.setProficiency(skill.getProficiency().name());
            result.add(skillData);
        }

        return result;
    }

    private Map<String, List<TimeRangeData>> mapWeeklyTimeOff(WeeklyAvailability availability) {
        Map<String, List<TimeRangeData>> result = new LinkedHashMap<>();

        for (Map.Entry<DayOfWeek, List<TimeRange>> entry : availability.getTimeOff().entrySet()) {
            List<TimeRangeData> ranges = new ArrayList<>();

            for (TimeRange range : entry.getValue()) {
                ranges.add(toTimeRangeData(range));
            }

            result.put(entry.getKey().name(), ranges);
        }

        return result;
    }

    private List<String> mapFullDaysOff(WeeklyAvailability availability) {
        List<String> result = new ArrayList<>();

        for (DayOfWeek day : availability.getFullDaysOff()) {
            result.add(day.name());
        }

        return result;
    }

    private List<LeaveData> mapApprovedLeaves(Employee employee) {
        List<LeaveData> result = new ArrayList<>();

        for (Leave leave : employee.getLeaveCalendar().getLeaves()) {
            LeaveData leaveData = new LeaveData();
            leaveData.setDate(leave.getDate().toString());
            leaveData.setType(leave.getType().name());
            leaveData.setStart(leave.getRange().getStart().toString());
            leaveData.setEnd(leave.getRange().getEnd().toString());
            leaveData.setNote(leave.getNote());
            result.add(leaveData);
        }

        return result;
    }

    private void restoreSkills(Employee employee, List<EmployeeSkillData> skillsData) {
        if (skillsData == null) {
            return;
        }

        for (EmployeeSkillData skillData : skillsData) {
            if (skillData == null) {
                continue;
            }

            Skill skill = Skill.valueOf(skillData.getSkill());
            Proficiency proficiency = Proficiency.valueOf(skillData.getProficiency());

            employee.getSkills().addOrUpdate(skill, proficiency);
        }
    }

    private void restoreWeeklyTimeOff(Employee employee, Map<String, List<TimeRangeData>> weeklyTimeOffData) {
        if (weeklyTimeOffData == null) {
            return;
        }

        for (Map.Entry<String, List<TimeRangeData>> entry : weeklyTimeOffData.entrySet()) {
            DayOfWeek day = DayOfWeek.valueOf(entry.getKey());
            List<TimeRangeData> ranges = entry.getValue();

            if (ranges == null) {
                continue;
            }

            for (TimeRangeData rangeData : ranges) {
                if (rangeData == null) {
                    continue;
                }

                employee.getAvailability().addTimeOff(day, toTimeRange(rangeData));
            }
        }
    }

    private void restoreFullDaysOff(Employee employee, List<String> fullDaysOffData) {
        if (fullDaysOffData == null) {
            return;
        }

        for (String dayValue : fullDaysOffData) {
            if (dayValue == null || dayValue.isBlank()) {
                continue;
            }

            DayOfWeek day = DayOfWeek.valueOf(dayValue);
            employee.getAvailability().addFullDayOff(day);
        }
    }

    private void restoreApprovedLeaves(Employee employee, List<LeaveData> leaveDataList) {
        if (leaveDataList == null) {
            return;
        }

        for (LeaveData leaveData : leaveDataList) {
            if (leaveData == null) {
                continue;
            }

            LocalDate date = LocalDate.parse(leaveData.getDate());

            TimeRange range = new TimeRange(
                    LocalTime.parse(leaveData.getStart()),
                    LocalTime.parse(leaveData.getEnd())
            );

            Leave leave = new Leave(
                    date,
                    range,
                    LeaveType.valueOf(leaveData.getType()),
                    leaveData.getNote()
            );

            employee.addLeave(leave);
        }
    }

    private TimeRangeData toTimeRangeData(TimeRange range) {
        return new TimeRangeData(
                range.getStart().toString(),
                range.getEnd().toString()
        );
    }

    private TimeRange toTimeRange(TimeRangeData data) {
        return new TimeRange(
                LocalTime.parse(data.getStart()),
                LocalTime.parse(data.getEnd())
        );
    }

    private void validateEmployeeData(EmployeeData data) {
        Objects.requireNonNull(data, "Employee data cannot be null");
        Objects.requireNonNull(data.getName(), "Employee name cannot be null");
        Objects.requireNonNull(data.getPriority(), "Employee priority cannot be null");
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");

        String normalized = name.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        return normalized;
    }
}