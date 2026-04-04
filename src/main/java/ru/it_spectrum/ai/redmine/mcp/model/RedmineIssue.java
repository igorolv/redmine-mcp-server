package ru.it_spectrum.ai.redmine.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineIssue(
        int id,
        IdName project,
        IdName tracker,
        IdName status,
        IdName priority,
        IdName author,
        @JsonProperty("assigned_to") IdName assignedTo,
        IdName parent,
        @JsonProperty("fixed_version") IdName fixedVersion,
        IdName category,
        String subject,
        String description,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("due_date") String dueDate,
        @JsonProperty("done_ratio") int doneRatio,
        @JsonProperty("estimated_hours") Double estimatedHours,
        @JsonProperty("spent_hours") Double spentHours,
        @JsonProperty("is_private") boolean isPrivate,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn,
        @JsonProperty("custom_fields") List<CustomField> customFields,
        List<RedmineAttachment> attachments,
        List<Journal> journals,
        List<Relation> relations,
        List<Child> children
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Child(
            int id,
            IdName tracker,
            String subject
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomField(
            int id,
            String name,
            Boolean multiple,
            Object value
    ) {
        public boolean isMultiple() {
            return Boolean.TRUE.equals(multiple);
        }

        public List<String> values() {
            if (value == null) {
                return List.of();
            }

            var normalized = new ArrayList<String>();
            appendValues(normalized, value);
            return List.copyOf(normalized);
        }

        public boolean isEmpty() {
            return values().stream().allMatch(v -> v == null || v.isBlank());
        }

        public String displayValue() {
            return values().stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
        }

        private static void appendValues(List<String> target, Object rawValue) {
            if (rawValue == null) {
                return;
            }
            if (rawValue instanceof String text) {
                target.add(text);
                return;
            }
            if (rawValue instanceof Iterable<?> iterable) {
                for (var item : iterable) {
                    appendValues(target, item);
                }
                return;
            }
            if (rawValue.getClass().isArray()) {
                int length = Array.getLength(rawValue);
                for (int i = 0; i < length; i++) {
                    appendValues(target, Array.get(rawValue, i));
                }
                return;
            }
            target.add(rawValue.toString());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relation(
            int id,
            @JsonProperty("issue_id") int issueId,
            @JsonProperty("issue_to_id") int issueToId,
            @JsonProperty("relation_type") String relationType,
            Integer delay
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Journal(
            int id,
            IdName user,
            String notes,
            @JsonProperty("created_on") String createdOn,
            List<Detail> details
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            String property,
            String name,
            @JsonProperty("old_value") String oldValue,
            @JsonProperty("new_value") String newValue
    ) {
    }

    /** Wrapper for {"issue": {...}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Single(RedmineIssue issue) {
    }

    /** Wrapper for {"issues": [...], "total_count": N, "offset": N, "limit": N} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            List<RedmineIssue> issues,
            @JsonProperty("total_count") int totalCount,
            int offset,
            int limit
    ) {
    }
}
