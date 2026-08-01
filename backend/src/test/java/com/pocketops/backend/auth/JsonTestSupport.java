package com.pocketops.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonTestSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestSupport() {
    }

    public static String extractString(String json, String path) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        for (String part : path.split("\\.")) {
            node = node.get(part);
        }
        return node.asText();
    }
}
