package it.pacenti.moka.exception;

/**
 * Thrown when trying to assign an employee to a slot
 * that is already assigned.
 */
public class SlotAlreadyAssignedException extends RuntimeException {

    public SlotAlreadyAssignedException(String message) {
        super(message);
    }

}