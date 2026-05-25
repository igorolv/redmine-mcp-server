package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;

@Schema(description = "File attached to a Redmine issue.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Attachment(
        @Schema(description = "Attachment identifier.", requiredMode = Schema.RequiredMode.REQUIRED, example = "456")
        int id,
        @Schema(description = "Original file name as uploaded.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "specification.pdf", nullable = true)
        String filename,
        @Schema(description = "File size in bytes.", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345")
        long filesize,
        @Schema(description = "MIME content type, may be null when Redmine has not detected it.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "application/pdf", nullable = true)
        String contentType,
        @Schema(description = "Absolute URL to download the raw file contents (requires Redmine authentication).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "https://redmine.example.com/attachments/download/456/specification.pdf", nullable = true)
        String contentUrl,
        @Schema(description = "Optional caption supplied by the uploader.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String description,
        @Schema(description = "User who uploaded the attachment.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref author,
        @Schema(description = "Upload timestamp in ISO-8601 format.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, format = "date-time", example = "2024-12-31T10:15:30Z", nullable = true)
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
