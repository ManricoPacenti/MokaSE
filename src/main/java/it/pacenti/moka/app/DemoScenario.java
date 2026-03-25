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

    public DemoScenario(ManagerService managerService) {
        this.managerService = managerService;
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

        managerService.addSkill("Alice", Skill.OPENING, Proficiency.HIGH);
        managerService.addSkill("Alice", Skill.RESP, Proficiency.HIGH);
        managerService.addSkill("Alice", Skill.WAITER, Proficiency.MID);

        managerService.addSkill("Bruno", Skill.KITCHEN, Proficiency.HIGH);
        managerService.addSkill("Bruno", Skill.OPENING, Proficiency.MID);

        managerService.addSkill("Chiara", Skill.WAITER, Proficiency.HIGH);
        managerService.addSkill("Chiara", Skill.RUNNER, Proficiency.HIGH);
        managerService.addSkill("Chiara", Skill.BAR, Proficiency.MID);

        managerService.addSkill("Davide", Skill.BAR, Proficiency.MID);
        managerService.addSkill("Davide", Skill.WAITER, Proficiency.LOW);

        managerService.addSkill("Elena", Skill.RUNNER, Proficiency.MID);
        managerService.addSkill("Elena", Skill.BAR, Proficiency.LOW);

        printEmployeesSnapshot();
    }

    private void configureUnavailability() {
        printSection("3. Configuring weekly unavailability");

        TimeRange lunch = new TimeRange(LocalTime.of(10, 0), LocalTime.of(15, 0));
        TimeRange dinner = new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0));

        managerService.addUnavailability("Alice", DayOfWeek.SUNDAY, lunch);
        managerService.addUnavailability("Alice", DayOfWeek.SUNDAY, dinner);

        managerService.addUnavailability("Bruno", DayOfWeek.MONDAY, dinner);
        managerService.addUnavailability("Bruno", DayOfWeek.TUESDAY, dinner);

        managerService.addUnavailability("Chiara", DayOfWeek.WEDNESDAY, lunch);

        managerService.addUnavailability("Davide", DayOfWeek.MONDAY, lunch);
        managerService.addUnavailability("Davide", DayOfWeek.TUESDAY, lunch);
        managerService.addUnavailability("Davide", DayOfWeek.WEDNESDAY, lunch);
        managerService.addUnavailability("Davide", DayOfWeek.THURSDAY, lunch);

        managerService.addUnavailability("Elena", DayOfWeek.FRIDAY, dinner);
        managerService.addUnavailability("Elena", DayOfWeek.SATURDAY, dinner);

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

        LeaveRequest request = managerService.submitLeaveRequest("Chiara", chiaraLeave);
        System.out.println("Submitted leave request for Chiara. Request id: " + request.getId());

        managerService.approveLeaveRequest(request.getId());
        System.out.println("Approved leave request id: " + request.getId());

        printPendingRequestsSnapshot();
        printApprovedLeavesSnapshot();
    }

    private void createWeeklyTemplate() {
        printSection("5. Creating weekly template");

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        managerService.createTemplate(monday);

        TimeRange lunch = new TimeRange(LocalTime.of(10, 0), LocalTime.of(15, 0));
        TimeRange dinner = new TimeRange(LocalTime.of(18, 0), LocalTime.of(23, 0));

        // MONDAY
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.MONDAY, lunch, Skill.OPENING));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.MONDAY, lunch, Skill.KITCHEN));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.MONDAY, lunch, Skill.WAITER));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.MONDAY, dinner, Skill.RESP));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.MONDAY, dinner, Skill.KITCHEN));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.MONDAY, dinner, Skill.RUNNER));

        // TUESDAY
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.TUESDAY, lunch, Skill.OPENING));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.TUESDAY, lunch, Skill.BAR));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.TUESDAY, lunch, Skill.WAITER));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.TUESDAY, dinner, Skill.RESP));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.TUESDAY, dinner, Skill.KITCHEN));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.TUESDAY, dinner, Skill.RUNNER));

        // WEDNESDAY
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, lunch, Skill.OPENING));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, lunch, Skill.KITCHEN));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, lunch, Skill.WAITER));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, dinner, Skill.RESP));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, dinner, Skill.BAR));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.WEDNESDAY, dinner, Skill.RUNNER));

        // FRIDAY
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.FRIDAY, lunch, Skill.OPENING));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.FRIDAY, lunch, Skill.WAITER));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.FRIDAY, lunch, Skill.BAR));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.FRIDAY, dinner, Skill.RESP));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.FRIDAY, dinner, Skill.KITCHEN));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.FRIDAY, dinner, Skill.RUNNER));

        // SATURDAY
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.SATURDAY, lunch, Skill.OPENING));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.SATURDAY, lunch, Skill.WAITER));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.SATURDAY, lunch, Skill.RUNNER));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.SATURDAY, dinner, Skill.RESP));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.SATURDAY, dinner, Skill.KITCHEN));
        managerService.addTemplateSlot(new ShiftSlot(DayOfWeek.SATURDAY, dinner, Skill.BAR));

        printTemplateSnapshot();
    }

    private void generateAndPrintSchedule() {
        printSection("6. Generating weekly schedule");

        WeeklySchedule schedule = managerService.generateSchedule();
        System.out.println("Schedule generated successfully.");

        printScheduleSummary(schedule);
        printAssignmentsSnapshot(schedule);

        printSection("7. Printing weekly schedule grid");
        SchedulePrinter printer = new SchedulePrinter();
        printer.printWeeklyGrid(schedule);

        printSection("8. Coverage summary");
        printCoverageSummary(schedule);

        printSection("9. Unassigned slots");
        printUnassignedSlots(schedule);
    }

    private void printEmployeesSnapshot() {
        printSubSection("Employees snapshot");

        List<Employee> employees = managerService.getEmployees().stream()
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

        List<Employee> employees = managerService.getEmployees().stream()
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
                                .map(range -> formatRange(range))
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

        List<Employee> employees = managerService.getEmployees().stream()
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

        WeeklyScheduleTemplate template = managerService.getTemplate();

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

        List<Employee> employees = managerService.getEmployees().stream()
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