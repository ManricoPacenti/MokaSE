package it.pacenti.moka.app;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeSkill;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.exception.TemplateNotInitializedException;
import it.pacenti.moka.repository.TemplateRepository;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklySchedule;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;
import it.pacenti.moka.service.ManagerService;
import it.pacenti.moka.view.SchedulePrinter;
import it.pacenti.moka.view.TemplatePrinter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ManagerConsoleController {

    private final ConsoleIO io;
    private final ManagerService managerService;
    private final TemplateRepository templateRepository;
    private final SchedulePrinter schedulePrinter;
    private final TemplatePrinter templatePrinter;

    private WeeklyScheduleTemplate currentTemplate;
    private WeeklySchedule currentSchedule;
    private String currentTemplateName;

    public ManagerConsoleController(
            ConsoleIO io,
            ManagerService managerService,
            TemplateRepository templateRepository
    ) {
        this.io = io;
        this.managerService = managerService;
        this.templateRepository = templateRepository;
        this.schedulePrinter = new SchedulePrinter();
        this.templatePrinter = new TemplatePrinter();
    }

    public void run() {
        boolean running = true;

        io.printHeader("MOKA - MANAGER CONSOLE");

        while (running) {
            printMainMenu();
            int choice = io.readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1 -> runEmployeeManagementMenu();
                    case 2 -> runLeaveRequestManagementMenu();
                    case 3 -> runTemplateManagementMenu();
                    case 4 -> runScheduleManagementMenu();
                    case 5 -> {
                        running = false;
                        io.println("Exiting Moka Console. Goodbye.");
                    }
                    default -> io.println("Unknown option.");
                }
            } catch (Exception ex) {
                io.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void printMainMenu() {
        io.printSection("Main Menu");
        io.println("1. Employee management");
        io.println("2. Leave request management");
        io.println("3. Template management");
        io.println("4. Schedule management");
        io.println("5. Exit");
        io.println();
    }

    private void runEmployeeManagementMenu() {
        boolean back = false;

        while (!back) {
            io.printSection("Employee Management");
            io.println("1. Create employee");
            io.println("2. List employees");
            io.println("3. View employee details");
            io.println("4. Change employee priority");
            io.println("5. Delete employee");
            io.println("6. Add or update skill");
            io.println("7. Remove skill");
            io.println("8. Add weekly unavailability");
            io.println("9. Add full day off");
            io.println("10. Create leave request");
            io.println("11. Back");
            io.println();

            int choice = io.readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1 -> createEmployee();
                    case 2 -> listEmployees();
                    case 3 -> viewEmployeeDetails();
                    case 4 -> changeEmployeePriority();
                    case 5 -> deleteEmployee();
                    case 6 -> addOrUpdateSkillToEmployee();
                    case 7 -> removeSkillFromEmployee();
                    case 8 -> addWeeklyTimeOff();
                    case 9 -> addFullDayOff();
                    case 10 -> createLeaveRequest();
                    case 11 -> back = true;
                    default -> io.println("Unknown option.");
                }
            } catch (Exception ex) {
                io.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void runLeaveRequestManagementMenu() {
        boolean back = false;

        while (!back) {
            io.printSection("Leave Request Management");
            io.println("1. View pending leave requests");
            io.println("2. Approve leave request");
            io.println("3. Reject leave request");
            io.println("4. Back");
            io.println();

            int choice = io.readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1 -> viewPendingRequests();
                    case 2 -> approveLeaveRequest();
                    case 3 -> rejectLeaveRequest();
                    case 4 -> back = true;
                    default -> io.println("Unknown option.");
                }
            } catch (Exception ex) {
                io.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void runTemplateManagementMenu() {
        boolean back = false;

        while (!back) {
            io.printSection("Template Management");
            io.println("1. Create new template");
            io.println("2. View current template");
            io.println("3. Add slot to template");
            io.println("4. Remove slot from template");
            io.println("5. Clear template");
            io.println("6. Save current template");
            io.println("7. Load saved template");
            io.println("8. List saved templates");
            io.println("9. Delete saved template");
            io.println("10. Back");
            io.println();

            int choice = io.readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1 -> createWeeklyTemplate();
                    case 2 -> viewCurrentTemplate();
                    case 3 -> addSlotToTemplate();
                    case 4 -> removeSlotFromTemplate();
                    case 5 -> clearCurrentTemplate();
                    case 6 -> saveCurrentTemplate();
                    case 7 -> loadSavedTemplate();
                    case 8 -> listSavedTemplates();
                    case 9 -> deleteSavedTemplate();
                    case 10 -> back = true;
                    default -> io.println("Unknown option.");
                }
            } catch (Exception ex) {
                io.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void runScheduleManagementMenu() {
        boolean back = false;

        while (!back) {
            io.printSection("Schedule Management");
            io.println("1. Generate weekly schedule");
            io.println("2. Print current schedule");
            io.println("3. Back");
            io.println();

            int choice = io.readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1 -> generateSchedule();
                    case 2 -> printCurrentSchedule();
                    case 3 -> back = true;
                    default -> io.println("Unknown option.");
                }
            } catch (Exception ex) {
                io.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void createEmployee() {
        io.printSection("Create Employee");

        String name = io.readLine("Name: ");
        Priority priority = choosePriority();
        int agreedHours = io.readInt("Agreed weekly hours: ");
        int hourlyCost = io.readInt("Hourly cost: ");

        Employee employee = managerService.createEmployee(name, priority, agreedHours, hourlyCost);

        io.println("Employee created successfully: " + employee.getName());
    }

    private void listEmployees() {
        io.printSection("Employees");

        List<Employee> employees = getSortedEmployees();

        if (employees.isEmpty()) {
            io.println("No employees found.");
            return;
        }

        int index = 1;
        for (Employee employee : employees) {
            io.println(index++ + ". " + formatEmployeeSummary(employee));
        }
    }

    private void viewEmployeeDetails() {
        io.printSection("Employee Details");

        Employee employee = selectEmployee();

        io.println("Name: " + employee.getName());
        io.println("Priority: " + employee.getPriority());
        io.println("Agreed weekly hours: " + employee.getAgreedHours());
        io.println("Hourly cost: " + employee.getHourlyCost());
        io.println("Skills: " + formatSkills(employee));
        io.println("Weekly time off: " + formatWeeklyTimeOff(employee));
        io.println("Approved leaves: " + formatApprovedLeaves(employee));
    }

    private void changeEmployeePriority() {
        io.printSection("Change Employee Priority");

        Employee employee = selectEmployee();

        io.println("Current priority: " + employee.getPriority());
        Priority newPriority = choosePriority();

        managerService.changeEmployeePriority(employee.getName(), newPriority);

        io.println("Priority updated successfully for " + employee.getName());
    }

    private void deleteEmployee() {
        io.printSection("Delete Employee");

        Employee employee = selectEmployee();
        boolean confirmed = io.confirm(
                "Are you sure you want to permanently delete employee '" + employee.getName() + "'?"
        );

        if (!confirmed) {
            io.println("Operation cancelled.");
            return;
        }

        managerService.deleteEmployee(employee.getName());
        io.println("Employee deleted successfully.");
    }

    private void addOrUpdateSkillToEmployee() {
        io.printSection("Add Or Update Skill");

        Employee employee = selectEmployee();
        boolean editing = true;

        while (editing) {
            Skill skill = chooseSkill();
            Proficiency proficiency = chooseProficiency();

            managerService.addSkillToEmployee(employee.getName(), skill, proficiency);

            io.println("Skill saved successfully for " + employee.getName());
            editing = io.confirm("Add or update another skill for the same employee?");
        }
    }

    private void removeSkillFromEmployee() {
        io.printSection("Remove Skill");

        Employee employee = selectEmployee();

        List<EmployeeSkill> skills = employee.getSkills()
                .asCollection()
                .stream()
                .sorted(Comparator.comparing(skill -> skill.getSkill().name()))
                .toList();

        if (skills.isEmpty()) {
            io.println("This employee has no skills to remove.");
            return;
        }

        for (int i = 0; i < skills.size(); i++) {
            io.println((i + 1) + ". " + formatSkill(skills.get(i)));
        }

        int selected = io.readInt("Select skill number to remove: ");

        if (selected < 1 || selected > skills.size()) {
            throw new IllegalArgumentException("Invalid skill selection.");
        }

        Skill skillToRemove = skills.get(selected - 1).getSkill();
        managerService.removeSkillFromEmployee(employee.getName(), skillToRemove);

        io.println("Skill removed successfully from " + employee.getName());
    }

    private void addWeeklyTimeOff() {
        io.printSection("Add Weekly Unavailability");

        Employee employee = selectEmployee();
        DayOfWeek day = chooseDayOfWeek();
        LocalTime start = io.readTime("Start time");
        LocalTime end = io.readTime("End time");

        TimeRange range = new TimeRange(start, end);
        managerService.addWeeklyTimeOff(employee.getName(), day, range);

        io.println("Weekly time range off added successfully for " + employee.getName());
    }

    private void addFullDayOff() {
        io.printSection("Add Full Day Off");

        Employee employee = selectEmployee();
        DayOfWeek day = chooseDayOfWeek();

        managerService.addFullDayOff(employee.getName(), day);

        io.println("Full day off added successfully for " + employee.getName());
    }

    private void createLeaveRequest() {
        io.printSection("Create Leave Request");

        Employee employee = selectEmployee();
        LocalDate date = io.readDate("Leave date");

        io.println("Leave mode:");
        io.println("1. Full day leave");
        io.println("2. Time range leave");
        int leaveMode = io.readInt("Choose leave mode: ");

        TimeRange range;

        switch (leaveMode) {
            case 1 -> range = new TimeRange(LocalTime.of(0, 0), LocalTime.of(23, 59));
            case 2 -> {
                LocalTime start = io.readTime("Start time");
                LocalTime end = io.readTime("End time");
                range = new TimeRange(start, end);
            }
            default -> throw new IllegalArgumentException("Invalid leave mode selection.");
        }

        LeaveType leaveType = chooseLeaveType();
        String note = io.readLine("Optional note (leave blank if none): ");
        String normalizedNote = note.isBlank() ? "" : note;

        Leave leave = new Leave(date, range, leaveType, normalizedNote);
        LeaveRequest request = managerService.createLeaveRequest(employee.getName(), leave);

        io.println("Leave request created successfully. Request id: " + request.getId());
    }

    private void viewPendingRequests() {
        io.printSection("Pending Leave Requests");

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();

        if (pendingRequests.isEmpty()) {
            io.println("No pending requests.");
            return;
        }

        for (LeaveRequest request : pendingRequests) {
            io.println(formatLeaveRequest(request));
        }
    }

    private void approveLeaveRequest() {
        io.printSection("Approve Leave Request");

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();

        if (pendingRequests.isEmpty()) {
            io.println("No pending requests.");
            return;
        }

        for (LeaveRequest request : pendingRequests) {
            io.println(formatLeaveRequest(request));
        }

        int requestId = io.readInt("Request id to approve: ");
        managerService.approveRequest(requestId);

        io.println("Request approved.");
    }

    private void rejectLeaveRequest() {
        io.printSection("Reject Leave Request");

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();

        if (pendingRequests.isEmpty()) {
            io.println("No pending requests.");
            return;
        }

        for (LeaveRequest request : pendingRequests) {
            io.println(formatLeaveRequest(request));
        }

        int requestId = io.readInt("Request id to reject: ");
        managerService.rejectRequest(requestId);

        io.println("Request rejected.");
    }

    private void createWeeklyTemplate() {
        io.printSection("Create Weekly Template");

        LocalDate weekStart = io.readDate("Week start date");
        currentTemplate = new WeeklyScheduleTemplate(weekStart);
        currentTemplateName = null;
        currentSchedule = null;

        io.println("Weekly template created for week starting " + weekStart);
    }

    private void viewCurrentTemplate() {
        io.printSection("Current Template");

        ensureTemplateExists();

        if (currentTemplateName != null) {
            io.println("Current template name: " + currentTemplateName);
        }

        if (currentTemplate.isEmpty()) {
            io.println("Current template is empty.");
            return;
        }

        templatePrinter.printWeeklyGrid(currentTemplate);
    }

    private void addSlotToTemplate() {
        io.printSection("Add Slot To Current Template");

        ensureTemplateExists();

        boolean adding = true;

        while (adding) {
            DayOfWeek day = chooseDayOfWeek();
            LocalTime start = io.readTime("Start time");
            LocalTime end = io.readTime("End time");
            Skill skill = chooseSkill();

            ShiftSlot slot = new ShiftSlot(day, new TimeRange(start, end), skill);
            currentTemplate.addSlot(slot);
            currentSchedule = null;

            io.println("Slot added successfully to current template.");
            adding = io.confirm("Add another slot to the same template?");
        }
    }

    private void removeSlotFromTemplate() {
        io.printSection("Remove Slot From Current Template");

        ensureTemplateExists();

        if (currentTemplate.isEmpty()) {
            io.println("Current template is empty.");
            return;
        }

        List<ShiftSlot> sortedSlots = templatePrinter.getSortedSlots(currentTemplate);
        templatePrinter.printIndexedList(currentTemplate);

        int selected = io.readInt("Select slot number to remove: ");

        if (selected < 1 || selected > sortedSlots.size()) {
            throw new IllegalArgumentException("Invalid slot selection.");
        }

        ShiftSlot slotToRemove = sortedSlots.get(selected - 1);
        currentTemplate.removeSlot(slotToRemove);
        currentSchedule = null;

        io.println("Slot removed successfully.");
    }

    private void clearCurrentTemplate() {
        io.printSection("Clear Current Template");

        ensureTemplateExists();

        if (currentTemplate.isEmpty()) {
            io.println("Current template is already empty.");
            return;
        }

        boolean confirmed = io.confirm("Are you sure you want to clear the current template?");

        if (!confirmed) {
            io.println("Operation cancelled.");
            return;
        }

        currentTemplate.clear();
        currentSchedule = null;

        io.println("Current template cleared successfully.");
    }

    private void saveCurrentTemplate() {
        io.printSection("Save Current Template");

        ensureTemplateExists();

        String defaultHint = currentTemplateName == null ? "" : " [" + currentTemplateName + "]";
        String input = io.readLine("Template name" + defaultHint + ": ");

        String templateName;
        if (input.isBlank()) {
            if (currentTemplateName == null) {
                throw new IllegalArgumentException("Template name cannot be blank.");
            }
            templateName = currentTemplateName;
        } else {
            templateName = input.trim();
        }

        templateRepository.save(templateName, currentTemplate);
        currentTemplateName = templateName;

        io.println("Template saved successfully as: " + templateName);
    }

    private void loadSavedTemplate() {
        io.printSection("Load Saved Template");

        List<String> names = templateRepository.findAllNames();

        if (names.isEmpty()) {
            io.println("No saved templates found.");
            return;
        }

        for (int i = 0; i < names.size(); i++) {
            io.println((i + 1) + ". " + names.get(i));
        }

        int selected = io.readInt("Select template number to load: ");

        if (selected < 1 || selected > names.size()) {
            throw new IllegalArgumentException("Invalid template selection.");
        }

        String selectedName = names.get(selected - 1);

        WeeklyScheduleTemplate loadedTemplate = templateRepository.findByName(selectedName)
                .orElseThrow(() -> new IllegalStateException("Template not found: " + selectedName));

        currentTemplate = loadedTemplate;
        currentTemplateName = selectedName;
        currentSchedule = null;

        io.println("Template loaded successfully: " + selectedName);
    }

    private void listSavedTemplates() {
        io.printSection("Saved Templates");

        List<String> names = templateRepository.findAllNames();

        if (names.isEmpty()) {
            io.println("No saved templates found.");
            return;
        }

        for (int i = 0; i < names.size(); i++) {
            io.println((i + 1) + ". " + names.get(i));
        }
    }

    private void deleteSavedTemplate() {
        io.printSection("Delete Saved Template");

        List<String> names = templateRepository.findAllNames();

        if (names.isEmpty()) {
            io.println("No saved templates found.");
            return;
        }

        for (int i = 0; i < names.size(); i++) {
            io.println((i + 1) + ". " + names.get(i));
        }

        int selected = io.readInt("Select template number to delete: ");

        if (selected < 1 || selected > names.size()) {
            throw new IllegalArgumentException("Invalid template selection.");
        }

        String selectedName = names.get(selected - 1);
        boolean confirmed = io.confirm("Are you sure you want to delete template '" + selectedName + "'?");

        if (!confirmed) {
            io.println("Operation cancelled.");
            return;
        }

        boolean removed = templateRepository.deleteByName(selectedName);

        if (!removed) {
            io.println("Template not found.");
            return;
        }

        if (currentTemplateName != null && selectedName.equalsIgnoreCase(currentTemplateName)) {
            currentTemplateName = null;
        }

        io.println("Template deleted successfully.");
    }

    private void generateSchedule() {
        io.printSection("Generate Weekly Schedule");

        ensureTemplateExists();

        if (currentTemplate.isEmpty()) {
            throw new IllegalStateException("Current template is empty.");
        }

        if (managerService.getAllEmployees().isEmpty()) {
            throw new IllegalStateException("No employees available.");
        }

        currentSchedule = managerService.generateSchedule(currentTemplate);

        io.println("Schedule generated successfully.");
    }

    private void printCurrentSchedule() {
        io.printSection("Current Schedule");

        if (currentSchedule == null) {
            io.println("No schedule generated yet.");
            return;
        }

        if (currentTemplate != null) {
            schedulePrinter.printWeeklyGrid(currentSchedule, currentTemplate);
        } else {
            schedulePrinter.printWeeklyGrid(currentSchedule);
        }
    }

    private List<Employee> getSortedEmployees() {
        return managerService.getAllEmployees()
                .stream()
                .sorted(Comparator.comparing(Employee::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Employee selectEmployee() {
        List<Employee> employees = getSortedEmployees();

        if (employees.isEmpty()) {
            throw new IllegalStateException("No employees available.");
        }

        io.println("Available employees:");
        for (int i = 0; i < employees.size(); i++) {
            io.println((i + 1) + ". " + employees.get(i).getName());
        }

        int selected = io.readInt("Select employee number: ");

        if (selected < 1 || selected > employees.size()) {
            throw new IllegalArgumentException("Invalid employee selection.");
        }

        return employees.get(selected - 1);
    }

    private void ensureTemplateExists() {
        if (currentTemplate == null) {
            throw new TemplateNotInitializedException();
        }
    }

    private Priority choosePriority() {
        return chooseEnum("Priority", Priority.values());
    }

    private Skill chooseSkill() {
        return chooseEnum("Skill", Skill.values());
    }

    private Proficiency chooseProficiency() {
        return chooseEnum("Proficiency", Proficiency.values());
    }

    private LeaveType chooseLeaveType() {
        return chooseEnum("Leave type", LeaveType.values());
    }

    private DayOfWeek chooseDayOfWeek() {
        return chooseEnum("Day of week", DayOfWeek.values());
    }

    private <T extends Enum<T>> T chooseEnum(String label, T[] values) {
        io.println(label + ":");

        for (int i = 0; i < values.length; i++) {
            io.println((i + 1) + ". " + values[i].name());
        }

        int selected = io.readInt("Select " + label.toLowerCase() + ": ");

        if (selected < 1 || selected > values.length) {
            throw new IllegalArgumentException("Invalid " + label.toLowerCase() + " selection.");
        }

        return values[selected - 1];
    }

    private String formatEmployeeSummary(Employee employee) {
        return employee.getName()
                + " | priority=" + employee.getPriority()
                + " | agreedHours=" + employee.getAgreedHours()
                + " | hourlyCost=" + employee.getHourlyCost();
    }

    private String formatSkills(Employee employee) {
        if (employee.getSkills().isEmpty()) {
            return "none";
        }

        return employee.getSkills().asCollection()
                .stream()
                .map(this::formatSkill)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private String formatSkill(EmployeeSkill skill) {
        return skill.getSkill().name() + ":" + skill.getProficiency().name();
    }

    private String formatWeeklyTimeOff(Employee employee) {
        Map<DayOfWeek, List<TimeRange>> timeOff = employee.getAvailability().getTimeOff();

        StringBuilder builder = new StringBuilder();
        boolean hasContent = false;

        if (!employee.getAvailability().getFullDaysOff().isEmpty()) {
            builder.append("fullDays=");
            String fullDays = employee.getAvailability().getFullDaysOff()
                    .stream()
                    .map(DayOfWeek::name)
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            builder.append(fullDays);
            hasContent = true;
        }

        if (!timeOff.isEmpty()) {
            if (hasContent) {
                builder.append(" | ");
            }

            boolean firstDay = true;
            for (Map.Entry<DayOfWeek, List<TimeRange>> entry : timeOff.entrySet()) {
                if (!firstDay) {
                    builder.append(" | ");
                }
                firstDay = false;

                builder.append(entry.getKey().name()).append("=");

                String ranges = entry.getValue()
                        .stream()
                        .map(TimeRange::toString)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");

                builder.append(ranges);
            }

            hasContent = true;
        }

        if (!hasContent) {
            return "none";
        }

        return builder.toString();
    }

    private String formatApprovedLeaves(Employee employee) {
        if (employee.getLeaveCalendar().getLeaves().isEmpty()) {
            return "none";
        }

        return employee.getLeaveCalendar().getLeaves()
                .stream()
                .map(leave -> {
                    String note = leave.getNote();
                    String notePart = (note == null || note.isBlank()) ? "" : " | note=" + note;

                    return leave.getDate()
                            + " " + leave.getRange()
                            + " " + leave.getType()
                            + notePart;
                })
                .sorted()
                .reduce((a, b) -> a + " | " + b)
                .orElse("none");
    }

    private String formatLeaveRequest(LeaveRequest request) {
        String note = request.getLeave().getNote();
        String notePart = (note == null || note.isBlank()) ? "" : " | note=" + note;

        return "Request #" + request.getId()
                + " | employee=" + request.getEmployee().getName()
                + " | date=" + request.getLeave().getDate()
                + " | range=" + request.getLeave().getRange()
                + " | type=" + request.getLeave().getType()
                + " | status=" + request.getStatus()
                + notePart;
    }
}