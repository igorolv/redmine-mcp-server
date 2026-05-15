package ru.it_spectrum.ai.redmine.mcp.service;

public interface ProgressReporter {

    void stage(String message);

    void report(int current, int total, String message);

    void done(String message);

    ProgressReporter NOOP = new ProgressReporter() {
        @Override public void stage(String message) {}
        @Override public void report(int current, int total, String message) {}
        @Override public void done(String message) {}
    };
}
