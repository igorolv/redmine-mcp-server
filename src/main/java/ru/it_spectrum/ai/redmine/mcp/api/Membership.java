package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineMembership;

import java.util.List;

@Schema(description = "Project membership entry: either a single user or a group, together with the roles they hold in the project. Exactly one of `user` and `group` is set.")
public record Membership(
        @Schema(description = "User member, when this entry represents an individual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref user,
        @Schema(description = "Group member, when this entry represents a group.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Ref group,
        @Schema(description = "Roles the user or group holds in the project (Developer, Reporter, ...).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Ref> roles
) {
    public static Membership from(RedmineMembership source) {
        if (source == null) {
            return null;
        }
        var roles = source.roles() == null
                ? List.<Ref>of()
                : source.roles().stream().map(Ref::from).toList();
        return new Membership(Ref.from(source.user()), Ref.from(source.group()), roles);
    }
}
