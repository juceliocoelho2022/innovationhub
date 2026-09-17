package com.innovationhub.project.api;

import com.innovationhub.project.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        var response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + response.id()))
                .body(response);
    }

    @GetMapping
    public Page<ProjectResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
