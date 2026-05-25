package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

@Schema(description = "Project-defined custom field value in display form.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomFieldValue(
        @Schema(description = "Custom field name as configured in the project.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "Customer code", nullable = true)
        String name,
        @Schema(description = "Effective values (single-valued fields produce one element).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<String> values
) {
    public static CustomFieldValue from(RedmineIssue.CustomField source) {
        if (source == null) {
            return null;
        }
        return new CustomFieldValue(source.name(), source.values());
    }

    public static List<CustomFieldValue> fromAll(List<RedmineIssue.CustomField> source) {
        if (source == null) {
            return null;
        }
        return ApiCollections.mapNonNull(source, CustomFieldValue::from);
    }
}
