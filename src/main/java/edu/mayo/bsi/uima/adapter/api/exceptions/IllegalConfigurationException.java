package edu.mayo.bsi.uima.adapter.api.exceptions;

public class IllegalConfigurationException extends Exception {

    public IllegalConfigurationException() {
        super();
    }

    public IllegalConfigurationException(String message) {
        super(message);
    }

    public IllegalConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalConfigurationException(Throwable cause) {
        super(cause);
    }
}
