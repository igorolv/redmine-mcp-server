package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

@Schema(description = "Single field-level change inside a journal entry. Values are raw Redmine identifiers when the property is `attr` (e.g. status_id), or raw text otherwise.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Detail(
        @Schema(description = "Property kind.", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allowableValues = {"attr", "cf", "relation", "attachment"}, nullable = true)
        String property,
        @Schema(description = "Property name (e.g. status_id, subject) when property is `attr`; custom field id when property is `cf`.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String name,
        @Schema(description = "Previous raw value, null on creation.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String oldValue,
        @Schema(description = "New raw value, null on deletion.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String newValue
) {
    public static Detail from(RedmineIssue.Detail source) {
        if (source == null) {
            return null;
        }
        return new Detail(source.property(), source.name(), source.oldValue(), source.newValue());
    }

    public static List<Detail> fromAll(List<RedmineIssue.Detail> source) {
        if (source == null) {
            return null;
        }
        return ApiCollections.mapNonNull(source, Detail::from);
    }
}
