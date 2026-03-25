package it.pacenti.moka.exception;

public class TemplateNotInitializedException extends MokaApplicationException {

    public TemplateNotInitializedException() {
        super("Weekly schedule template has not been created yet");
    }
}