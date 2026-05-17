package ru.it_spectrum.ai.redmine.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "redmine-mcp")
public record RedmineMcpProperties(
        String dataDir,
        AttachmentExtraction attachment,
        FullContext fullContext,
        Pagination pagination,
        Tree tree,
        Analysis analysis,
        Extraction extraction
) {
    public static final String DEFAULT_DATA_DIR_NAME = ".redmine-mcp-server";
    public static final int DEFAULT_ATTACHMENT_PREVIEW_LIMIT = 100_000;
    public static final int DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT = 10_000;
    public static final int DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT = 30_000;
    public static final int DEFAULT_FULL_CONTEXT_MAX_SIBLINGS = 20;
    public static final int DEFAULT_FULL_CONTEXT_MAX_CHILDREN = 20;
    public static final int DEFAULT_FULL_CONTEXT_MAX_RELATED = 10;
    public static final int DEFAULT_FULL_CONTEXT_MAX_RECENT_NOTES = 10;
    public static final int DEFAULT_FULL_CONTEXT_MAX_NOTE_LENGTH = 500;
    public static final int DEFAULT_PAGE_LIMIT = 25;
    public static final int DEFAULT_PAGE_OFFSET = 0;
    public static final int DEFAULT_MEMBERS_PAGE_LIMIT = 100;
    public static final int DEFAULT_TREE_DEPTH = 2;
    public static final int DEFAULT_TREE_MAX_DEPTH = 5;
    public static final int DEFAULT_TREE_MAX_ISSUES = 50;
    public static final int DEFAULT_ANALYSIS_MAX_PAGES = 5;
    public static final int DEFAULT_ANALYSIS_PAGE_SIZE = 100;
    public static final int DEFAULT_ANALYSIS_TOP_ISSUES_LIMIT = 10;
    public static final int DEFAULT_ANALYSIS_MAX_BLOCKER_DEPTH = 10;
    public static final int DEFAULT_ANALYSIS_MAX_BLOCKER_ISSUES = 30;
    public static final int DEFAULT_STALE_DAYS_SINCE_UPDATE = 30;
    public static final int DEFAULT_STALE_LIMIT = 25;
    public static final int DEFAULT_STALE_MAX_LIMIT = 100;
    public static final int DEFAULT_EXTRACTION_MAX_DEPTH = 1;
    public static final int DEFAULT_EXTRACTION_MAX_TOTAL_PARTS = 100;
    public static final long DEFAULT_EXTRACTION_MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    public static final long DEFAULT_EXTRACTION_MAX_ENTRY_BYTES = 10L * 1024 * 1024;
    public static final boolean DEFAULT_PANDOC_ENABLED = true;
    public static final int DEFAULT_PANDOC_PROBE_TIMEOUT_SECONDS = 2;
    public static final int DEFAULT_PANDOC_CONVERSION_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_ZIP_MAX_ENTRIES_PER_ARCHIVE = 100;
    public static final int DEFAULT_TIKA_BODY_LIMIT_BYTES = 5 * 1024 * 1024;
    public static final int DEFAULT_TIKA_METADATA_MAX_FIELDS = 40;

    public RedmineMcpProperties {
        attachment = attachment != null
                ? attachment
                : new AttachmentExtraction(DEFAULT_ATTACHMENT_PREVIEW_LIMIT);
        fullContext = fullContext != null
                ? fullContext
                : new FullContext(
                        DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT,
                        DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT,
                        DEFAULT_FULL_CONTEXT_MAX_SIBLINGS,
                        DEFAULT_FULL_CONTEXT_MAX_CHILDREN,
                        DEFAULT_FULL_CONTEXT_MAX_RELATED,
                        DEFAULT_FULL_CONTEXT_MAX_RECENT_NOTES,
                        DEFAULT_FULL_CONTEXT_MAX_NOTE_LENGTH);
        pagination = pagination != null
                ? pagination
                : new Pagination(
                        DEFAULT_PAGE_LIMIT,
                        DEFAULT_PAGE_OFFSET,
                        DEFAULT_MEMBERS_PAGE_LIMIT);
        tree = tree != null
                ? tree
                : new Tree(
                        DEFAULT_TREE_DEPTH,
                        DEFAULT_TREE_MAX_DEPTH,
                        DEFAULT_TREE_MAX_ISSUES);
        analysis = analysis != null
                ? analysis
                : new Analysis(
                        DEFAULT_ANALYSIS_MAX_PAGES,
                        DEFAULT_ANALYSIS_PAGE_SIZE,
                        DEFAULT_ANALYSIS_TOP_ISSUES_LIMIT,
                        DEFAULT_ANALYSIS_MAX_BLOCKER_DEPTH,
                        DEFAULT_ANALYSIS_MAX_BLOCKER_ISSUES,
                        null);
        extraction = extraction != null
                ? extraction
                : new Extraction(null, null, null, null);
    }

    public Path resolvedDataDir() {
        String value = dataDir;
        if (value == null || value.isBlank()) {
            value = Path.of(System.getProperty("user.home"), DEFAULT_DATA_DIR_NAME).toString();
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    public record AttachmentExtraction(
            @DefaultValue("" + DEFAULT_ATTACHMENT_PREVIEW_LIMIT) int previewLimit
    ) {
        public AttachmentExtraction {
            if (previewLimit <= 0) {
                previewLimit = DEFAULT_ATTACHMENT_PREVIEW_LIMIT;
            }
        }
    }

    public record FullContext(
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT) int attachmentTextLimit,
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT) int totalAttachmentTextLimit,
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_MAX_SIBLINGS) int maxSiblings,
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_MAX_CHILDREN) int maxChildren,
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_MAX_RELATED) int maxRelated,
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_MAX_RECENT_NOTES) int maxRecentNotes,
            @DefaultValue("" + DEFAULT_FULL_CONTEXT_MAX_NOTE_LENGTH) int maxNoteLength
    ) {
        public FullContext {
            if (attachmentTextLimit < 0) {
                attachmentTextLimit = DEFAULT_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT;
            }
            if (totalAttachmentTextLimit < 0) {
                totalAttachmentTextLimit = DEFAULT_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT;
            }
            if (maxSiblings < 0) {
                maxSiblings = DEFAULT_FULL_CONTEXT_MAX_SIBLINGS;
            }
            if (maxChildren < 0) {
                maxChildren = DEFAULT_FULL_CONTEXT_MAX_CHILDREN;
            }
            if (maxRelated < 0) {
                maxRelated = DEFAULT_FULL_CONTEXT_MAX_RELATED;
            }
            if (maxRecentNotes < 0) {
                maxRecentNotes = DEFAULT_FULL_CONTEXT_MAX_RECENT_NOTES;
            }
            if (maxNoteLength < 0) {
                maxNoteLength = DEFAULT_FULL_CONTEXT_MAX_NOTE_LENGTH;
            }
        }
    }

    public record Pagination(
            @DefaultValue("" + DEFAULT_PAGE_LIMIT) int defaultLimit,
            @DefaultValue("" + DEFAULT_PAGE_OFFSET) int defaultOffset,
            @DefaultValue("" + DEFAULT_MEMBERS_PAGE_LIMIT) int membersDefaultLimit
    ) {
        public Pagination {
            if (defaultLimit <= 0) {
                defaultLimit = DEFAULT_PAGE_LIMIT;
            }
            if (defaultOffset < 0) {
                defaultOffset = DEFAULT_PAGE_OFFSET;
            }
            if (membersDefaultLimit <= 0) {
                membersDefaultLimit = DEFAULT_MEMBERS_PAGE_LIMIT;
            }
        }
    }

    public record Tree(
            @DefaultValue("" + DEFAULT_TREE_DEPTH) int defaultDepth,
            @DefaultValue("" + DEFAULT_TREE_MAX_DEPTH) int maxDepth,
            @DefaultValue("" + DEFAULT_TREE_MAX_ISSUES) int maxIssues
    ) {
        public Tree {
            if (maxDepth <= 0) {
                maxDepth = DEFAULT_TREE_MAX_DEPTH;
            }
            if (defaultDepth < 0) {
                defaultDepth = DEFAULT_TREE_DEPTH;
            }
            if (defaultDepth > maxDepth) {
                defaultDepth = maxDepth;
            }
            if (maxIssues <= 0) {
                maxIssues = DEFAULT_TREE_MAX_ISSUES;
            }
        }
    }

    public record Analysis(
            @DefaultValue("" + DEFAULT_ANALYSIS_MAX_PAGES) int maxPages,
            @DefaultValue("" + DEFAULT_ANALYSIS_PAGE_SIZE) int pageSize,
            @DefaultValue("" + DEFAULT_ANALYSIS_TOP_ISSUES_LIMIT) int topIssuesLimit,
            @DefaultValue("" + DEFAULT_ANALYSIS_MAX_BLOCKER_DEPTH) int maxBlockerDepth,
            @DefaultValue("" + DEFAULT_ANALYSIS_MAX_BLOCKER_ISSUES) int maxBlockerIssues,
            StaleIssues staleIssues
    ) {
        public Analysis {
            if (maxPages <= 0) {
                maxPages = DEFAULT_ANALYSIS_MAX_PAGES;
            }
            if (pageSize <= 0) {
                pageSize = DEFAULT_ANALYSIS_PAGE_SIZE;
            }
            if (topIssuesLimit < 0) {
                topIssuesLimit = DEFAULT_ANALYSIS_TOP_ISSUES_LIMIT;
            }
            if (maxBlockerDepth < 0) {
                maxBlockerDepth = DEFAULT_ANALYSIS_MAX_BLOCKER_DEPTH;
            }
            if (maxBlockerIssues <= 0) {
                maxBlockerIssues = DEFAULT_ANALYSIS_MAX_BLOCKER_ISSUES;
            }
            staleIssues = staleIssues != null
                    ? staleIssues
                    : new StaleIssues(
                            DEFAULT_STALE_DAYS_SINCE_UPDATE,
                            DEFAULT_STALE_LIMIT,
                            DEFAULT_STALE_MAX_LIMIT);
        }
    }

    public record StaleIssues(
            @DefaultValue("" + DEFAULT_STALE_DAYS_SINCE_UPDATE) int defaultDaysSinceUpdate,
            @DefaultValue("" + DEFAULT_STALE_LIMIT) int defaultLimit,
            @DefaultValue("" + DEFAULT_STALE_MAX_LIMIT) int maxLimit
    ) {
        public StaleIssues {
            if (defaultDaysSinceUpdate < 0) {
                defaultDaysSinceUpdate = DEFAULT_STALE_DAYS_SINCE_UPDATE;
            }
            if (defaultLimit <= 0) {
                defaultLimit = DEFAULT_STALE_LIMIT;
            }
            if (maxLimit <= 0) {
                maxLimit = DEFAULT_STALE_MAX_LIMIT;
            }
            if (defaultLimit > maxLimit) {
                defaultLimit = maxLimit;
            }
        }
    }

    public record Extraction(
            Pandoc pandoc,
            Limits limits,
            Zip zip,
            Tika tika
    ) {
        public Extraction {
            pandoc = pandoc != null ? pandoc : new Pandoc(DEFAULT_PANDOC_ENABLED,
                    DEFAULT_PANDOC_PROBE_TIMEOUT_SECONDS,
                    DEFAULT_PANDOC_CONVERSION_TIMEOUT_SECONDS);
            limits = limits != null ? limits : new Limits(
                    DEFAULT_EXTRACTION_MAX_DEPTH,
                    DEFAULT_EXTRACTION_MAX_TOTAL_PARTS,
                    DEFAULT_EXTRACTION_MAX_TOTAL_BYTES,
                    DEFAULT_EXTRACTION_MAX_ENTRY_BYTES);
            zip = zip != null ? zip : new Zip(DEFAULT_ZIP_MAX_ENTRIES_PER_ARCHIVE);
            tika = tika != null ? tika : new Tika(
                    DEFAULT_TIKA_BODY_LIMIT_BYTES,
                    DEFAULT_TIKA_METADATA_MAX_FIELDS);
        }
    }

    public record Pandoc(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("" + DEFAULT_PANDOC_PROBE_TIMEOUT_SECONDS) int probeTimeoutSeconds,
            @DefaultValue("" + DEFAULT_PANDOC_CONVERSION_TIMEOUT_SECONDS) int conversionTimeoutSeconds
    ) {
        public Pandoc {
            if (probeTimeoutSeconds <= 0) {
                probeTimeoutSeconds = DEFAULT_PANDOC_PROBE_TIMEOUT_SECONDS;
            }
            if (conversionTimeoutSeconds <= 0) {
                conversionTimeoutSeconds = DEFAULT_PANDOC_CONVERSION_TIMEOUT_SECONDS;
            }
        }
    }

    public record Limits(
            @DefaultValue("" + DEFAULT_EXTRACTION_MAX_DEPTH) int maxDepth,
            @DefaultValue("" + DEFAULT_EXTRACTION_MAX_TOTAL_PARTS) int maxTotalParts,
            @DefaultValue("" + DEFAULT_EXTRACTION_MAX_TOTAL_BYTES) long maxTotalBytes,
            @DefaultValue("" + DEFAULT_EXTRACTION_MAX_ENTRY_BYTES) long maxEntryBytes
    ) {
        public Limits {
            if (maxDepth < 0) {
                maxDepth = DEFAULT_EXTRACTION_MAX_DEPTH;
            }
            if (maxTotalParts <= 0) {
                maxTotalParts = DEFAULT_EXTRACTION_MAX_TOTAL_PARTS;
            }
            if (maxTotalBytes <= 0) {
                maxTotalBytes = DEFAULT_EXTRACTION_MAX_TOTAL_BYTES;
            }
            if (maxEntryBytes <= 0) {
                maxEntryBytes = DEFAULT_EXTRACTION_MAX_ENTRY_BYTES;
            }
        }
    }

    public record Zip(
            @DefaultValue("" + DEFAULT_ZIP_MAX_ENTRIES_PER_ARCHIVE) int maxEntriesPerArchive
    ) {
        public Zip {
            if (maxEntriesPerArchive <= 0) {
                maxEntriesPerArchive = DEFAULT_ZIP_MAX_ENTRIES_PER_ARCHIVE;
            }
        }
    }

    public record Tika(
            @DefaultValue("" + DEFAULT_TIKA_BODY_LIMIT_BYTES) int bodyLimitBytes,
            @DefaultValue("" + DEFAULT_TIKA_METADATA_MAX_FIELDS) int metadataMaxFields
    ) {
        public Tika {
            if (bodyLimitBytes <= 0) {
                bodyLimitBytes = DEFAULT_TIKA_BODY_LIMIT_BYTES;
            }
            if (metadataMaxFields < 0) {
                metadataMaxFields = DEFAULT_TIKA_METADATA_MAX_FIELDS;
            }
        }
    }
}
