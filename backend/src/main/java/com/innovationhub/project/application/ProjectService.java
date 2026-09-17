package com.innovationhub.project.application;

import com.innovationhub.project.api.CreateProjectRequest;
import com.innovationhub.project.api.ProjectResponse;
import com.innovationhub.project.domain.Project;
import com.innovationhub.project.infrastructure.ProjectRepository;
import com.innovationhub.shared.exception.BusinessRuleException;
import com.innovationhub.shared.exception.ProjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        validateDates(request);

        var project = new Project(
                generateCode(),
                request.name().trim(),
                request.description(),
                request.innovationArea(),
                request.startDate(),
                request.endDate(),
                request.budget(),
                request.managerName().trim()
        );

        return toResponse(repository.save(project));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private void validateDates(CreateProjectRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("A data final não pode ser anterior à data inicial.");
        }
    }

    private String generateCode() {
        var suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);

        return "PDI-" + Year.now().getValue() + "-" + suffix;
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getInnovationArea().name(),
                project.getStartDate(),
                project.getEndDate(),
                project.getBudget(),
                project.getManagerName(),
                project.getVersion()
        );
    }
}
