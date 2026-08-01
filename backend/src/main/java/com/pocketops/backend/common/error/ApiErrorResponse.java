package com.pocketops.backend.common.error;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId
) {
}
