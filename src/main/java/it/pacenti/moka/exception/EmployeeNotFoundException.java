package it.pacenti.moka.exception;

public class EmployeeNotFoundException extends MokaApplicationException {

    public EmployeeNotFoundException(String employeeName) {
        super("Employee not found: " + employeeName);
    }
}