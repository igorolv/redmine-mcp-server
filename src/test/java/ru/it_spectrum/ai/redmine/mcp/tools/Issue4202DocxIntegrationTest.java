package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.modelcontextprotocol.spec.McpSchema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REDMINE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REDMINE_API_KEY", matches = ".+")
class Issue4202DocxIntegrationTest {

    private static final int ISSUE_ID = 4202;

    @Autowired
    private RedmineClient redmineClient;

    @Autowired
    private AttachmentTools attachmentTools;

    @Autowired
    private DocumentTextExtractor textExtractor;

    @Test
    void shouldParseFirstDocxAttachmentFromIssue4202ViaProjectMethods() throws Exception {
        var issue = loadIssue4202();
        var attachment = findFirstDocxAttachment(issue);

        printIssueAndAttachment(issue, attachment);

        String directText = textExtractor.extractText(attachment);
        assertThat(directText)
                .as("DocumentTextExtractor should extract text from attachment #%d".formatted(attachment.id()))
                .isNotBlank()
                .doesNotContain("failed to extract text");

        String content = readAttachmentContent(attachment);
        var contentJson = ToolJsonTestSupport.parse(content);
        String contentBody = contentJson.get("content").asText();

        assertThat(content)
                .as("Attachment #%d (%s) should be readable".formatted(attachment.id(), attachment.filename()))
                .contains(attachment.filename())
                .contains("\"textExtracted\":true")
                .doesNotContain("Binary file")
                .doesNotContain("failed to extract text");

        assertThat(contentJson.get("extractionType").asText()).isEqualTo("docx");
        assertThat(contentBody).isNotBlank();
        assertThat(directText).contains(contentBody.substring(0, Math.min(200, contentBody.length())));

        System.out.println("Direct extractor preview:");
        System.out.println(snippet(directText, 2_000));
        System.out.println();
        System.out.println("AttachmentTools.getAttachmentContent preview:");
        System.out.println(snippet(content, 2_000));
    }

    @Test
    void shouldSearchInsideFirstDocxAttachmentFromIssue4202() {
        var issue = loadIssue4202();
        var attachment = findFirstDocxAttachment(issue);
        String directText = textExtractor.extractText(attachment);
        String query = pickSearchQuery(directText);

        String result = attachmentTools.searchAttachmentContent(query, ISSUE_ID, null, null);

        assertThat(result)
                .contains("Attachment content search for \"" + query + "\" in issue #" + ISSUE_ID)
                .contains("Issue #" + ISSUE_ID + ":")
                .contains("[" + attachment.id() + "] " + attachment.filename())
                .doesNotContain("No matches found");

        System.out.println();
        System.out.println("Attachment search query: " + query);
        System.out.println(snippet(result, 2_000));
    }

    @Test
    void shouldLoadFirstPngAttachmentFromIssue4202AsImageContent() throws Exception {
        var issue = loadIssue4202();
        var attachment = findFirstImageAttachment(issue);

        McpSchema.CallToolResult result = attachmentTools.getImageAttachment(attachment.id(), null);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().getFirst()).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
                .contains(attachment.filename());
        assertThat(result.content().get(1)).isInstanceOf(McpSchema.ImageContent.class);

        var imageContent = (McpSchema.ImageContent) result.content().get(1);
        assertThat(imageContent.mimeType()).isEqualTo("image/png");
        assertThat(imageContent.data()).isNotBlank();

