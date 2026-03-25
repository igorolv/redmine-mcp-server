package ru.it_spectrum.ai.redmine.mcp.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedmineClientTest {

    @Autowired
    private RedmineClient redmineClient;

    @Test
    void shouldLoadIssue4053() {
        RedmineIssue issue = redmineClient.getIssue(4053);

        assertThat(issue).isNotNull();
        assertThat(issue.id()).isEqualTo(4053);
        assertThat(issue.subject()).isNotBlank();
        assertThat(issue.project()).isNotNull();
        assertThat(issue.status()).isNotNull();
        assertThat(issue.author()).isNotNull();

        System.out.println("Issue #" + issue.id());
        System.out.println("Subject: " + issue.subject());
        System.out.println("Project: " + issue.project().name());
        System.out.println("Status: " + issue.status().name());
        System.out.println("Author: " + issue.author().name());
        System.out.println("Created: " + issue.createdOn());
        if (issue.assignedTo() != null) {
            System.out.println("Assigned to: " + issue.assignedTo().name());
        }
        if (issue.description() != null) {
            System.out.println("Description: " + issue.description().substring(0, Math.min(200, issue.description().length())) + "...");
        }
        if (issue.relations() != null) {
            System.out.println("\nRelations (" + issue.relations().size() + "):");
            for (var rel : issue.relations()) {
                int relatedId = rel.issueId() == issue.id() ? rel.issueToId() : rel.issueId();
                System.out.println("  " + rel.relationType() + " #" + relatedId);
            }
        }
        if (issue.journals() != null) {
            System.out.println("\nNotes (" + issue.journals().size() + "):");
            for (var journal : issue.journals()) {
                if (journal.notes() != null && !journal.notes().isBlank()) {
                    System.out.println("  [" + journal.createdOn() + "] " + journal.user().name() + ":");
                    System.out.println("    " + journal.notes().replace("\n", "\n    "));
                }
            }
        }
    }
}
