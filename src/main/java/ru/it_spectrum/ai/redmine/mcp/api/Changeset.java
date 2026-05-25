package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

@Schema(description = "Linked VCS commit / changeset associated with the issue. May be sourced from Redmine's repository integration or extracted from journal note URLs.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Changeset(
        @Schema(description = "Revision identifier as reported by the VCS. Lowercased when extracted from journal URLs (may be a short prefix).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String revision,
        @Schema(description = "Committer / author of the changeset. For `redmine`-sourced entries this is the VCS user mapping; for `comment_reference` entries this is the journal author.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref user,
        @Schema(description = "Commit message. Null for `comment_reference` entries — we don't try to parse free-form notes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String comments,
        @Schema(description = "Commit timestamp for `redmine`-sourced entries; journal creation time for `comment_reference` entries. ISO-8601.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
        String committedOn,
        @Schema(description = "Where the reference came from: `redmine` — from Redmine's repository integration; `comment_reference` — extracted from a commit URL inside a journal note.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allowableValues = {"redmine", "comment_reference"}, nullable = true)
        String source
) {
    public static final String SOURCE_REDMINE = "redmine";
    public static final String SOURCE_COMMENT_REFERENCE = "comment_reference";

    public static Changeset from(RedmineIssue.Changeset source) {
        if (source == null) {
            return null;
        }
        return new Changeset(source.revision(), Ref.from(source.user()),
                source.comments(), source.committedOn(), SOURCE_REDMINE);
    }

    public static List<Changeset> fromAll(List<RedmineIssue.Changeset> source) {
        if (source == null) {
            return null;
        }
        return ApiCollections.mapNonNull(source, Changeset::from);
    }
}
