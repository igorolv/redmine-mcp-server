package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineCustomFieldValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class CustomFieldValuesParser {

    private final ObjectMapper mapper;

    public CustomFieldValuesParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RedmineCustomFieldValue> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("customFieldsJson must be a JSON object keyed by field ID");
            }
            var fields = new ArrayList<RedmineCustomFieldValue>();
            root.properties().forEach(entry -> fields.add(
                    new RedmineCustomFieldValue(parseFieldId(entry.getKey()), fieldValue(entry.getValue()))));
            return List.copyOf(fields);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("customFieldsJson is not valid JSON", e);
        }
    }

    private int parseFieldId(String key) {
        String normalized = key.startsWith("cf_") ? key.substring(3) : key;
        try {
            int id = Integer.parseInt(normalized);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid custom field key '%s'; expected a positive ID or cf_<ID>".formatted(key));
        }
    }

    private Object fieldValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isArray()) {
            var values = new ArrayList<String>();
            value.forEach(item -> {
                if (item.isObject() || item.isArray()) {
                    throw new IllegalArgumentException("Custom field arrays must contain scalar values");
                }
                values.add(item.isNull() ? "" : item.asString());
            });
            return List.copyOf(values);
        }
        if (value.isObject()) {
            throw new IllegalArgumentException("Custom field values must be scalars or arrays of scalars");
        }
        return value.asString();
    }
}
