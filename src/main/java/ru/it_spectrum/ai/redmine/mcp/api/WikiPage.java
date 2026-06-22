package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPage;

import java.util.List;

@Schema(description = "Redmine wiki page. When returned from the index listing only metadata fields are populated; fetch a specific page to retrieve `text` and attachments.")
public record WikiPage(
        @Schema(description = "Page title (URL-escaped form used as the path).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String title,
        @Schema(description = "Page body in Textile or Markdown markup depending on the Redmine instance. Null in index listings.", nullable = true)
        String text,
        @Schema(description = "Monotonic revision number of the page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int version,
        @Schema(description = "Author of the most recent revision.", nullable = true)
        Ref author,
        @Schema(description = "Comment attached to the most recent revision.", nullable = true)
        String comments,
        @Schema(description = "When the page was first created, ISO-8601.", format = "date-time", nullable = true)
        String createdOn,
        @Schema(description = "When the page was last edited, ISO-8601.", format = "date-time", nullable = true)
        String updatedOn,
        @Schema(description = "Files attached to the page. Empty in index listings.", nullable = true)
        List<Attachment> attachments
) {
    public static WikiPage from(RedmineWikiPage source) {
        if (source == null) {
            return null;
        }
        var attachments = source.attachments() == null
                ? null
                : source.attachments().stream().map(Attachment::from).toList();
        return new WikiPage(
                source.title(),
                source.text(),
                source.version(),
                Ref.from(source.author()),
                source.comments(),
                source.createdOn(),
                source.updatedOn(),
                attachments
        );
    }
}
