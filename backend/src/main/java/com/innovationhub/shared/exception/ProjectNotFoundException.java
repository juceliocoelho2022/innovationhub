package com.innovationhub.shared.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long id) {
        super("Projeto não encontrado: " + id);
    }
}
