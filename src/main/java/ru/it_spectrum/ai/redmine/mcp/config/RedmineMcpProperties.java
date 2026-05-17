package ru.it_spectrum.ai.redmine.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redmine-mcp")
public record RedmineMcpProperties(
        String dataDir,
        AttachmentExtraction attachment,
        FullContext fullContext
) {
    public static final int DEFAULT_ATTACHMENT_PREVIEW_LIMIT = 100_000;
    public static final int DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT = 10_000;
    public static final int DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT = 30_000;

    public RedmineMcpProperties {
        attachment = attachment != null
                ? attachment
                : new AttachmentExtraction(DEFAULT_ATTACHMENT_PREVIEW_LIMIT);
        fullContext = fullContext != null
                ? fullContext
                : new FullContext(
                        DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT,
                        DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT);
    }

    public RedmineMcpProperties(String dataDir) {
        this(dataDir, null, null);
    }

    public record AttachmentExtraction(
            int previewLimit
    ) {
        public AttachmentExtraction {
            if (previewLimit <= 0) {
                previewLimit = DEFAULT_ATTACHMENT_PREVIEW_LIMIT;
            }
        }
    }

    public record FullContext(
            int attachmentTextLimit,
            int totalAttachmentTextLimit
    ) {
        public FullContext {
            if (attachmentTextLimit < 0) {
                attachmentTextLimit = DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT;
            }
            if (totalAttachmentTextLimit < 0) {
                totalAttachmentTextLimit = DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT;
            }
        }
    }
}
