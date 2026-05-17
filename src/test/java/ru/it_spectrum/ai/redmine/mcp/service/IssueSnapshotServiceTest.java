package ru.it_spectrum.ai.redmine.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueSnapshotServiceTest {

    @TempDir
    private Path dataDir;

    @Mock
    private RedmineClient client;

    @Test
    void shouldStoreIssueMetadataAndDownloadAttachmentUnderIssueDirectory() throws Exception {
        byte[] content = "snapshot document".getBytes();
        var attachment = attachment(12345, "document.docx", content.length);
        var issue = issue(4202, List.of(attachment));
        var service = service();

        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(content);

        service.snapshotIssue(issue);
        Path localFile = service.materializeAttachment(4202, attachment);

        assertThat(localFile).isRegularFile();
        assertThat(localFile).isEqualTo(dataDir.resolve("issues")
                .resolve("4202")
                .resolve("attachments")
                .resolve("12345__document.docx"));
        assertThat(Files.readString(localFile)).isEqualTo("snapshot document");
        assertThat(dataDir.resolve("issues").resolve("4202").resolve("issue.json")).isRegularFile();
        assertThat(dataDir.resolve("issues").resolve("4202").resolve("snapshot.json")).isRegularFile();
        assertThat(Files.readString(dataDir.resolve("issues").resolve("4202").resolve("snapshot.json")))
                .contains("\"issueId\" : 4202")
                .contains("\"redmineUpdatedOn\" : \"2025-01-02\"")
                .contains("\"snapshottedAt\"")
                .contains("\"source\" : \"provided RedmineIssue object\"");
        assertThat(dataDir.resolve("issues").resolve("4202").resolve("attachments.json")).isRegularFile();
        assertThat(dataDir.resolve("issues").resolve("4202").resolve("extracted").resolve("12345")).isDirectory();
        assertThat(dataDir.resolve("issues").resolve("attachment-index.json")).doesNotExist();
    }

    @Test
    void shouldStoreExplicitSnapshotSource() throws Exception {
        var service = service();

        service.snapshotIssue(issue(4202, List.of()), "GET /issues/4202.json?include=attachments");

        assertThat(Files.readString(dataDir.resolve("issues").resolve("4202").resolve("snapshot.json")))
                .contains("\"source\" : \"GET /issues/4202.json?include=attachments\"");
    }

    @Test
    void shouldReuseMaterializedAttachmentWhenSizeMatches() throws Exception {
        byte[] content = "snapshot document".getBytes();
        var attachment = attachment(12345, "document.docx", content.length);
        var service = service();

        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(content);

        Path first = service.materializeAttachment(4202, attachment);
        Path second = service.materializeAttachment(4202, attachment);

        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(second)).isEqualTo("snapshot document");
        verify(client, times(1)).downloadAttachment(attachment.contentUrl());
    }

    @Test
    void shouldSanitizeAttachmentFilename() {
        byte[] content = "snapshot document".getBytes();
        var attachment = attachment(12345, "dir\\bad:name?.docx", content.length);
        var service = service();

        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(content);

        Path localFile = service.materializeAttachment(4202, attachment);

        assertThat(localFile.getFileName().toString()).isEqualTo("12345__dir_bad_name_.docx");
    }

    private IssueSnapshotService service() {
        return new IssueSnapshotService(client, new ObjectMapper(),
                TestRedmineMcpProperties.withDataDir(dataDir));
    }

    private static RedmineAttachment attachment(int id, String filename, long size) {
        return new RedmineAttachment(
                id, filename, size, "application/octet-stream",
                "http://redmine/download/" + id + "/" + filename,
                null,
                new IdName(1, "Tester"),
                "2025-01-01"
        );
    }

    private static RedmineIssue issue(int id, List<RedmineAttachment> attachments) {
        return new RedmineIssue(
                id,
                new IdName(1, "proj"),
                new IdName(1, "Bug"),
                new IdName(1, "Open"),
                new IdName(2, "Normal"),
                new IdName(1, "Author"),
                null,
                null, null, null,
                "Test issue", null,
                null, null, 0,
                null, null, false,
                "2025-01-01", "2025-01-02",
                null, attachments, null, null, null
        );
    }
}
