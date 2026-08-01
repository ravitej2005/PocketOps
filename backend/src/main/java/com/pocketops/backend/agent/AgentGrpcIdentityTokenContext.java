package com.pocketops.backend.agent;

final class AgentGrpcIdentityTokenContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private AgentGrpcIdentityTokenContext() {
    }

    static void set(String identityToken) {
        CURRENT.set(identityToken);
    }

    static String current() {
        return CURRENT.get();
    }

    static void clear() {
        CURRENT.remove();
    }
}
