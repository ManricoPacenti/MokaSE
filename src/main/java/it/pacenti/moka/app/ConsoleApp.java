package it.pacenti.moka.app;

import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.InMemoryEmployeeRepository;
import it.pacenti.moka.repository.InMemoryLeaveRequestRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;
import it.pacenti.moka.scheduling.ShiftScheduler;
import it.pacenti.moka.service.ManagerService;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsoleApp {

    public static void main(String[] args) {
        silenceConsoleLogging();

        EmployeeRepository employeeRepository = new InMemoryEmployeeRepository();
        LeaveRequestRepository leaveRequestRepository = new InMemoryLeaveRequestRepository();
        EmployeeFactory employeeFactory = new EmployeeFactory();
        ShiftScheduler shiftScheduler = new ShiftScheduler();

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                employeeFactory
        );

        DemoScenario demoScenario = new DemoScenario(managerService, shiftScheduler);
        demoScenario.run();
    }

    private static void silenceConsoleLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE);

        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.SEVERE);
        }
    }
}