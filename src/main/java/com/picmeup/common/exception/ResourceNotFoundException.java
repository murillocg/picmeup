package com.picmeup.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("%s not found: %s".formatted(resourceType, identifier));
    }
}
