package com.v1ct04.benchstack.webserver;

public class WebServerResponseException extends RuntimeException {

    public WebServerResponseException(String path, String error) {
        super(buildMessage(path, error));
    }

    public WebServerResponseException(String path, String error, Throwable cause) {
        super(buildMessage(path, error), cause);
    }

    private static String buildMessage(String path, String error) {
        return "Server response error at " + path + ": " + error;
    }
}
