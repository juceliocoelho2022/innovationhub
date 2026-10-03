package com.innovationhub.shared.exception;

public class ProjectVersionConflictException extends RuntimeException {

    public ProjectVersionConflictException(Long projectId, Long expectedVersion, Long currentVersion) {
        super("Conflito de versão no projeto %d: esperado %s, atual %s."
                .formatted(projectId, expectedVersion, currentVersion));
    }
}
