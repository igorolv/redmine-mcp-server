package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

@Schema(description = "File attached to a Redmine issue.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Attachment(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int id,
        @Schema(description = "Original file name as uploaded.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String filename,
        @Schema(description = "File size in bytes.", requiredMode = Schema.RequiredMode.REQUIRED)
        long filesize,
        @Schema(description = "MIME content type, may be null when Redmine has not detected it.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String contentType,
        @Schema(description = "Absolute URL to download the raw file contents (requires Redmine authentication).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String contentUrl,
        @Schema(description = "Optional caption supplied by the uploader.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String description,
        @Schema(description = "User who uploaded the attachment.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref author,
        @Schema(description = "Upload timestamp in ISO-8601 format.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", nullable = true)
        String createdOn
) {
    public static Attachment from(RedmineAttachment source) {
        if (source == null) {
            return null;
        }
        return new Attachment(
                source.id(),
                source.filename(),
                source.filesize(),
                source.contentType(),
                source.contentUrl(),
                source.description(),
                Ref.from(source.author()),
                source.createdOn()
        );
    }
}
