package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.Test;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiContentMarkerTest {

    private final AiContentMarker defaults = new AiContentMarker(TestRedmineMcpProperties.defaults());
    private final AiContentMarker configured = new AiContentMarker(TestRedmineMcpProperties.withWritePrefixes(
            "AI_EDIT:", "[AI note]", "[AI time]", "[AI wiki]", "AI_EDIT__"));

    @Test
    void shouldMarkOnlyIssueDescriptionByDefault() {
        assertThat(defaults.markIssueDescription("Investigate failure"))
                .isEqualTo("AI_EDIT:\n\nInvestigate failure");
        assertThat(defaults.markIssueDescription(null)).isEqualTo("AI_EDIT:");

        assertThat(defaults.markIssueNote("Note")).isEqualTo("Note");
        assertThat(defaults.markTimeEntryComment("Work")).isEqualTo("Work");
        assertThat(defaults.markTimeEntryComment(null)).isNull();
        assertThat(defaults.markWikiComment("Draft")).isEqualTo("Draft");
        assertThat(defaults.markFilename("report.pdf")).isEqualTo("report.pdf");
    }

    @Test
    void shouldUseMultilineSeparatorForBodiesAndSpaceForSingleLineFields() {
        assertThat(configured.markIssueNote("Note")).isEqualTo("[AI note]\n\nNote");
        assertThat(configured.markTimeEntryComment("Work")).isEqualTo("[AI time] Work");
        assertThat(configured.markWikiComment("Draft")).isEqualTo("[AI wiki] Draft");
        assertThat(configured.markTimeEntryComment("  ")).isEqualTo("[AI time]");
    }

    @Test
    void shouldMarkTextOnlyOnce() {
        assertThat(configured.markIssueDescription("AI_EDIT:\n\nInvestigate failure"))
                .isEqualTo("AI_EDIT:\n\nInvestigate failure");
        assertThat(configured.markTimeEntryComment("[AI time] Work")).isEqualTo("[AI time] Work");
    }

    @Test
    void shouldStripConfiguredPrefixWhitespace() {
        var marker = new AiContentMarker(TestRedmineMcpProperties.withWritePrefixes(
                "  AI:  ", null, "[AI] ", null, null));

        assertThat(marker.markIssueDescription("Text")).isEqualTo("AI:\n\nText");
        assertThat(marker.markTimeEntryComment("Work")).isEqualTo("[AI] Work");
    }

    @Test
    void shouldMarkFilenameAndPreserveExtensionWhenTruncated() {
        assertThat(configured.markFilename("report.pdf")).isEqualTo("AI_EDIT__report.pdf");
        assertThat(configured.markFilename("AI_EDIT__report.pdf")).isEqualTo("AI_EDIT__report.pdf");

        String longName = "a".repeat(300) + ".txt";
        assertThat(configured.markFilename(longName)).hasSize(255).startsWith("AI_EDIT__").endsWith(".txt");
        assertThat(defaults.markFilename(longName)).hasSize(255).startsWith("aaa").endsWith(".txt");
    }

    @Test
    void shouldTruncateFilenameEvenWhenExtensionIsUnusuallyLong() {
        String marked = configured.markFilename("file." + "x".repeat(300));

        assertThat(marked).startsWith("AI_EDIT__").hasSize(255);
    }

    @Test
    void shouldRejectFilenamePrefixThatRedmineWouldRewrite() {
        assertThatThrownBy(() -> new AiContentMarker(TestRedmineMcpProperties.withWritePrefixes(
                null, null, null, null, "ai/")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attachment-filename-prefix");
        assertThatThrownBy(() -> new AiContentMarker(TestRedmineMcpProperties.withWritePrefixes(
                null, null, null, null, "x".repeat(255))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shorter than 255");
    }
}
