package ru.it_spectrum.ai.redmine.mcp.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.AttachmentContentImagePartsCollapseStep;
import ru.it_spectrum.ai.redmine.mcp.compression.steps.AttachmentContentTextPartsTruncateStep;

import java.util.List;

/**
 * Applies the response-size pipeline to a single {@link AttachmentContent}
 * returned by {@code getAttachment}. Step order is cheap → costly:
 * image parts first, then text-part truncation.
 *
 * <p>When the caller passed explicit {@code maxChars}/{@code partLimit}, the text is already
 * bounded by those limits (capped by {@code attachment.max-request-chars}), so only image parts
 * are collapsed and the budget grows by the allowed text size. Truncating text below an explicit
 * request would silently override the caller.</p>
 */
@Service
public class AttachmentContentCompression {

    private final ResponseCompressor compressor;
    private final RedmineMcpProperties properties;

    public AttachmentContentCompression(ResponseCompressor compressor, RedmineMcpProperties properties) {
        this.compressor = compressor;
        this.properties = properties;
    }

    public AttachmentContent compress(AttachmentContent content) {
        return compress(content, false);
    }

    public AttachmentContent compress(AttachmentContent content, boolean explicitLimits) {
        if (content == null) {
            return null;
        }
        int budget = properties.response().maxChars();
        if (explicitLimits && content.limits() != null) {
            budget += content.limits().maxChars();
        }
        var result = compressor.fit(content, buildBudgetSteps(explicitLimits), budget);
        if (result.notes().isEmpty()) {
            return result.value();
        }
        return result.value().withCompressionNotes(result.notes());
    }

    List<CompressionStep<AttachmentContent>> buildBudgetSteps(boolean explicitLimits) {
        var response = properties.response();
        var collapseImages = new AttachmentContentImagePartsCollapseStep(response.imagePartsKeep());
        if (explicitLimits) {
            return List.of(collapseImages);
        }
        return List.of(
                collapseImages,
                new AttachmentContentTextPartsTruncateStep(response.attachmentTextPartChars())
        );
    }
}
