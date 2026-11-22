package com.texos.texos_backend.exception;

import org.springframework.http.HttpStatus;

public class KitchenAccessException extends RuntimeException {

    private final HttpStatus status;

    public KitchenAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
