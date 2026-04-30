package com.example.sportsanalytics.sportradar.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

final class JsonNodes {
    private JsonNodes() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    static String textAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(field);
        }
        return textValue(current);
    }

    static String firstTextAt(JsonNode node, String[]... paths) {
        for (String[] path : paths) {
            String value = textAt(node, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static Double decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static boolean boolAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return false;
            }
            current = current.path(field);
        }
        if (current.isBoolean()) {
            return current.asBoolean();
        }
        return "true".equalsIgnoreCase(current.asText());
    }

    static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    static List<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return array(value);
    }

    static List<JsonNode> array(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<JsonNode> nodes = new ArrayList<>();
            value.forEach(nodes::add);
            return nodes;
        }
        if (value.isObject()) {
            for (String childName : List.of("event", "competitor", "player", "sport_event_timeline", "competitors")) {
                JsonNode child = value.path(childName);
                if (child.isArray()) {
                    return array(child);
                }
            }
        }
        return List.of(value);
    }

    static String textValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
