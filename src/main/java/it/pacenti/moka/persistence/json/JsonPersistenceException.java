package it.pacenti.moka.persistence.json;

public class JsonPersistenceException extends RuntimeException {

    public JsonPersistenceException(String message) {
        super(message);
    }

    public JsonPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}