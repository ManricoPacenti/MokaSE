package it.pacenti.moka.exception;

public class LeaveRequestNotFoundException extends MokaApplicationException {

    public LeaveRequestNotFoundException(int requestId) {
        super("Leave request not found: " + requestId);
    }
}