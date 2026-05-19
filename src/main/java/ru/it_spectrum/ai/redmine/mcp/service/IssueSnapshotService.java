package ru.it_spectrum.ai.redmine.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

@Service
public class IssueSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(IssueSnapshotService.class);
    private static final String UNKNOWN_SOURCE = "provided RedmineIssue object";

    private final RedmineClient client;
    private final ObjectMapper mapper;
    private final Path dataDir;
    private final Path issuesDir;

    public IssueSnapshotService(RedmineClient client, ObjectMapper mapper, RedmineMcpProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.dataDir = properties.resolvedDataDir();
        this.issuesDir = this.dataDir.resolve("issues");
    }

    @PostConstruct
    void logResolvedDataDir() {
        log.info("Redmine MCP data directory resolved to: {} (issues stored under: {})", dataDir, issuesDir);
    }

    public void snapshotIssue(RedmineIssue issue) {
        snapshotIssue(issue, UNKNOWN_SOURCE);
    }

    public void snapshotIssue(RedmineIssue issue, String source) {
        if (issue == null) {
            return;
        }

        try {
            String snapshottedAt = Instant.now().toString();
            Path issueDir = issueDirectory(issue.id());
            Files.createDirectories(issueDir);
            Files.createDirectories(issueDir.resolve("attachments"));
            Files.createDirectories(issueDir.resolve("extracted"));
            mapper.writerWithDefaultPrettyPrinter().writeValue(issueDir.resolve("issue.json").toFile(), issue);
            mapper.writerWithDefaultPrettyPrinter().writeValue(issueDir.resolve("snapshot.json").toFile(),
                    new SnapshotMetadata(
                            issue.id(),
                            issue.updatedOn(),
                            snapshottedAt,
                            source
                    ));

            if (issue.attachments() != null) {
                writeAttachmentsManifest(issue.id(), snapshottedAt, issue.attachments());
            }
        } catch (IOException e) {
            log.warn("Failed to write issue #{} snapshot: {}", issue.id(), e.getMessage());
        }
    }

    public Path materializeAttachment(int issueId, RedmineAttachment attachment) {
        Path attachmentsDir = issueDirectory(issueId).resolve("attachments");
        Path extractedDir = attachmentExtractedDir(issueId, attachment.id());
        Path target = attachmentsDir.resolve(localFilename(attachment));

        try {
            Files.createDirectories(attachmentsDir);
            Files.createDirectories(extractedDir);
            if (isMaterializedFileValid(target, attachment)) {
                return target;
            }

            byte[] data = client.downloadAttachment(attachment.contentUrl());
            if (data == null) {
                throw new AttachmentDownloadFailedException(attachment.id());
            }

            Path partial = target.resolveSibling(target.getFileName() + ".part");
            Files.write(partial, data);
            moveIntoPlace(partial, target);
            return target;
        } catch (IOException e) {
            throw new AttachmentDownloadFailedException(attachment.id(), e);
        }
    }

    public Path issueDirectory(int issueId) {
        return issueDirectory(String.valueOf(issueId));
    }

    /** Per-attachment scratch dir for extraction parsers (intermediate ZIP entries, pandoc output, etc.). */
    public Path attachmentExtractedDir(int issueId, int attachmentId) {
        return issueDirectory(issueId).resolve("extracted").resolve(String.valueOf(attachmentId));
    }

    public Path issueDirectory(String issueSegment) {
        return issuesDir.resolve(issueSegment);
    }

    private boolean isMaterializedFileValid(Path target, RedmineAttachment attachment) throws IOException {
        if (!Files.isRegularFile(target)) {
            return false;
        }
        long expectedSize = attachment.filesize();
        return expectedSize <= 0 || Files.size(target) == expectedSize;
    }

    private void writeAttachmentsManifest(int issueId, String snapshottedAt,
                                          List<RedmineAttachment> attachments) throws IOException {
        writeAttachmentsManifest(String.valueOf(issueId), issueId, snapshottedAt, attachments);
    }

    private void writeAttachmentsManifest(String issueSegment, Integer issueId,
                                          String snapshottedAt, List<?> attachments) throws IOException {
        Path issueDir = issueDirectory(issueSegment);
        Files.createDirectories(issueDir);
        List<?> snapshotAttachments = attachments.stream()
                .map(value -> value instanceof RedmineAttachment attachment
                        ? SnapshotAttachment.from(attachment, localFilename(attachment))
                        : value)
                .toList();
        var manifest = new AttachmentManifest(issueId, snapshottedAt, snapshotAttachments);
        mapper.writerWithDefaultPrettyPrinter().writeValue(issueDir.resolve("attachments.json").toFile(), manifest);
    }

    private void moveIntoPlace(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String localFilename(RedmineAttachment attachment) {
        return attachment.id() + "__" + safeFilename(attachment.filename());
    }

    private String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "attachment" : filename.strip();
        value = value.replace('\\', '_').replace('/', '_').replace(':', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_')
                .replace('<', '_').replace('>', '_').replace('|', '_');
        value = value.replaceAll("\\p{Cntrl}", "_").replaceAll("\\s+", " ").strip();
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
            return "attachment";
        }
        return value;
    }

    public record SnapshotMetadata(
            int issueId,
            String redmineUpdatedOn,
            String snapshottedAt,
            String source
    ) {
    }

    public record AttachmentManifest(
            Integer issueId,
            String snapshottedAt,
            List<?> attachments
    ) {
    }

    public record SnapshotAttachment(
            int id,
            String filename,
            String localFilename,
            long filesize,
            String contentType,
            String contentUrl,
            String description,
            Object author,
            String createdOn
    ) {
        static SnapshotAttachment from(RedmineAttachment attachment, String localFilename) {
            return new SnapshotAttachment(
                    attachment.id(),
                    attachment.filename(),
                    localFilename,
                    attachment.filesize(),
                    attachment.contentType(),
                    attachment.contentUrl(),
                    attachment.description(),
                    attachment.author(),
                    attachment.createdOn()
            );
        }
    }
}
