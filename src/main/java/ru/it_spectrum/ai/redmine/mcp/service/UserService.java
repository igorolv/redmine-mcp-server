package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.User;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

import java.util.Optional;

@Service
public class UserService {
    private final RedmineClient client;

    public UserService(RedmineClient client) {
        this.client = client;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(client.getCurrentUser()).map(User::from);
    }
}
