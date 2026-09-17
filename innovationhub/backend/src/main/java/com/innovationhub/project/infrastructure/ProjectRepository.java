package com.innovationhub.project.infrastructure;

import com.innovationhub.project.domain.Project;
import com.innovationhub.project.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    long countByStatus(ProjectStatus status);

    long countByStatusNot(ProjectStatus status);

    @Query("select coalesce(sum(p.budget), 0) from Project p")
    BigDecimal sumBudget();
}
