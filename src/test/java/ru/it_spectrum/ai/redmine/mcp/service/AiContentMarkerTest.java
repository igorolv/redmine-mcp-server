package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiContentMarkerTest {

    private final AiContentMarker marker = new AiContentMarker();

    @Test
    void shouldMarkTextOnlyOnce() {
        assertThat(marker.markText("Investigate failure"))
                .isEqualTo("AI_EDIT:\n\nInvestigate failure");
        assertThat(marker.markText("AI_EDIT:\n\nInvestigate failure"))
                .isEqualTo("AI_EDIT:\n\nInvestigate failure");
        assertThat(marker.markText(null)).isEqualTo("AI_EDIT:");
    }

    @Test
    void shouldMarkFilenameAndPreserveExtensionWhenTruncated() {
        assertThat(marker.markFilename("report.pdf")).isEqualTo("AI_EDIT__report.pdf");
        assertThat(marker.markFilename("AI_EDIT__report.pdf")).isEqualTo("AI_EDIT__report.pdf");

        String longName = "a".repeat(300) + ".txt";
        String marked = marker.markFilename(longName);
        assertThat(marked).hasSize(255).startsWith("AI_EDIT__").endsWith(".txt");
    }

    @Test
    void shouldTruncateFilenameEvenWhenExtensionIsUnusuallyLong() {
        String marked = marker.markFilename("file." + "x".repeat(300));

        assertThat(marked).startsWith(AiContentMarker.FILE_PREFIX).hasSize(255);
    }
}
