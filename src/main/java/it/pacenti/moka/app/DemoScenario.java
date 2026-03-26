package it.pacenti.moka.app;

import it.pacenti.moka.availability.Leave;
import it.pacenti.moka.availability.LeaveRequest;
import it.pacenti.moka.availability.LeaveType;
import it.pacenti.moka.employee.Employee;
import it.pacenti.moka.employee.EmployeeSkill;
import it.pacenti.moka.employee.Priority;
import it.pacenti.moka.employee.Proficiency;
import it.pacenti.moka.employee.Skill;
import it.pacenti.moka.scheduling.Assignment;
import it.pacenti.moka.scheduling.ShiftScheduler;
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

public class DemoScenario {

    private final ManagerService managerService;
    private final ShiftScheduler shiftScheduler;

    private WeeklyScheduleTemplate template;
    private WeeklySchedule currentSchedule;

    public DemoScenario(ManagerService managerService, ShiftScheduler shiftScheduler) {
        this.managerService = managerService;
        this.shiftScheduler = shiftScheduler;
    }

    public void run() {
        printHeader("MOKA - CONSOLE DEMO");

        createEmployees();
        assignSkills();
        configureUnavailability();
        manageLeaveRequests();
        createWeeklyTemplate();
        generateAndPrintSchedule();

        printHeader("DEMO COMPLETED");
    }

    private void createEmployees() {
        printSection("1. Creating employees");

        managerService.createEmployee("Alice", Priority.HIGH, 40, 18);
        managerService.createEmployee("Bruno", Priority.MEDIUM, 36, 15);
        managerService.createEmployee("Chiara", Priority.HIGH, 30, 16);
        managerService.createEmployee("Davide", Priority.LOW, 24, 14);
        managerService.createEmployee("Elena", Priority.MEDIUM, 20, 13);

        printEmployeesSnapshot();
    }

    private void assignSkills() {
        printSection("2. Assigning skills");

        Employee alice = getEmployee("Alice");
        alice.getSkills().addOrUpdate(Skill.OPENING, Proficiency.HIGH);
        alice.getSkills().addOrUpdate(Skill.RESP, Proficiency.HIGH);
        alice.getSkills().addOrUpdate(Skill.WAITER, Proficiency.MID);

        Employee bruno = getEmployee("Bruno");
        bruno.getSkills().addOrUpdate(Skill.KITCHEN, Proficiency.HIGH);
        bruno.getSkills().addOrUpdate(Skill.OPENING, Proficiency.MID);

        Employee chiara = getEmployee("Chiara");
        chiara.getSkills().addOrUpdate(Skill.WAITER, Proficiency.HIGH);
        chiara.getSkills().addOrUpdate(Skill.RUNNER, Proficiency.HIGH);
        chiara.getSkills().addOrUpdate(Skill.BAR, Proficiency.MID);

        Employee davide = getEmployee("Davide");
        davide.getSkills().addOrUpdate(Skill.BAR, Proficiency.MID);
        davide.getSkills().addOrUpdate(Skill.WAITER, Proficiency.LOW);

        Employee elena = getEmployee("Elena");
        elena.getSkills().addOrUpdate(Skill.RUNNER, Proficiency.MID);
        elena.getSkills().addOrUpdate(Skill.BAR, Proficiency.LOW);

        saveAll(alice, bruno, chiara, davide, elena);

        printEmployeesSnapshot();
    }

