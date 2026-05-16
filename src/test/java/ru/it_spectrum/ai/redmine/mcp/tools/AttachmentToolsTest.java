package ru.it_spectrum.ai.redmine.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.IssueSnapshotService;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;

import io.modelcontextprotocol.spec.McpSchema;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
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
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), new RedmineMcpProperties(dataDir.toString()));
        var service = new AttachmentService(client,
                new DocumentTextExtractor(), snapshot);
        tools = new AttachmentTools(service, ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
    }

    // --- getImageAttachment ---

    @Test
    void shouldReturnImage() throws Exception {
        byte[] imageData = generatePng(100, 80);
        var attachment = attachment(20, "photo.png", "image/png", imageData.length);

        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(imageData);

        McpSchema.CallToolResult result = tools.getImageAttachment(100, 20, null);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst()).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("photo.png");
        assertThat(result.content().get(1)).isInstanceOf(McpSchema.ImageContent.class);
    }

    @Test
    void shouldResizeLargeImage() throws Exception {
        byte[] imageData = generatePng(2048, 1536);
        var attachment = attachment(21, "big.png", "image/png", imageData.length);

        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(imageData);

        McpSchema.CallToolResult result = tools.getImageAttachment(100, 21, 512);

        assertThat(result.isError()).isFalse();
        assertThat(result.content().get(1)).isInstanceOf(McpSchema.ImageContent.class);
        var imageContent = (McpSchema.ImageContent) result.content().get(1);
        assertThat(imageContent.mimeType()).isEqualTo("image/png");
    }

    @Test
    void shouldRejectNonImageAttachment() {
        var attachment = attachment(22, "document.pdf", "application/pdf", 10_000);
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));

        McpSchema.CallToolResult result = tools.getImageAttachment(100, 22, null);

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
                .contains("not an image");
    }

    @Test
    void shouldHandleImageAttachmentNotFound() {
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of()));

        McpSchema.CallToolResult result = tools.getImageAttachment(100, 999, null);

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
                .contains("not found");
    }

    @Test
    void shouldHandleImageDownloadFailure() {
        var attachment = attachment(23, "fail.png", "image/png", 5_000);
        when(client.getIssue(100)).thenReturn(issueWithAttachments(100, List.of(attachment)));
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(null);

        McpSchema.CallToolResult result = tools.getImageAttachment(100, 23, null);

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
                .contains("Failed to download");
    }

    // --- helpers ---

    private static byte[] generatePng(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

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
