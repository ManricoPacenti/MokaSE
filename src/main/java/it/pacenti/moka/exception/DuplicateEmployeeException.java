package it.pacenti.moka.exception;

public class DuplicateEmployeeException extends MokaApplicationException {

    public DuplicateEmployeeException(String employeeName) {
        super("An employee with this name already exists: " + employeeName);
    }
}