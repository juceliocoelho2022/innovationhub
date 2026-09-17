package com.innovationhub.dashboard;

import com.innovationhub.project.domain.ProjectStatus;
import com.innovationhub.project.infrastructure.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final ProjectRepository repository;

    public DashboardService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        return new DashboardSummary(
                repository.count(),
                repository.countByStatusIn(List.of(
                        ProjectStatus.APPROVED,
                        ProjectStatus.IN_PROGRESS,
                        ProjectStatus.AT_RISK
                )),
                repository.countByStatus(ProjectStatus.IN_PROGRESS),
                repository.countByStatus(ProjectStatus.AT_RISK),
                repository.countByStatus(ProjectStatus.COMPLETED),
                repository.sumBudget()
        );
    }
}