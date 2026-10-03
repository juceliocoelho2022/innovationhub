package com.innovationhub.project.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovationhub.AbstractSqlServerIntegrationTest;
import com.innovationhub.project.api.UpdateProjectRequest;
import com.innovationhub.project.domain.InnovationArea;
import com.innovationhub.project.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectUpdateApiIntegrationTest extends AbstractSqlServerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProjectRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldUpdateProjectAndIncrementVersionInSqlServer() throws Exception {
        var project = persistProject("PDI-UPDATE-001", "Original Project", new BigDecimal("500000.00"));
        var initialVersion = project.getVersion();
        var originalCode = project.getCode();
        var originalStatus = project.getStatus();

        var request = new UpdateProjectRequest(
                initialVersion,
                "Updated Project",
                "Updated through the real HTTP and SQL Server path",
                InnovationArea.ARTIFICIAL_INTELLIGENCE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 7, 31),
                new BigDecimal("650000.00"),
                "Manager B"
        );

        mockMvc.perform(put("/api/v1/projects/{id}", project.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Project"))
                .andExpect(jsonPath("$.version").value(initialVersion + 1));

        var persisted = repository.findById(project.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Updated Project");
        assertThat(persisted.getDescription())
                .isEqualTo("Updated through the real HTTP and SQL Server path");
        assertThat(persisted.getBudget()).isEqualByComparingTo("650000.00");
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(persisted.getCode()).isEqualTo(originalCode);
        assertThat(persisted.getStatus()).isEqualTo(originalStatus);
    }

    @Test
    void shouldReturnConflictWithoutOverwritingLatestState() throws Exception {
        var project = persistProject("PDI-UPDATE-002", "Original Project", new BigDecimal("500000.00"));
        var initialVersion = project.getVersion();

        var firstUpdate = new UpdateProjectRequest(
                initialVersion,
                "First committed update",
                "Authoritative state",
                InnovationArea.INDUSTRY_4_0,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                new BigDecimal("700000.00"),
                "Manager First"
        );

        mockMvc.perform(put("/api/v1/projects/{id}", project.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(firstUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(initialVersion + 1));

        var staleUpdate = new UpdateProjectRequest(
                initialVersion,
                "Stale update",
                "Must not overwrite the first update",
                InnovationArea.OTHER,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 9, 30),
                new BigDecimal("100.00"),
                "Stale Manager"
        );

        mockMvc.perform(put("/api/v1/projects/{id}", project.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(staleUpdate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://innovationhub.local/problems/version-conflict"))
                .andExpect(jsonPath("$.title").value("Conflito de versão"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "O projeto foi alterado por outro usuário. Recarregue os dados antes de tentar novamente."
                ));

        var persisted = repository.findById(project.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("First committed update");
        assertThat(persisted.getDescription()).isEqualTo("Authoritative state");
        assertThat(persisted.getBudget()).isEqualByComparingTo("700000.00");
        assertThat(persisted.getManagerName()).isEqualTo("Manager First");
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
    }

    private Project persistProject(String code, String name, BigDecimal budget) {
        return repository.saveAndFlush(new Project(
                code,
                name,
                "Initial description",
                InnovationArea.INDUSTRY_4_0,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 6, 30),
                budget,
                "Manager A"
        ));
    }
}
