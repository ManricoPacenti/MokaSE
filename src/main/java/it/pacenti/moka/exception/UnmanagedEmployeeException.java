package it.pacenti.moka.exception;

public class UnmanagedEmployeeException extends MokaApplicationException {

    public UnmanagedEmployeeException(String employeeName) {
        super("Employee is not managed by this service: " + employeeName);
    }
}