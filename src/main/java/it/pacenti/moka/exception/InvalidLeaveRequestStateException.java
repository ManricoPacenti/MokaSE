package it.pacenti.moka.exception;

import it.pacenti.moka.availability.RequestStatus;

public class InvalidLeaveRequestStateException extends MokaApplicationException {

    public InvalidLeaveRequestStateException(int requestId, RequestStatus status) {
        super("Leave request " + requestId + " is not pending. Current status: " + status);
    }
}