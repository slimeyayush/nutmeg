package com.example.nutmeg.api.json;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately tiny JSON codec for the WebSocket layer.
 *
 * Why not Jackson? Spring Boot 3 ships Jackson 2 (com.fasterxml), Spring Boot 4
 * ships Jackson 3 (tools.jackson) with renamed APIs. The socket protocol is
 * small and fixed, so owning ~200 lines here means the game server compiles and
 * behaves identically on either. REST endpoints still use Spring's own Jackson.
 *
 * Writes: null, String, Number, Boolean, Enum, Map, Collection, arrays, records.
 * Reads:  objects, arrays, strings, numbers, booleans, null.
 */
public final class Json {

    private Json() {}

    // ================================================================= writing

    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence s) {
            writeString(out, s.toString());
        } else if (value instanceof Boolean b) {
            out.append(b ? "true" : "false");
        } else if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
            out.append("null");
        } else if (value instanceof Float f && (f.isNaN() || f.isInfinite())) {
            out.append("null");
        } else if (value instanceof Number n) {
            out.append(n.toString());
        } else if (value instanceof Enum<?> e) {
            writeString(out, e.name());
        } else if (value instanceof Map<?, ?> map) {
            writeMap(out, map);
        } else if (value instanceof Collection<?> collection) {
            writeCollection(out, collection);
        } else if (value instanceof Object[] array) {
            writeCollection(out, List.of(array));
        } else if (value.getClass().isRecord()) {
            writeRecord(out, value);
        } else {
            writeString(out, value.toString());
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeCollection(StringBuilder out, Collection<?> items) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, item);
        }
        out.append(']');
    }

    private static void writeRecord(StringBuilder out, Object record) {
        out.append('{');
        boolean first = true;
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object fieldValue;
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                fieldValue = accessor.invoke(record);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                throw new IllegalStateException("Cannot serialise " + component, ex);
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, component.getName());
            out.append(':');
            writeValue(out, fieldValue);
        }
        out.append('}');
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ================================================================= reading

    /** Parses any JSON value. Objects become LinkedHashMap, arrays ArrayList. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Trailing characters at " + parser.pos);
        }
        return value;
    }

    /** Parses a JSON object, or throws if the text is anything else. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {

        private static final int MAX_DEPTH = 32;

        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("Nested too deeply");
            }
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject(depth);
                case '[' -> readArray(depth);
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw new IllegalArgumentException("Unexpected '" + c + "' at " + pos);
                }
            };
        }

        Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException("Expected key at " + pos);
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue(depth + 1);
                map.put(key, value);
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == '}') {
                    return map;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected , or } at " + (pos - 1));
                }
            }
        }

        List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue(depth + 1));
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == ']') {
                    return list;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected , or ] at " + (pos - 1));
                }
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("Bad escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        if (pos + 4 > s.length()) {
                            throw new IllegalArgumentException("Bad unicode escape");
                        }
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape \\" + e);
                }
            }
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (!atEnd() && "0123456789.eE+-".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String token = s.substring(start, pos);
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.parseDouble(token);
            }
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(token);
            }
        }

        Object readLiteral(String word, Object value) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("Unexpected token at " + pos);
            }
            pos += word.length();
            return value;
        }

        char peek() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            }
            pos++;
        }
    }
}
