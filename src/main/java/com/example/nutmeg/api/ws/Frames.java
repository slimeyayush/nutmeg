package com.example.nutmeg.api.ws;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.nutmeg.api.json.Json;

/** Builders for the few non-STATE frames the server sends. */
final class Frames {

    private Frames() {}

    static String error(String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        return envelope("ERROR", payload);
    }

    static String of(String type, Object payload) {
        return envelope(type, payload);
    }

    private static String envelope(String type, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("payload", payload);
        return Json.write(envelope);
    }
}
