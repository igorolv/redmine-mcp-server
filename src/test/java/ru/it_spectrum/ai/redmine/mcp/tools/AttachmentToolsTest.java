package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.AttachmentTextCache;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;

import io.modelcontextprotocol.spec.McpSchema;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentToolsTest {

    @Mock
    private RedmineClient client;

    private AttachmentTools tools;

    @BeforeEach
    void setUp() {
        tools = new AttachmentTools(client, new AttachmentTextCache());
    }

    // --- listAttachments ---

    @Test
    void shouldListAttachments() {
        var attachments = List.of(
                new RedmineAttachment(10, "report.pdf", 150_000, "application/pdf",
                        "http://redmine/download/10/report.pdf", "Monthly report",
                        new IdName(1, "Alice"), "2025-03-01"),
                new RedmineAttachment(11, "screenshot.png", 50_000, "image/png",
                        "http://redmine/download/11/screenshot.png", null,
                        new IdName(2, "Bob"), "2025-03-02")
        );
        var issue = issueWithAttachments(100, attachments);
        when(client.getIssue(100)).thenReturn(issue);

        String result = tools.listAttachments(100);

        assertThat(result).contains("Attachments for issue #100 (2 files):");
        assertThat(result).containsPattern("\\[10] report\\.pdf \\(application/pdf, 146[.,]5 KB\\)");
        assertThat(result).contains("Description: Monthly report");
        assertThat(result).containsPattern("\\[11] screenshot\\.png \\(image/png, 48[.,]8 KB\\)");
    }

    @Test
    void shouldHandleIssueWithNoAttachments() {
        var issue = issueWithAttachments(200, List.of());
        when(client.getIssue(200)).thenReturn(issue);

        String result = tools.listAttachments(200);

        assertThat(result).isEqualTo("Issue #200 has no attachments");
    }

    @Test
    void shouldHandleIssueNotFoundForAttachments() {
        when(client.getIssue(999)).thenReturn(null);

        String result = tools.listAttachments(999);

        assertThat(result).isEqualTo("Issue #999 not found");
    }

    // --- getImageAttachment ---

    @Test
    void shouldReturnImage() throws Exception {
        byte[] imageData = generatePng(100, 80);
        var attachment = attachment(20, "photo.png", "image/png", imageData.length);

        when(client.getAttachment(20)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(imageData);

        McpSchema.CallToolResult result = tools.getImageAttachment(20, null);

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

        when(client.getAttachment(21)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(imageData);

        McpSchema.CallToolResult result = tools.getImageAttachment(21, 512);

        assertThat(result.isError()).isFalse();
        assertThat(result.content().get(1)).isInstanceOf(McpSchema.ImageContent.class);
        var imageContent = (McpSchema.ImageContent) result.content().get(1);
        assertThat(imageContent.mimeType()).isEqualTo("image/png");
    }

    @Test
    void shouldRejectNonImageAttachment() {
        var attachment = attachment(22, "document.pdf", "application/pdf", 10_000);
        when(client.getAttachment(22)).thenReturn(attachment);

        McpSchema.CallToolResult result = tools.getImageAttachment(22, null);

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
                .contains("not an image");
    }

    @Test
    void shouldHandleImageAttachmentNotFound() {
        when(client.getAttachment(999)).thenReturn(null);

        McpSchema.CallToolResult result = tools.getImageAttachment(999, null);

        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
                .contains("not found");
    }

    @Test
    void shouldHandleImageDownloadFailure() {
        var attachment = attachment(23, "fail.png", "image/png", 5_000);
        when(client.getAttachment(23)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(null);

        McpSchema.CallToolResult result = tools.getImageAttachment(23, null);

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
