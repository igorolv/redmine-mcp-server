package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;

import java.nio.file.Files;
import java.nio.file.Path;

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

        String content = readAttachmentContext(attachment);
        var contentJson = ToolJsonTestSupport.parse(content);
        String contentBody = contentJson.get("parts").get(0).get("content").asText();
        String directText = contentBody;

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
        System.out.println("AttachmentTools.getAttachment preview:");
        System.out.println(snippet(content, 2_000));
    }

    @Test
    void shouldLoadFirstPngAttachmentFromIssue4202AsOriginalFile() throws Exception {
        var issue = loadIssue4202();
        var attachment = findFirstImageAttachment(issue);

        String result = attachmentTools.getAttachment(ISSUE_ID, attachment.id());
        var json = ToolJsonTestSupport.parse(result);

        assertThat(json.get("attachment").get("filename").asText()).isEqualTo(attachment.filename());
        Path localPath = Path.of(json.get("localPath").asText());
        assertThat(localPath).exists().isRegularFile();
        assertThat(Files.size(localPath)).isGreaterThan(0);

        System.out.println();
        System.out.println("PNG attachment: #" + attachment.id() + " " + attachment.filename());
        System.out.println("Local file: " + localPath);
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

    private String readAttachmentContext(RedmineAttachment attachment) {
        try {
            return attachmentTools.getAttachment(ISSUE_ID, attachment.id());
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
