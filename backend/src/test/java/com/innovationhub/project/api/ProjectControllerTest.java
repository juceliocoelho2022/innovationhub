package com.innovationhub.project.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovationhub.project.application.ProjectService;
import com.innovationhub.project.domain.InnovationArea;
import com.innovationhub.project.domain.Project;
import com.innovationhub.shared.exception.BusinessRuleException;
import com.innovationhub.shared.exception.ProjectNotFoundException;
import com.innovationhub.shared.exception.ProjectVersionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectService service;

    @Test
    void shouldReturnCreatedProject() throws Exception {
        var request = new CreateProjectRequest(
                "AI Vision",
                "Visão computacional",
                InnovationArea.ARTIFICIAL_INTELLIGENCE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 1, 31),
                new BigDecimal("1200000"),
                "Mariana Costa"
        );

        when(service.create(any())).thenReturn(new ProjectResponse(
                1L,
                "PDI-2026-ABC12345",
                "AI Vision",
                "Visão computacional",
                "DRAFT",
                "ARTIFICIAL_INTELLIGENCE",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 1, 31),
                new BigDecimal("1200000"),
                "Mariana Costa",
                0L
        ));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PDI-2026-ABC12345"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.innovationArea").value("ARTIFICIAL_INTELLIGENCE"));
    }

    @Test
    void shouldUpdateProject() throws Exception {
        var request = validUpdateRequest(5L);
        var response = new ProjectResponse(
                1L,
                "PDI-2026-ABC12345",
                "AI Vision Updated",
                "Nova descrição",
                "DRAFT",
                "ARTIFICIAL_INTELLIGENCE",
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2027, 2, 28),
                new BigDecimal("1300000.00"),
                "Mariana Costa",
                6L
        );

        when(service.update(eq(1L), eq(request))).thenReturn(response);

        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AI Vision Updated"))
                .andExpect(jsonPath("$.budget").value(1300000.00))
                .andExpect(jsonPath("$.version").value(6));

        verify(service).update(1L, request);
    }

    @Test
    void shouldRejectUpdateWithoutVersion() throws Exception {
        var payload = """
                {
                  "name": "AI Vision Updated",
                  "description": "Nova descrição",
                  "innovationArea": "ARTIFICIAL_INTELLIGENCE",
                  "startDate": "2026-10-02",
                  "endDate": "2027-02-28",
                  "budget": 1300000.00,
                  "managerName": "Mariana Costa"
                }
                """;

        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUpdateWithNegativeVersion() throws Exception {
        var request = validUpdateRequest(-1L);

        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingProject() throws Exception {
        var request = validUpdateRequest(5L);
        when(service.update(eq(1L), any(UpdateProjectRequest.class)))
                .thenThrow(new ProjectNotFoundException(1L));

        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUnprocessableEntityForInvalidProjectPeriod() throws Exception {
        var request = validUpdateRequest(5L);
        when(service.update(eq(1L), any(UpdateProjectRequest.class)))
                .thenThrow(new BusinessRuleException("A data final não pode ser anterior à data inicial."));

        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldReturnConflictForStaleProjectVersion() throws Exception {
        var request = validUpdateRequest(5L);
        when(service.update(eq(1L), any(UpdateProjectRequest.class)))
                .thenThrow(new ProjectVersionConflictException(1L, 5L, 6L));

        assertVersionConflict(request);
    }

    @Test
    void shouldReturnSameConflictContractForOptimisticLockingFailure() throws Exception {
        var request = validUpdateRequest(5L);
        when(service.update(eq(1L), any(UpdateProjectRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertVersionConflict(request);
    }

    private void assertVersionConflict(UpdateProjectRequest request) throws Exception {
        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://innovationhub.local/problems/version-conflict"))
                .andExpect(jsonPath("$.title").value("Conflito de versão"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "O projeto foi alterado por outro usuário. Recarregue os dados antes de tentar novamente."
                ));
    }

    private UpdateProjectRequest validUpdateRequest(Long version) {
        return new UpdateProjectRequest(
                version,
                "AI Vision Updated",
                "Nova descrição",
                InnovationArea.ARTIFICIAL_INTELLIGENCE,
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2027, 2, 28),
                new BigDecimal("1300000.00"),
                "Mariana Costa"
        );
    }
}
