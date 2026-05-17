package ru.it_spectrum.ai.redmine.mcp.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
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
        System.out.println("Private: " + issue.isPrivate());
        if (issue.assignedTo() != null) {
            System.out.println("Assigned to: " + issue.assignedTo().name());
        }
        if (issue.parent() != null) {
            System.out.println("Parent: #" + issue.parent().id() + " " + issue.parent().name());
        }
        if (issue.fixedVersion() != null) {
            System.out.println("Target version: " + issue.fixedVersion().name());
        }
        if (issue.category() != null) {
            System.out.println("Category: " + issue.category().name());
        }
        if (issue.estimatedHours() != null) {
            System.out.println("Estimated: " + issue.estimatedHours() + " h");
        }
        if (issue.spentHours() != null) {
            System.out.println("Spent: " + issue.spentHours() + " h");
        }
        if (issue.customFields() != null && !issue.customFields().isEmpty()) {
            System.out.println("\nCustom fields (" + issue.customFields().size() + "):");
            for (var cf : issue.customFields()) {
                System.out.println("  " + cf.name() + ": " + cf.value());
            }
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

    @Test
    void shouldLoadIssue4183Changesets() {
        RedmineIssue issue = redmineClient.getIssue(4183);

        assertThat(issue).isNotNull();
        assertThat(issue.changesets()).isNotEmpty();

        var first = issue.changesets().getFirst();
        assertThat(first.revision()).isNotBlank();
        assertThat(first.comments()).isNotNull();
        assertThat(first.committedOn()).isNotBlank();
        assertThat(first.user()).isNotNull();
        assertThat(first.user().name()).isNotBlank();

        System.out.println("Issue #" + issue.id() + " changesets: " + issue.changesets().size());
        for (var changeset : issue.changesets().stream().limit(5).toList()) {
            System.out.println("  " + changeset.revision() + " | "
                    + changeset.committedOn() + " | "
                    + (changeset.user() != null ? changeset.user().name() : "unknown"));
        }
    }

    @Test
    void shouldListProjects() {
        var page = redmineClient.getProjects(0, 100);

        assertThat(page).isNotNull();
        assertThat(page.projects()).isNotEmpty();

        System.out.println("Projects (" + page.totalCount() + " total):");
        for (var project : page.projects()) {
            System.out.println("  [" + project.identifier() + "] " + project.name() + " (id: " + project.id() + ")");
        }
    }

    @Test
    void shouldGetProjectDetails() {
        // get first project identifier from list
        var page = redmineClient.getProjects(0, 1);
        assertThat(page.projects()).isNotEmpty();
        String identifier = page.projects().getFirst().identifier();

        var project = redmineClient.getProject(identifier);

        assertThat(project).isNotNull();
        assertThat(project.name()).isNotBlank();
        assertThat(project.identifier()).isEqualTo(identifier);

        System.out.println("Project: " + project.name());
        System.out.println("Identifier: " + project.identifier());
        System.out.println("ID: " + project.id());
        if (project.parent() != null) {
            System.out.println("Parent: " + project.parent().name());
        }
        if (project.description() != null && !project.description().isBlank()) {
            System.out.println("Description: " + project.description());
        }
        System.out.println("Public: " + (project.isPublic() ? "yes" : "no"));
        System.out.println("Created: " + project.createdOn());
        if (project.trackers() != null) {
            System.out.println("Trackers: " + project.trackers().stream()
                    .map(t -> t.name()).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        if (project.enabledModules() != null) {
            System.out.println("Modules: " + project.enabledModules().stream()
                    .map(m -> m.name()).reduce((a, b) -> a + ", " + b).orElse(""));
        }
    }

    @Test
    void shouldListIssuesWithFilters() {
        var page = redmineClient.listIssues("asv_microservices", null, null, null, null, null,
                "updated_on:desc", 0, 5);

        assertThat(page).isNotNull();
        assertThat(page.issues()).isNotEmpty();

        System.out.println("Issues for asv_microservices (" + page.totalCount() + " total, showing 5):");
        for (var issue : page.issues()) {
            System.out.println("  #" + issue.id() + " [" + issue.status().name() + "] " + issue.subject());
        }
    }

    @Test
    void shouldListProjectMembers() {
        var page = redmineClient.getProjectMembers("asv_microservices", 0, 100);

        assertThat(page).isNotNull();
        assertThat(page.memberships()).isNotEmpty();

        System.out.println("Members of asv_microservices (" + page.totalCount() + "):");
        for (var m : page.memberships()) {
            String member = m.user() != null ? m.user().name() : (m.group() != null ? m.group().name() + " (group)" : "unknown");
            String roles = m.roles() != null
                    ? m.roles().stream().map(r -> r.name()).reduce((a, b) -> a + ", " + b).orElse("")
                    : "";
            System.out.println("  " + member + " — " + roles);
        }
    }

    @Test
    void shouldListProjectVersions() {
        var versions = redmineClient.getProjectVersions("asv_microservices");

        assertThat(versions).isNotNull();

        System.out.println("Versions of asv_microservices (" + versions.size() + "):");
        for (var v : versions) {
            System.out.println("  " + v.name() + " [" + v.status() + "]"
                    + (v.dueDate() != null ? " due: " + v.dueDate() : ""));
        }
    }

    @Test
    void shouldGetWikiPage() {
        var page = redmineClient.getWikiPage("asv_microservices", "Wiki");

        if (page != null) {
            System.out.println("Wiki page: " + page.title());
            if (page.author() != null) System.out.println("Author: " + page.author().name());
            System.out.println("Version: " + page.version());
            if (page.text() != null) {
                System.out.println("Content: " + page.text().substring(0, Math.min(300, page.text().length())) + "...");
            }
        } else {
            System.out.println("No wiki start page found for asv_microservices");
        }
    }

    @Test
    void shouldSearchAll() {
        var result = redmineClient.search("выплата", null, Set.of(), true, 0, 10);

        assertThat(result).isNotNull();
        assertThat(result.results()).isNotNull();
        assertThat(result.offset()).isZero();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.totalCount()).isGreaterThanOrEqualTo(result.results().size());

        System.out.println("Global search 'выплата' (" + result.totalCount() + " total, showing 10):");
        for (var item : result.results()) {
            System.out.println("  [" + item.type() + "] #" + item.id() + " " + item.title());
        }
    }

    @Test
    void shouldGetIssueStatuses() {
        var statuses = redmineClient.getIssueStatuses();

        assertThat(statuses).isNotEmpty();

        System.out.println("Issue statuses (" + statuses.size() + "):");
        for (var s : statuses) {
            System.out.println("  [" + s.id() + "] " + s.name());
        }
    }

    @Test
    void shouldGetTrackers() {
        var trackers = redmineClient.getTrackers();

        assertThat(trackers).isNotEmpty();

        System.out.println("Trackers (" + trackers.size() + "):");
        for (var t : trackers) {
            System.out.println("  [" + t.id() + "] " + t.name());
        }
    }

    @Test
    void shouldGetIssuePriorities() {
        var priorities = redmineClient.getIssuePriorities();

        assertThat(priorities).isNotEmpty();

        System.out.println("Issue priorities (" + priorities.size() + "):");
        for (var p : priorities) {
            System.out.println("  [" + p.id() + "] " + p.name());
        }
    }

    @Test
    void shouldGetTimeEntries() {
        var page = redmineClient.getTimeEntries(null, null, null, null, null, 0, 5);

        assertThat(page).isNotNull();
        assertThat(page.timeEntries()).isNotEmpty();

        System.out.println("Time entries (" + page.totalCount() + " total, showing 5):");
        for (var entry : page.timeEntries()) {
            System.out.println("  " + entry.spentOn() + " | " + entry.hours() + "h | "
                    + (entry.user() != null ? entry.user().name() : "—") + " | "
                    + (entry.activity() != null ? entry.activity().name() : "—")
                    + (entry.issue() != null ? " | #" + entry.issue().id() : ""));
        }
    }

    @Test
    void shouldGetWikiIndex() {
        var pages = redmineClient.getWikiIndex("asv_microservices");

        assertThat(pages).isNotNull();

        System.out.println("Wiki pages in asv_microservices (" + pages.size() + "):");
        for (var page : pages) {
            System.out.println("  " + page.title() + " (updated: " + page.updatedOn() + ")");
        }
    }

    @Test
    void shouldGetIssueCategories() {
        // use first project from the list
        var page = redmineClient.getProjects(0, 1);
        assertThat(page.projects()).isNotEmpty();
        String projectId = page.projects().getFirst().identifier();

        var categories = redmineClient.getIssueCategories(projectId);

        assertThat(categories).isNotNull();

        System.out.println("Issue categories for " + projectId + " (" + categories.size() + "):");
        for (var c : categories) {
            System.out.println("  [" + c.id() + "] " + c.name());
        }
    }

    @Test
    void shouldGetTimeEntryActivities() {
        var activities = redmineClient.getTimeEntryActivities();

        assertThat(activities).isNotEmpty();

        System.out.println("Time entry activities (" + activities.size() + "):");
        for (var a : activities) {
            System.out.println("  [" + a.id() + "] " + a.name());
        }
    }

    @Test
    void shouldGetCurrentUser() {
        var user = redmineClient.getCurrentUser();

        assertThat(user).isNotNull();
        assertThat(user.id()).isGreaterThan(0);
        assertThat(user.login()).isNotBlank();

        System.out.println("Current user: " + user.firstname() + " " + user.lastname());
        System.out.println("ID: " + user.id() + " | Login: " + user.login());
        if (user.mail() != null) System.out.println("Email: " + user.mail());
        System.out.println("Last login: " + user.lastLoginOn());
        if (user.groups() != null && !user.groups().isEmpty()) {
            System.out.println("Groups: " + user.groups().stream()
                    .map(g -> g.name()).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        if (user.memberships() != null) {
            System.out.println("Memberships: " + user.memberships().size() + " projects");
        }
    }
}
