package com.innovationhub.dashboard;

import com.innovationhub.project.domain.ProjectStatus;
import com.innovationhub.project.infrastructure.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void shouldAggregatePortfolioSummary() {
        var repository = mock(ProjectRepository.class);

        when(repository.count()).thenReturn(18L);
        when(repository.countByStatusNot(ProjectStatus.CANCELLED)).thenReturn(18L);
        when(repository.countByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(11L);
        when(repository.countByStatus(ProjectStatus.AT_RISK)).thenReturn(4L);
        when(repository.countByStatus(ProjectStatus.COMPLETED)).thenReturn(3L);
        when(repository.sumBudget()).thenReturn(new BigDecimal("4200000.00"));

        var service = new DashboardService(repository);
        var summary = service.summary();

        assertThat(summary.totalProjects()).isEqualTo(18);
        assertThat(summary.inProgress()).isEqualTo(11);
        assertThat(summary.atRisk()).isEqualTo(4);
        assertThat(summary.totalBudget()).isEqualByComparingTo("4200000.00");
    }
}
