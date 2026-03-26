package it.pacenti.moka.app;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Scanner;

public class ConsoleIO {

    private final Scanner scanner;

    public ConsoleIO() {
        this.scanner = new Scanner(System.in);
    }

    public void println() {
        System.out.println();
    }

    public void println(String message) {
        System.out.println(message);
    }

    public void print(String message) {
        System.out.print(message);
    }

    public void printHeader(String title) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(title);
        System.out.println("============================================================");
    }

    public void printSection(String title) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println(title);
        System.out.println("------------------------------------------------------------");
    }

    public String readLine(String prompt) {
        print(prompt);
        return scanner.nextLine().trim();
    }

    public int readInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readLine(prompt));
            } catch (NumberFormatException ex) {
                println("Invalid number. Please try again.");
            }
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            try {
                return LocalDate.parse(readLine(prompt + " [yyyy-MM-dd]: "));
            } catch (Exception ex) {
                println("Invalid date format. Please use yyyy-MM-dd.");
            }
        }
    }

    public LocalTime readTime(String prompt) {
        while (true) {
            try {
                return LocalTime.parse(readLine(prompt + " [HH:mm]: "));
            } catch (Exception ex) {
                println("Invalid time format. Please use HH:mm.");
            }
        }
    }

    public boolean confirm(String prompt) {
        while (true) {
            String value = readLine(prompt + " [y/n]: ").toLowerCase();

            if (value.equals("y") || value.equals("yes")) {
                return true;
            }
            if (value.equals("n") || value.equals("no")) {
                return false;
            }

            println("Please answer y or n.");
        }
    }

    public void waitForEnter() {
        readLine("Press ENTER to continue...");
    }
}