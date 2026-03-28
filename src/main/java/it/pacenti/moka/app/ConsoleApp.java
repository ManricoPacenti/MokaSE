package it.pacenti.moka.app;

import it.pacenti.moka.employee.EmployeeFactory;
import it.pacenti.moka.persistence.json.JsonEmployeeRepository;
import it.pacenti.moka.persistence.json.JsonLeaveRequestRepository;
import it.pacenti.moka.persistence.json.JsonTemplateRepository;
import it.pacenti.moka.repository.EmployeeRepository;
import it.pacenti.moka.repository.LeaveRequestRepository;
import it.pacenti.moka.repository.TemplateRepository;
import it.pacenti.moka.scheduling.ShiftScheduler;
import it.pacenti.moka.service.ManagerService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ConsoleApp {

    public static void main(String[] args) {
        configureLogging();

        Path dataDirectory = Paths.get("data");

        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
            return;
        }

        EmployeeRepository employeeRepository =
                new JsonEmployeeRepository(dataDirectory.resolve("employees.json"));

        LeaveRequestRepository leaveRequestRepository =
                new JsonLeaveRequestRepository(
                        dataDirectory.resolve("leave-requests.json"),
                        employeeRepository
                );

        TemplateRepository templateRepository =
                new JsonTemplateRepository(dataDirectory.resolve("templates.json"));

        EmployeeFactory employeeFactory = new EmployeeFactory();
        ShiftScheduler shiftScheduler = new ShiftScheduler();

        ManagerService managerService = new ManagerService(
                employeeRepository,
                leaveRequestRepository,
                employeeFactory,
                shiftScheduler
        );

        ConsoleIO io = new ConsoleIO();
        ManagerConsoleController controller =
                new ManagerConsoleController(io, managerService, templateRepository);

        controller.run();
    }

    private static void configureLogging() {
        try {
            Path logDirectory = Paths.get("data", "logs");
            Files.createDirectories(logDirectory);

            Logger rootLogger = Logger.getLogger("");

            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            FileHandler fileHandler = new FileHandler(
                    logDirectory.resolve("moka.log").toString(),
                    true
            );
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);

            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(Level.INFO);

        } catch (IOException e) {
            System.err.println("Failed to configure file logging: " + e.getMessage());
        }
    }
}