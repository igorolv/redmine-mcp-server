package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.AttachmentTextCache;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.FixedSizeTextChunker;

import io.modelcontextprotocol.spec.McpSchema;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
        var service = new AttachmentService(client,
                new DocumentTextExtractor(client, new AttachmentTextCache()),
                new FixedSizeTextChunker());
        tools = new AttachmentTools(service, ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
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

        assertThat(result).contains("\"issueId\":100");
        assertThat(result).contains("\"id\":10");
        assertThat(result).contains("report.pdf");
        assertThat(result).contains("application/pdf");
        assertThat(result).contains("Monthly report");
        assertThat(result).contains("\"id\":11");
        assertThat(result).contains("screenshot.png");
        assertThat(result).contains("image/png");
    }

    @Test
    void shouldHandleIssueWithNoAttachments() {
        var issue = issueWithAttachments(200, List.of());
        when(client.getIssue(200)).thenReturn(issue);

        String result = tools.listAttachments(200);

        assertThat(result).contains("\"issueId\":200");
        assertThat(result).contains("\"attachments\":[]");
    }

    @Test
    void shouldHandleIssueNotFoundForAttachments() {
        when(client.getIssue(999)).thenReturn(null);

        String result = tools.listAttachments(999);

        assertThat(result).contains("\"kind\":\"not_found\"");
        assertThat(result).contains("issue #999 not found");
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

    // --- searchAttachmentContent ---

    @Test
    void shouldFindTextInAttachment() {
        var att = new RedmineAttachment(50, "spec.txt", 100, "text/plain",
                "http://redmine/download/50/spec.txt", null,
                new IdName(1, "Alice"), "2025-03-01");
        var issue = issueWithAttachments(100, List.of(att));
        when(client.getIssue(100)).thenReturn(issue);
        when(client.downloadAttachment(att.contentUrl()))
                .thenReturn("The system uses OAuth for authentication and JWT for sessions".getBytes());

        String result = tools.searchAttachmentContent("OAuth", 100, null, null);

        assertThat(result).contains("\"query\":\"OAuth\"");
        assertThat(result).contains("\"issueId\":100");
        assertThat(result).contains("\"issueId\":100");
        assertThat(result).contains("Test issue");
        assertThat(result).contains("\"attachmentId\":50");
        assertThat(result).contains("spec.txt");
        assertThat(result).contains("OAuth");
        assertThat(result).contains("\"totalMatches\":1");
        assertThat(result).contains("\"matchingAttachments\":1");
        assertThat(result).contains("\"matchingIssues\":1");
    }

    @Test
    void shouldFindTextInsideZipAttachment() throws Exception {
        byte[] zipBytes = generateZip(Map.of(
                "docs/spec.txt", "The system uses OAuth inside a ZIP archive".getBytes(),
                "images/screenshot.png", new byte[]{1, 2, 3}
        ));
        var att = new RedmineAttachment(51, "docs.zip", zipBytes.length, "application/octet-stream",
                "http://redmine/download/51/docs.zip", null,
                new IdName(1, "Alice"), "2025-03-01");
        var issue = issueWithAttachments(100, List.of(att));
        when(client.getIssue(100)).thenReturn(issue);
        when(client.downloadAttachment(att.contentUrl())).thenReturn(zipBytes);

        String result = tools.searchAttachmentContent("OAuth", 100, null, null);

        assertThat(result).contains("\"query\":\"OAuth\"");
        assertThat(result).contains("\"issueId\":100");
        assertThat(result).contains("Test issue");
        assertThat(result).contains("\"attachmentId\":51");
        assertThat(result).contains("docs.zip");
        assertThat(result).contains("OAuth inside a ZIP archive");
        assertThat(result).contains("\"totalMatches\":1");
    }

    @Test
    void shouldReturnNoMatchesWhenQueryNotFound() {
        var att = new RedmineAttachment(50, "spec.txt", 100, "text/plain",
                "http://redmine/download/50/spec.txt", null,
                new IdName(1, "Alice"), "2025-03-01");
        var issue = issueWithAttachments(100, List.of(att));
        when(client.getIssue(100)).thenReturn(issue);
        when(client.downloadAttachment(att.contentUrl()))
                .thenReturn("Nothing relevant here".getBytes());

        String result = tools.searchAttachmentContent("OAuth", 100, null, null);

        assertThat(result).contains("\"totalMatches\":0");
    }

    @Test
    void shouldSkipBinaryAttachments() {
        var binAtt = new RedmineAttachment(60, "image.png", 5000, "image/png",
                "http://redmine/download/60/image.png", null,
                new IdName(1, "Alice"), "2025-03-01");
        var issue = issueWithAttachments(100, List.of(binAtt));
        when(client.getIssue(100)).thenReturn(issue);

        String result = tools.searchAttachmentContent("test", 100, null, null);

        assertThat(result).contains("\"scannedAttachments\":0");
    }

    @Test
    void shouldSearchAcrossProjectIssues() {
        var att1 = new RedmineAttachment(50, "doc.txt", 100, "text/plain",
                "http://redmine/download/50/doc.txt", null,
                new IdName(1, "Alice"), "2025-03-01");
        var issue1 = issueWithAttachments(101, List.of(att1));

        // listIssues returns issue summaries (no attachments)
        var summaryIssue = issueWithAttachments(101, null);
        when(client.listIssues("proj", "*", null, null, null, null,
                "updated_on:desc", null, 0, 10))
                .thenReturn(new RedmineIssue.Page(List.of(summaryIssue), 1, 0, 10));
        when(client.getIssue(101)).thenReturn(issue1);
        when(client.downloadAttachment(att1.contentUrl()))
                .thenReturn("OAuth integration guide".getBytes());

        String result = tools.searchAttachmentContent("OAuth", null, "proj", null);

        assertThat(result).contains("\"query\":\"OAuth\"");
        assertThat(result).contains("\"projectId\":\"proj\"");
        assertThat(result).contains("\"issueId\":101");
        assertThat(result).contains("OAuth");
    }

    @Test
    void shouldRequireIssueIdOrProjectId() {
        String result = tools.searchAttachmentContent("test", null, null, null);

        assertThat(result).contains("\"kind\":\"argument\"");
        assertThat(result).contains("At least one of issueId or projectId must be provided");
    }

    @Test
    void shouldBeCaseInsensitive() {
        var att = new RedmineAttachment(50, "doc.txt", 100, "text/plain",
                "http://redmine/download/50/doc.txt", null,
                new IdName(1, "Alice"), "2025-03-01");
        var issue = issueWithAttachments(100, List.of(att));
        when(client.getIssue(100)).thenReturn(issue);
        when(client.downloadAttachment(att.contentUrl()))
                .thenReturn("The OAUTH protocol is used here".getBytes());

        String result = tools.searchAttachmentContent("oauth", 100, null, null);

        assertThat(result).contains("\"totalMatches\":1");
        assertThat(result).contains("\"matchingAttachments\":1");
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

    private static byte[] generateZip(Map<String, byte[]> entries) throws Exception {
        var baos = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(baos)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
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
