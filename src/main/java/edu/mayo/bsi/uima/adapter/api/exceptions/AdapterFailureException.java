package edu.mayo.bsi.uima.adapter.api.exceptions;

public class AdapterFailureException extends Exception {

    public AdapterFailureException() {
        super();
    }

    public AdapterFailureException(String message) {
        super(message);
    }

    public AdapterFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdapterFailureException(Throwable cause) {
        super(cause);
    }
}