    private void configureUnavailability() {
        printSection("3. Configuring weekly unavailability");

        TimeRange lunch = new TimeRange(LocalTime.of(10, 0), LocalTime.of(15, 0));
        TimeRange dinner = new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0));

        Employee alice = getEmployee("Alice");
        alice.getAvailability().addTimeOff(DayOfWeek.SUNDAY, lunch);
        alice.getAvailability().addTimeOff(DayOfWeek.SUNDAY, dinner);

        Employee bruno = getEmployee("Bruno");
        bruno.getAvailability().addTimeOff(DayOfWeek.MONDAY, dinner);
        bruno.getAvailability().addTimeOff(DayOfWeek.TUESDAY, dinner);

        Employee chiara = getEmployee("Chiara");
        chiara.getAvailability().addTimeOff(DayOfWeek.WEDNESDAY, lunch);

        Employee davide = getEmployee("Davide");
        davide.getAvailability().addTimeOff(DayOfWeek.MONDAY, lunch);
        davide.getAvailability().addTimeOff(DayOfWeek.TUESDAY, lunch);
        davide.getAvailability().addTimeOff(DayOfWeek.WEDNESDAY, lunch);
        davide.getAvailability().addTimeOff(DayOfWeek.THURSDAY, lunch);

        Employee elena = getEmployee("Elena");
        elena.getAvailability().addTimeOff(DayOfWeek.FRIDAY, dinner);
        elena.getAvailability().addTimeOff(DayOfWeek.SATURDAY, dinner);

        saveAll(alice, bruno, chiara, davide, elena);

        printUnavailabilitySnapshot();
    }

    private void manageLeaveRequests() {
        printSection("4. Managing leave requests");

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        TimeRange lunch = new TimeRange(LocalTime.of(10, 0), LocalTime.of(15, 0));

        Leave chiaraLeave = new Leave(
                monday.plusDays(2),
                lunch,
                LeaveType.VACATION,
                "One lunch shift off for personal rest"
        );

        LeaveRequest request = managerService.createLeaveRequest("Chiara", chiaraLeave);
        System.out.println("Submitted leave request for Chiara. Request id: " + request.getId());

        managerService.approveRequest(request.getId());
        System.out.println("Approved leave request id: " + request.getId());

        printPendingRequestsSnapshot();
        printApprovedLeavesSnapshot();
    }

    private void createWeeklyTemplate() {
        printSection("5. Creating weekly template");

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        template = new WeeklyScheduleTemplate(monday);

        TimeRange lunch = new TimeRange(LocalTime.of(10, 0), LocalTime.of(15, 0));
        TimeRange dinner = new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0));

        // MONDAY
        template.addSlot(new ShiftSlot(DayOfWeek.MONDAY, lunch, Skill.OPENING));
        template.addSlot(new ShiftSlot(DayOfWeek.MONDAY, lunch, Skill.KITCHEN));
        template.addSlot(new ShiftSlot(DayOfWeek.MONDAY, lunch, Skill.WAITER));
        template.addSlot(new ShiftSlot(DayOfWeek.MONDAY, dinner, Skill.RESP));
        template.addSlot(new ShiftSlot(DayOfWeek.MONDAY, dinner, Skill.KITCHEN));
        template.addSlot(new ShiftSlot(DayOfWeek.MONDAY, dinner, Skill.RUNNER));

        // TUESDAY
        template.addSlot(new ShiftSlot(DayOfWeek.TUESDAY, lunch, Skill.OPENING));
        template.addSlot(new ShiftSlot(DayOfWeek.TUESDAY, lunch, Skill.BAR));
        template.addSlot(new ShiftSlot(DayOfWeek.TUESDAY, lunch, Skill.WAITER));
        template.addSlot(new ShiftSlot(DayOfWeek.TUESDAY, dinner, Skill.RESP));
        template.addSlot(new ShiftSlot(DayOfWeek.TUESDAY, dinner, Skill.KITCHEN));
        template.addSlot(new ShiftSlot(DayOfWeek.TUESDAY, dinner, Skill.RUNNER));

        // WEDNESDAY
        template.addSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, lunch, Skill.OPENING));
        template.addSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, lunch, Skill.KITCHEN));
        template.addSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, lunch, Skill.WAITER));
        template.addSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, dinner, Skill.RESP));
        template.addSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, dinner, Skill.BAR));
        template.addSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, dinner, Skill.RUNNER));

        // FRIDAY
        template.addSlot(new ShiftSlot(DayOfWeek.FRIDAY, lunch, Skill.OPENING));
        template.addSlot(new ShiftSlot(DayOfWeek.FRIDAY, lunch, Skill.WAITER));
        template.addSlot(new ShiftSlot(DayOfWeek.FRIDAY, lunch, Skill.BAR));
        template.addSlot(new ShiftSlot(DayOfWeek.FRIDAY, dinner, Skill.RESP));
        template.addSlot(new ShiftSlot(DayOfWeek.FRIDAY, dinner, Skill.KITCHEN));
        template.addSlot(new ShiftSlot(DayOfWeek.FRIDAY, dinner, Skill.RUNNER));

        // SATURDAY
        template.addSlot(new ShiftSlot(DayOfWeek.SATURDAY, lunch, Skill.OPENING));
        template.addSlot(new ShiftSlot(DayOfWeek.SATURDAY, lunch, Skill.WAITER));
        template.addSlot(new ShiftSlot(DayOfWeek.SATURDAY, lunch, Skill.RUNNER));
        template.addSlot(new ShiftSlot(DayOfWeek.SATURDAY, dinner, Skill.RESP));
        template.addSlot(new ShiftSlot(DayOfWeek.SATURDAY, dinner, Skill.KITCHEN));
        template.addSlot(new ShiftSlot(DayOfWeek.SATURDAY, dinner, Skill.BAR));

        printTemplateSnapshot();
    }

    private void generateAndPrintSchedule() {
        printSection("6. Generating weekly schedule");

        currentSchedule = shiftScheduler.generateSchedule(
                template,
                managerService.getAllEmployees()
        );

        System.out.println("Schedule generated successfully.");

        printScheduleSummary(currentSchedule);
        printAssignmentsSnapshot(currentSchedule);

        printSection("7. Printing weekly schedule grid");
        SchedulePrinter printer = new SchedulePrinter();
        printer.printWeeklyGrid(currentSchedule);

        printSection("8. Coverage summary");
        printCoverageSummary(currentSchedule);

        printSection("9. Unassigned slots");
        printUnassignedSlots(currentSchedule);
    }

    private void printEmployeesSnapshot() {
        printSubSection("Employees snapshot");

        List<Employee> employees = managerService.getAllEmployees().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();

        for (Employee employee : employees) {
            System.out.println("- " + employee.getName()
                    + " | priority=" + employee.getPriority()
                    + " | agreedHours=" + employee.getAgreedHours()
                    + " | hourlyCost=" + employee.getHourlyCost());

            if (employee.getSkills().asCollection().isEmpty()) {
                System.out.println("  skills: none");
            } else {
                String skills = employee.getSkills().asCollection().stream()
                        .sorted(Comparator.comparing(s -> s.getSkill().name()))
                        .map(this::formatSkill)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                System.out.println("  skills: " + skills);
            }
        }
    }

    private void printUnavailabilitySnapshot() {
        printSubSection("Weekly unavailability snapshot");

        List<Employee> employees = managerService.getAllEmployees().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();

        for (Employee employee : employees) {
            System.out.println("- " + employee.getName());

            Map<DayOfWeek, List<TimeRange>> timeOff = employee.getAvailability().getTimeOff();

            if (timeOff.isEmpty()) {
                System.out.println("  no recurring unavailability");
                continue;
            }

            timeOff.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String ranges = entry.getValue().stream()
                                .sorted(Comparator.comparing(TimeRange::getStart))
                                .map(this::formatRange)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                        System.out.println("  " + entry.getKey() + ": " + ranges);
                    });
        }
    }

    private void printPendingRequestsSnapshot() {
        printSubSection("Pending leave requests");

        List<LeaveRequest> pending = managerService.getPendingRequests();

        if (pending.isEmpty()) {
            System.out.println("No pending requests.");
            return;
        }

        for (LeaveRequest request : pending) {
            System.out.println("- " + request);
        }
    }

    private void printApprovedLeavesSnapshot() {
        printSubSection("Approved leaves snapshot");

        List<Employee> employees = managerService.getAllEmployees().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();

        for (Employee employee : employees) {
            if (employee.getLeaveCalendar().getLeaves().isEmpty()) {
                continue;
            }

            System.out.println("- " + employee.getName());
            employee.getLeaveCalendar().getLeaves().forEach(leave ->
                    System.out.println("  " + leave.getDate()
                            + " | " + formatRange(leave.getRange())
                            + " | " + leave.getType()
                            + " | note=" + leave.getNote())
            );
        }
    }

    private void printTemplateSnapshot() {
        printSubSection("Template snapshot");

        if (template == null) {
            System.out.println("No template initialized.");
            return;
        }

        System.out.println("Week start: " + template.getWeekStart());
        System.out.println("Total template slots: " + template.getSlots().size());

        List<ShiftSlot> slots = template.getSlots().stream()
                .sorted(Comparator
                        .comparing(ShiftSlot::getDay)
                        .thenComparing(slot -> slot.getRange().getStart())
                        .thenComparing(slot -> slot.getRequiredSkill().name()))
                .toList();

        for (ShiftSlot slot : slots) {
            System.out.println("- "
                    + slot.getDay()
                    + " | "
                    + formatRange(slot.getRange())
                    + " | "
                    + slot.getRequiredSkill());
        }
    }

    private void printScheduleSummary(WeeklySchedule schedule) {
        printSubSection("Schedule summary by employee");

        List<Employee> employees = managerService.getAllEmployees().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();

        for (Employee employee : employees) {
            double assignedHours = schedule.getAssignedHours(employee);
            double remainingHours = schedule.getRemainingHours(employee);

            System.out.printf(
                    "- %s | assigned=%.1f h | remaining=%.1f h%n",
                    employee.getName(),
                    assignedHours,
                    remainingHours
            );
        }
    }

    private void printAssignmentsSnapshot(WeeklySchedule schedule) {
        printSubSection("Assignments snapshot");

        List<Assignment> assignments = schedule.getAssignments().stream()
                .sorted(Comparator
                        .comparing((Assignment a) -> a.getSlot().getDay())
                        .thenComparing(a -> a.getSlot().getRange().getStart())
                        .thenComparing(a -> a.getSlot().getRequiredSkill().name()))
                .toList();

        for (Assignment assignment : assignments) {
            ShiftSlot slot = assignment.getSlot();
            System.out.println("- "
                    + slot.getDay()
                    + " | "
                    + formatRange(slot.getRange())
                    + " | "
                    + slot.getRequiredSkill()
                    + " -> "
                    + assignment.getEmployee().getName());
        }
    }

    private void printCoverageSummary(WeeklySchedule schedule) {
        int totalTemplateSlots = schedule.getSlots().size();
        int assignedSlots = schedule.getAssignments().size();
        int unassignedSlots = schedule.getUnassignedSlots().size();

        System.out.println("Template slots:   " + totalTemplateSlots);
        System.out.println("Assigned slots:   " + assignedSlots);
        System.out.println("Unassigned slots: " + unassignedSlots);

        double coverage = totalTemplateSlots == 0
                ? 0.0
                : (assignedSlots * 100.0) / totalTemplateSlots;

        System.out.printf("Coverage: %.1f%%%n", coverage);
    }

    private void printUnassignedSlots(WeeklySchedule schedule) {
        List<ShiftSlot> unassigned = schedule.getUnassignedSlots().stream()
                .sorted(Comparator
                        .comparing(ShiftSlot::getDay)
                        .thenComparing(slot -> slot.getRange().getStart())
                        .thenComparing(slot -> slot.getRequiredSkill().name()))
                .toList();

        if (unassigned.isEmpty()) {
            System.out.println("No unassigned slots.");
            return;
        }

        for (ShiftSlot slot : unassigned) {
            System.out.println("- "
                    + slot.getDay()
                    + " | "
                    + formatRange(slot.getRange())
                    + " | "
                    + slot.getRequiredSkill());
        }
    }

    private Employee getEmployee(String name) {
        return managerService.findEmployee(name)
                .orElseThrow(() -> new IllegalStateException("Employee not found in demo: " + name));
    }

    private void saveAll(Employee... employees) {
        for (Employee employee : employees) {
            managerService.saveEmployee(employee);
            // serve un alias di compatibilità oppure un metodo saveEmployee nel service;
            // qui usiamo il repository solo se vuoi tenere il service minimale
            // vedi nota sotto
        }
    }

    private String formatSkill(EmployeeSkill skill) {
        return skill.getSkill() + "(" + skill.getProficiency() + ")";
    }

    private String formatRange(TimeRange range) {
        return range.getStart() + " - " + range.getEnd();
    }

    private void printHeader(String title) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(title);
        System.out.println("============================================================");
        System.out.println();
    }

    private void printSection(String title) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println(title);
        System.out.println("------------------------------------------------------------");
    }

    private void printSubSection(String title) {
        System.out.println(title + ":");
    }
}