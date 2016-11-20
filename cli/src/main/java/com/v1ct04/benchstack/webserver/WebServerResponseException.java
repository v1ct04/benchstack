package com.v1ct04.benchstack.webserver;

import java.io.Serializable;

public class WebServerResponseException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 2582084478175289907L;

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
