package ru.it_spectrum.ai.redmine.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdName(int id, String name) {
}
