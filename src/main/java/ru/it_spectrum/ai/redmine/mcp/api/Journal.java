package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

@Schema(description = "Single history entry: a user comment, a set of field changes, or both.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Journal(
        @Schema(description = "Journal entry identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(description = "Author of the change.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref user,
        @Schema(description = "Free-text note. Empty when the entry only carries field changes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String notes,
        @Schema(description = "Timestamp the entry was recorded, ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
        String createdOn,
        @Schema(description = "Field-level changes recorded in this entry. Raw form — use issue history endpoints for resolved values.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Detail> details
) {
    public static Journal from(RedmineIssue.Journal source) {
        if (source == null) {
            return null;
        }
        return new Journal(source.id(), Ref.from(source.user()), source.notes(),
                source.createdOn(), Detail.fromAll(source.details()));
    }

    public static List<Journal> fromAll(List<RedmineIssue.Journal> source) {
        if (source == null) {
            return null;
        }
        return ApiCollections.mapNonNull(source, Journal::from);
    }
}
