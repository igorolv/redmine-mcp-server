package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncMcpPromptProvider;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentPromptsTest {

    private final IncidentPrompts prompts = new IncidentPrompts();

    @Test
    void incidentBriefIsAnnotatedAsMcpPrompt() throws Exception {
        Method m = IncidentPrompts.class.getMethod("incidentBrief", int.class);
        var annotation = m.getAnnotation(McpPrompt.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("incident-brief");
        assertThat(annotation.description()).isNotBlank();

        var arg = m.getParameters()[0].getAnnotation(McpArg.class);
        assertThat(arg).isNotNull();
        assertThat(arg.name()).isEqualTo("issueId");
        assertThat(arg.required()).isTrue();
    }

    @Test
    void incidentBriefSubstitutesIssueIdAndConstrainsToolUsage() {
        String text = prompts.incidentBrief(12345);
        assertThat(text).contains("12345");
        assertThat(text).contains("getIssue");
        assertThat(text).contains("getAttachment");
        assertThat(text).contains("focus=\"default\"");
        assertThat(text).contains("maxChars=300");
        assertThat(text).contains("partLimit=300");
        // Brief must explicitly cap previews to the first 10 of >15 attachments.
        assertThat(text).contains("15");
        assertThat(text).contains("first 10");
    }

    @Test
    void incidentImplementationUsesImplementationFocus() throws Exception {
        Method m = IncidentPrompts.class.getMethod("incidentImplementation", int.class);
        var annotation = m.getAnnotation(McpPrompt.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("incident-implementation");

        String text = prompts.incidentImplementation(12345);
        assertThat(text).contains("focus=\"implementation\"");
        assertThat(text).contains("getAttachment");
        assertThat(text).contains("maxChars=3000");
        assertThat(text).contains("Do not call getIssueJournal");
        assertThat(text).contains("Review Checklist");
    }

    @Test
    void incidentTimelineUsesTimelineFocusAndJournalFallback() throws Exception {
        Method m = IncidentPrompts.class.getMethod("incidentTimeline", int.class);
        var annotation = m.getAnnotation(McpPrompt.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("incident-timeline");

        String text = prompts.incidentTimeline(12345);
        assertThat(text).contains("focus=\"timeline\"");
        assertThat(text).contains("getIssueJournal");
        assertThat(text).contains("compressionNotes");
        assertThat(text).contains("Do not call getAttachment");
        assertThat(text).contains("Chronology");
    }

    @Test
    void syncProviderDiscoversPrompt() {
        var provider = new SyncMcpPromptProvider(List.of(prompts));
        var specs = provider.getPromptSpecifications();
        var names = specs.stream().map(s -> s.prompt().name()).toList();
        assertThat(names).containsExactly(
                "incident-brief",
                "incident-implementation",
                "incident-timeline");
    }
}
