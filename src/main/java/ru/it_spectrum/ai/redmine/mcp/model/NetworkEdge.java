package ru.it_spectrum.ai.redmine.mcp.model;

public record NetworkEdge(int fromId, int toId, String type, Integer delay) {
}
