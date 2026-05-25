package ru.it_spectrum.ai.redmine.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractionTestPipelines;
import ru.it_spectrum.ai.redmine.mcp.focus.AttachmentContentFocus;
import ru.it_spectrum.ai.redmine.mcp.service.IssueSnapshotService;
import ru.it_spectrum.ai.redmine.mcp.service.compression.TestCompression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentToolsTest {

    @Mock
    private RedmineClient client;

    @TempDir
    private Path dataDir;

    private AttachmentTools tools;

    @BeforeEach
    void setUp() {
        var properties = TestRedmineMcpProperties.withDataDir(dataDir);
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), properties);
        var service = ExtractionTestPipelines.newAttachmentService(client, snapshot);
        tools = new AttachmentTools(service, new AttachmentContentFocus(),
                TestCompression.attachmentContentCompression(properties));
    }

    // --- getAttachment ---

    @Test
    void shouldReturnMaterializedFilePath() throws Exception {
        byte[] data = "original file bytes".getBytes();
        var attachment = attachment(20, "photo.png", "image/png", data.length);

        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(data);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 20));

        var json = ToolJsonTestSupport.parse(result);
        assertThat(json.get("attachment").get("filename").asText()).isEqualTo("photo.png");
        Path localPath = Path.of(json.get("localPath").asText());
        assertThat(localPath).exists();
        assertThat(Files.readAllBytes(localPath)).isEqualTo(data);
        assertThat(json.get("fileUri").asText()).startsWith("file:///");
        assertThat(json.get("localSize").asLong()).isEqualTo(data.length);
    }

    @Test
    void shouldReturnImageContextAsFilePart() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        var attachment = attachment(21, "big.png", "image/png", data.length);

        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(data);

        var result = ToolJsonTestSupport.stringify(tools.getAttachment(100, 21));
        var json = ToolJsonTestSupport.parse(result);

        assertThat(result).contains("big.png");
        assertThat(json.get("parts")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(result).contains("\"extractionType\":\"image\"");
        assertThat(result).contains("\"producer\":\"ImagePassthroughParser\"");
        assertThat(json.get("textExtracted").asBoolean()).isFalse();
        assertThat(result).contains("localPath");
        assertThat(result).contains("fileUri");
    }

    @Test
    void shouldHandleAttachmentNotFound() {
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of()));

        assertThatThrownBy(() -> tools.getAttachment(100, 999))
                .hasMessageContaining("not found");
    }

    @Test
    void shouldHandleAttachmentDownloadFailure() {
        var attachment = attachment(23, "fail.png", "image/png", 5_000);
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(null);

        assertThatThrownBy(() -> tools.getAttachment(100, 23))
                .hasMessageContaining("Failed to download attachment");
    }

    // --- helpers ---

    private static RedmineAttachment attachment(int id, String filename, String contentType, long size) {
        return new RedmineAttachment(
                id, filename, size, contentType,
                "http://redmine.example.com/attachments/download/" + id + "/" + filename,
                null,
                new IdName(1, "Test User"),
                "2025-01-01T00:00:00Z"
        );
    }

    private static RedmineIssue issueWithAttachments(int id, List<RedmineAttachment> attachments) {
        return new RedmineIssue(
                id,
                new IdName(1, "test-project"),
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
