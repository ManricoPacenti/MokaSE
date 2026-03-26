package it.pacenti.moka.app;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeSkill;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklySchedule;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;
import it.pacenti.moka.service.ManagerService;
import it.pacenti.moka.view.SchedulePrinter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ManagerConsoleController {

    private final ConsoleIO io;
    private final ManagerService managerService;
    private final SchedulePrinter schedulePrinter;

    private WeeklyScheduleTemplate currentTemplate;
    private WeeklySchedule currentSchedule;

    public ManagerConsoleController(ConsoleIO io, ManagerService managerService) {
        this.io = io;
        this.managerService = managerService;
        this.schedulePrinter = new SchedulePrinter();
    }

    public void run() {
        boolean running = true;

        io.printHeader("MOKA - MANAGER CONSOLE");

        while (running) {
            printMenu();
            int choice = io.readInt("Choose an option: ");

            try {
                switch (choice) {
                    case 1 -> createEmployee();
                    case 2 -> listEmployees();
                    case 3 -> addSkillToEmployee();
                    case 4 -> addWeeklyTimeOff();
                    case 5 -> createLeaveRequest();
                    case 6 -> viewPendingRequests();
                    case 7 -> processLeaveRequest();
                    case 8 -> createWeeklyTemplate();
                    case 9 -> addSlotToTemplate();
                    case 10 -> generateSchedule();
                    case 11 -> printCurrentSchedule();
                    case 12 -> {
                        running = false;
                        io.println("Exiting Moka Console. Goodbye.");
                    }
                    default -> io.println("Unknown option.");
                }
            } catch (Exception ex) {
                io.println("Operation failed: " + ex.getMessage());
            }

            if (running) {
                io.waitForEnter();
            }
        }
    }

    private void printMenu() {
        io.printSection("Main Menu");
        io.println("1.  Create employee");
        io.println("2.  List employees");
        io.println("3.  Add skill to employee");
        io.println("4.  Add weekly unavailability");
        io.println("5.  Create leave request");
        io.println("6.  View pending leave requests");
        io.println("7.  Approve / reject leave request");
        io.println("8.  Create weekly template");
        io.println("9.  Add slot to template");
        io.println("10. Generate weekly schedule");
        io.println("11. Print current schedule");
        io.println("12. Exit");
        io.println();
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

        List<Employee> employees = managerService.getAllEmployees()
                .stream()
                .sorted(Comparator.comparing(Employee::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (employees.isEmpty()) {
            io.println("No employees found.");
            return;
        }

        int index = 1;
        for (Employee employee : employees) {
            io.println(index++ + ". " + formatEmployee(employee));
        }
    }

    private void addSkillToEmployee() {
        io.printSection("Add Skill To Employee");

        Employee employee = selectEmployee();
        boolean adding = true;

        while (adding) {
            Skill skill = chooseSkill();
            Proficiency proficiency = chooseProficiency();

            managerService.addSkillToEmployee(employee.getName(), skill, proficiency);

            io.println("Skill updated successfully for " + employee.getName());
            adding = io.confirm("Add another skill to the same employee?");
        }
    }

    private void addWeeklyTimeOff() {
        io.printSection("Add Weekly Unavailability");

        Employee employee = selectEmployee();

        io.println("1. Add full day off");
        io.println("2. Add time range off");

        int choice = io.readInt("Choose unavailability type: ");
        DayOfWeek day = chooseDayOfWeek();

        switch (choice) {
            case 1 -> {
                managerService.addFullDayOff(employee.getName(), day);
                io.println("Full day off added successfully for " + employee.getName());
            }
            case 2 -> {
                LocalTime start = io.readTime("Start time");
                LocalTime end = io.readTime("End time");

                TimeRange range = new TimeRange(start, end);
                managerService.addWeeklyTimeOff(employee.getName(), day, range);

                io.println("Weekly time range off added successfully for " + employee.getName());
            }
            default -> throw new IllegalArgumentException("Invalid unavailability type selection.");
        }
    }

    private void createLeaveRequest() {
        io.printSection("Create Leave Request");

        Employee employee = selectEmployee();
        LocalDate date = io.readDate("Leave date");
        LocalTime start = io.readTime("Start time");
        LocalTime end = io.readTime("End time");
        LeaveType leaveType = chooseLeaveType();
        String note = io.readLine("Optional note (leave blank if none): ");

        TimeRange range = new TimeRange(start, end);
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

    private void processLeaveRequest() {
        io.printSection("Approve / Reject Leave Request");

        List<LeaveRequest> pendingRequests = managerService.getPendingRequests();

        if (pendingRequests.isEmpty()) {
            io.println("No pending requests.");
            return;
        }

        for (LeaveRequest request : pendingRequests) {
            io.println(formatLeaveRequest(request));
        }

        int requestId = io.readInt("Request id: ");
        boolean approve = io.confirm("Approve this request?");

        if (approve) {
            managerService.approveRequest(requestId);
            io.println("Request approved.");
        } else {
            managerService.rejectRequest(requestId);
            io.println("Request rejected.");
        }
    }

    private void createWeeklyTemplate() {
        io.printSection("Create Weekly Template");

        LocalDate weekStart = io.readDate("Week start date");
        currentTemplate = new WeeklyScheduleTemplate(weekStart);
        currentSchedule = null;

        io.println("Weekly template created for week starting " + weekStart);
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

            io.println("Slot added successfully to current template.");
            adding = io.confirm("Add another slot to the same template?");
        }
    }

    private void generateSchedule() {
        io.printSection("Generate Weekly Schedule");

        ensureTemplateExists();

        currentSchedule = managerService.generateSchedule(currentTemplate);

        io.println("Schedule generated successfully.");
    }

    private void printCurrentSchedule() {
        io.printSection("Current Schedule");

        if (currentSchedule == null) {
            io.println("No schedule generated yet.");
            return;
        }

        schedulePrinter.printWeeklyGrid(currentSchedule);
    }

    private Employee selectEmployee() {
        List<Employee> employees = managerService.getAllEmployees()
                .stream()
                .sorted(Comparator.comparing(Employee::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

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
            throw new IllegalStateException("Create a weekly template first.");
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

    private String formatEmployee(Employee employee) {
        StringBuilder builder = new StringBuilder();

        builder.append(employee.getName())
                .append(" | priority=").append(employee.getPriority())
                .append(" | agreedHours=").append(employee.getAgreedHours())
                .append(" | hourlyCost=").append(employee.getHourlyCost());

        String skillsText = employee.getSkills().asCollection()
                .stream()
                .map(this::formatSkill)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");

        builder.append(" | skills=").append(skillsText);
        builder.append(" | weeklyTimeOff=").append(formatWeeklyTimeOff(employee));

        return builder.toString();
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

    private String formatLeaveRequest(LeaveRequest request) {
        return "Request #" + request.getId()
                + " | employee=" + request.getEmployee().getName()
                + " | date=" + request.getLeave().getDate()
                + " | range=" + request.getLeave().getRange()
                + " | type=" + request.getLeave().getType()
                + " | status=" + request.getStatus();
    }
}