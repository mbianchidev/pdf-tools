package com.pdftools.operations;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LegacyWorkspaceRegistry {

    private final Set<Path> activeWorkspaces = ConcurrentHashMap.newKeySet();

    public void register(Path workspace) {
        activeWorkspaces.add(normalize(workspace));
    }

    public void unregister(Path workspace) {
        if (workspace != null) {
            activeWorkspaces.remove(normalize(workspace));
        }
    }

    public boolean isActive(Path workspace) {
        return activeWorkspaces.contains(normalize(workspace));
    }

    private Path normalize(Path workspace) {
        return workspace.toAbsolutePath().normalize();
    }
}
