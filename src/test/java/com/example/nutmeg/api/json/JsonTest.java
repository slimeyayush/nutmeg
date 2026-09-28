package com.example.nutmeg.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    public record Point(int x, String label, List<Integer> tags) {}

    @Test
    void writesRecordsMapsAndEscapes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("p", new Point(3, "a\"b\n", List.of(1, 2)));
        m.put("n", null);
        m.put("ok", true);
        assertEquals("{\"p\":{\"x\":3,\"label\":\"a\\\"b\\n\",\"tags\":[1,2]},\"n\":null,\"ok\":true}",
                Json.write(m));
    }

    @Test
    void roundTrips() {
        String text = "{\"type\":\"HELLO\",\"payload\":{\"roomCode\":\"PXLQ\",\"n\":42,\"f\":1.5,"
                + "\"u\":\"Ag\\u00fcero\",\"nil\":null,\"list\":[true,false]}}";
        Map<String, Object> frame = Json.parseObject(text);
        assertEquals("HELLO", frame.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) frame.get("payload");
        assertEquals("PXLQ", payload.get("roomCode"));
        assertEquals(42L, payload.get("n"));
        assertEquals(1.5, payload.get("f"));
        assertEquals("Agüero", payload.get("u"));
        assertNull(payload.get("nil"));
        assertEquals(List.of(true, false), payload.get("list"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":1} trailing"));
    }
}
