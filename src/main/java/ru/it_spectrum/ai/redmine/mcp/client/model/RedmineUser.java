package ru.it_spectrum.ai.redmine.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedmineUser(
        int id,
        String login,
        String firstname,
        String lastname,
        String mail,
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("created_on") String createdOn,
        @JsonProperty("updated_on") String updatedOn,
        @JsonProperty("last_login_on") String lastLoginOn,
        List<IdName> groups,
        List<RedmineMembership> memberships
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Single(RedmineUser user) {
    }
}
