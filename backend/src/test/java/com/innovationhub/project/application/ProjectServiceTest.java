package com.innovationhub.project.application;

import com.innovationhub.project.api.CreateProjectRequest;
import com.innovationhub.project.domain.Project;
import com.innovationhub.project.infrastructure.ProjectRepository;
import com.innovationhub.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository repository;

    @Test
    void shouldCreateProjectAsDraft() {
        var service = new ProjectService(repository);
        var request = new CreateProjectRequest(
                "Smart Factory AI",
                "Inspeção industrial com visão computacional",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 3, 31),
                new BigDecimal("850000.00"),
                "Jucelio Coelho"
        );

        when(repository.save(org.mockito.ArgumentMatchers.any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request);

        var captor = ArgumentCaptor.forClass(Project.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());

        assertThat(response.name()).isEqualTo("Smart Factory AI");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.code()).startsWith("PDI-");
        assertThat(captor.getValue().getBudget()).isEqualByComparingTo("850000.00");
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {
        var service = new ProjectService(repository);
        var request = new CreateProjectRequest(
                "Projeto inválido",
                "Datas inválidas",
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 1),
                new BigDecimal("1000"),
                "Gerente"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("data final");
    }
}
