package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;

import java.util.List;

@Schema(description = "Paginated slice of time entries.")
public record TimeEntryPage(
        @Schema(description = "Time entries on this page.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<TimeEntry> timeEntries,
        @Schema(description = "Total number of time entries across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,
        @Schema(description = "Zero-based offset of the first entry on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Maximum number of entries that may appear on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit
) {
    public static TimeEntryPage from(RedmineTimeEntry.Page source) {
        if (source == null) {
            return null;
        }
        var items = source.timeEntries() == null
                ? List.<TimeEntry>of()
                : source.timeEntries().stream().map(TimeEntry::from).toList();
        return new TimeEntryPage(items, source.totalCount(), source.offset(), source.limit());
    }
}