        byte[] imageBytes = Base64.getDecoder().decode(imageContent.data());
        var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);

        System.out.println();
        System.out.println("PNG attachment: #" + attachment.id() + " " + attachment.filename());
        System.out.println("Decoded image size: " + image.getWidth() + "x" + image.getHeight());
    }

    @Test
    void shouldLoadCustomFieldsFromIssue4202() {
        var issue = loadIssue4202();

        assertThat(issue.customFields())
                .as("Issue #%d should expose custom fields".formatted(ISSUE_ID))
                .isNotNull()
                .isNotEmpty();

        var customerSystemField = findCustomField(issue, "# в системе заказчика");
        var mantisField = findCustomField(issue, "№ в Mantis");
        var applicationsField = findCustomField(issue, "applications");

        assertThat(customerSystemField.displayValue()).isEqualTo("502167");
        assertThat(mantisField.values()).containsExactly("");
        assertThat(mantisField.displayValue()).isEmpty();
        assertThat(mantisField.isEmpty()).isTrue();
        assertThat(applicationsField.isMultiple()).isTrue();
        assertThat(applicationsField.values()).contains("rtk");
        assertThat(applicationsField.displayValue()).isEqualTo("rtk");

        System.out.println();
        System.out.println("Custom fields for issue #" + ISSUE_ID + ":");
        for (var customField : issue.customFields()) {
            if (!customField.isEmpty()) {
                System.out.println("  [" + customField.id() + "] " + customField.name()
                        + " = " + customField.displayValue());
            }
        }
    }

    private RedmineIssue loadIssue4202() {
        var issue = redmineClient.getIssue(ISSUE_ID);

        assertThat(issue).isNotNull();
        assertThat(issue.description())
                .as("Issue #%d description should be present".formatted(ISSUE_ID))
                .isNotBlank();
        assertThat(issue.attachments())
                .as("Issue #%d should have attachments".formatted(ISSUE_ID))
                .isNotNull()
                .isNotEmpty();

        return issue;
    }

    private RedmineAttachment findFirstDocxAttachment(RedmineIssue issue) {
        var attachment = issue.attachments().stream()
                .filter(att -> "docx".equals(textExtractor.getFileExtension(att.filename()))
                        || (att.contentType() != null && att.contentType().contains("wordprocessingml")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Issue #%d has no DOCX attachment".formatted(ISSUE_ID)));

        assertThat(attachment.contentUrl())
                .as("DOCX attachment #%d should have a content URL".formatted(attachment.id()))
                .isNotBlank();

        return attachment;
    }

    private RedmineAttachment findFirstImageAttachment(RedmineIssue issue) {
        var attachment = issue.attachments().stream()
                .filter(att -> {
                    String ext = textExtractor.getFileExtension(att.filename());
                    String contentType = att.contentType() != null ? att.contentType() : "";
                    return "png".equals(ext) || contentType.startsWith("image/");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Issue #%d has no image attachment".formatted(ISSUE_ID)));

        assertThat(attachment.contentUrl())
                .as("Image attachment #%d should have a content URL".formatted(attachment.id()))
                .isNotBlank();

        return attachment;
    }

    private String readAttachmentContent(RedmineAttachment attachment) {
        try {
            return attachmentTools.getAttachmentContent(attachment.id());
        } catch (Exception e) {
            throw new AssertionError(
                    "Failed to read DOCX attachment #%d (%s) from issue #%d via content URL %s"
                            .formatted(attachment.id(), attachment.filename(), ISSUE_ID, attachment.contentUrl()),
                    e
            );
        }
    }

    private void printIssueAndAttachment(RedmineIssue issue, RedmineAttachment attachment) {
        System.out.println("Issue #" + ISSUE_ID + " description:");
        System.out.println(snippet(issue.description(), 500));
        System.out.println();
        System.out.println("DOCX attachment: #" + attachment.id() + " " + attachment.filename()
                + " (" + attachment.contentType() + ")");
        System.out.println("Content URL: " + attachment.contentUrl());
    }

    private RedmineIssue.CustomField findCustomField(RedmineIssue issue, String name) {
        return issue.customFields().stream()
                .filter(customField -> name.equals(customField.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Issue #%d does not contain custom field '%s'".formatted(ISSUE_ID, name)));
    }

    private static String pickSearchQuery(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] preferred = {
                "Налоговое резидентство",
                "Ведение сведений второго гражданства",
                "Основное гражданство"
        };

        for (String candidate : preferred) {
            if (normalized.contains(candidate)) {
                return candidate;
            }
        }

        var matcher = java.util.regex.Pattern.compile("[\\p{L}]{8,}(?:\\s+[\\p{L}]{8,}){1,3}").matcher(normalized);
        if (matcher.find()) {
            return matcher.group();
        }

        throw new AssertionError("Could not pick a stable search query from extracted DOCX text");
    }

    private static String snippet(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n... (truncated)";
    }
}
