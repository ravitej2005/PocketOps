package com.pocketops.backend.common.error;

public final class RequestIdContext {
    public static final String ATTRIBUTE = "pocketops.requestId";
    public static final String HEADER = "X-Request-Id";

    private RequestIdContext() {
    }
}
