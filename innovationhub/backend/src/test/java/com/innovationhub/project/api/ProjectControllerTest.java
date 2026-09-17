package com.innovationhub.project.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovationhub.project.application.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
