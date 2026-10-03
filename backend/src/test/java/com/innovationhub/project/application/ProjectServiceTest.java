package com.innovationhub.project.application;

import com.innovationhub.project.api.CreateProjectRequest;
import com.innovationhub.project.api.UpdateProjectRequest;
import com.innovationhub.project.domain.InnovationArea;
import com.innovationhub.project.domain.Project;
import com.innovationhub.project.infrastructure.ProjectRepository;
import com.innovationhub.shared.exception.BusinessRuleException;
import com.innovationhub.shared.exception.ProjectNotFoundException;
import com.innovationhub.shared.exception.ProjectVersionConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository repository;

    @Test
    void shouldCreateProjectAsDraftWithInnovationArea() {
        var service = new ProjectService(repository);

        var request = new CreateProjectRequest(
                "Smart Factory AI",
                "Inspeção industrial com visão computacional",
                InnovationArea.ARTIFICIAL_INTELLIGENCE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 3, 31),
                new BigDecimal("850000.00"),
                "Jucelio Coelho"
        );

        when(repository.save(org.mockito.ArgumentMatchers.any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request);

        var captor = ArgumentCaptor.forClass(Project.class);
        verify(repository).save(captor.capture());

        assertThat(response.name()).isEqualTo("Smart Factory AI");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.code()).startsWith("PDI-");
        assertThat(response.innovationArea())
                .isEqualTo("ARTIFICIAL_INTELLIGENCE");
        assertThat(captor.getValue().getInnovationArea())
                .isEqualTo(InnovationArea.ARTIFICIAL_INTELLIGENCE);
        assertThat(captor.getValue().getBudget())
                .isEqualByComparingTo("850000.00");
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {
        var service = new ProjectService(repository);

        var request = new CreateProjectRequest(
                "Projeto inválido",
                "Datas inválidas",
                InnovationArea.OTHER,
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 1),
                new BigDecimal("1000"),
                "Gerente"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("data final");
    }

    @Test
    void shouldUpdateProjectAndReturnVersionAfterFlush() {
        var service = new ProjectService(repository);
        var project = existingProject(5L);
        var originalCode = project.getCode();
        var originalStatus = project.getStatus();

        when(repository.findById(10L)).thenReturn(Optional.of(project));
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(project, "version", 6L);
            return null;
        }).when(repository).flush();

        var request = new UpdateProjectRequest(
                5L,
                "  Smart Factory AI  ",
                "Nova descrição",
                InnovationArea.ARTIFICIAL_INTELLIGENCE,
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2027, 4, 30),
                new BigDecimal("900000.00"),
                "  Jucelio Coelho  "
        );

        var response = service.update(10L, request);

        assertThat(response.name()).isEqualTo("Smart Factory AI");
        assertThat(response.managerName()).isEqualTo("Jucelio Coelho");
        assertThat(response.description()).isEqualTo("Nova descrição");
        assertThat(response.innovationArea()).isEqualTo("ARTIFICIAL_INTELLIGENCE");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(response.budget()).isEqualByComparingTo("900000.00");
        assertThat(response.version()).isEqualTo(6L);
        assertThat(response.code()).isEqualTo(originalCode);
        assertThat(response.status()).isEqualTo(originalStatus.name());
        verify(repository).flush();
    }

    @Test
    void shouldRejectStaleProjectVersionWithoutMutatingOrFlushing() {
        var service = new ProjectService(repository);
        var project = existingProject(6L);
        var originalName = project.getName();
        var originalBudget = project.getBudget();

        when(repository.findById(10L)).thenReturn(Optional.of(project));

        var request = new UpdateProjectRequest(
                5L,
                "Nome obsoleto",
                "Descrição obsoleta",
                InnovationArea.OTHER,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 5, 31),
                new BigDecimal("100.00"),
                "Outro gerente"
        );

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(ProjectVersionConflictException.class);

        assertThat(project.getName()).isEqualTo(originalName);
        assertThat(project.getBudget()).isEqualByComparingTo(originalBudget);
        verify(repository, never()).flush();
    }

    @Test
    void shouldRejectUpdateWhenProjectDoesNotExist() {
        var service = new ProjectService(repository);
        when(repository.findById(404L)).thenReturn(Optional.empty());

        var request = validUpdateRequest(0L);

        assertThatThrownBy(() -> service.update(404L, request))
                .isInstanceOf(ProjectNotFoundException.class);

        verify(repository, never()).flush();
    }

    @Test
    void shouldRejectUpdateWhenEndDateIsBeforeStartDate() {
        var service = new ProjectService(repository);
        var project = existingProject(2L);
        when(repository.findById(10L)).thenReturn(Optional.of(project));

        var request = new UpdateProjectRequest(
                2L,
                "Projeto inválido",
                "Período inválido",
                InnovationArea.OTHER,
                LocalDate.of(2027, 1, 10),
                LocalDate.of(2027, 1, 1),
                new BigDecimal("5000.00"),
                "Gerente"
        );

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("data final");

        verify(repository, never()).flush();
    }

    private Project existingProject(Long version) {
        var project = new Project(
                "PDI-2026-LOCK0001",
                "Projeto atual",
                "Descrição atual",
                InnovationArea.INDUSTRY_4_0,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 6, 30),
                new BigDecimal("500000.00"),
                "Manager A"
        );
        ReflectionTestUtils.setField(project, "id", 10L);
        ReflectionTestUtils.setField(project, "version", version);
        return project;
    }

    private UpdateProjectRequest validUpdateRequest(Long version) {
        return new UpdateProjectRequest(
                version,
                "Projeto atualizado",
                "Descrição atualizada",
                InnovationArea.ARTIFICIAL_INTELLIGENCE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                new BigDecimal("600000.00"),
                "Manager B"
        );
    }
}
