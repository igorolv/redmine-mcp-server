package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineMembership;

import java.util.List;

@Schema(description = "Paginated slice of project memberships.")
public record MembershipPage(
        @Schema(description = "Memberships on this page.", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        List<Membership> members,
        @Schema(description = "Total number of memberships across all pages.", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,
        @Schema(description = "Zero-based offset of the first membership on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Maximum number of memberships that may appear on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit
) {
    public static MembershipPage from(RedmineMembership.Page source) {
        if (source == null) {
            return null;
        }
        var items = source.memberships() == null
                ? List.<Membership>of()
                : source.memberships().stream().map(Membership::from).toList();
        return new MembershipPage(items, source.totalCount(), source.offset(), source.limit());
    }
}
