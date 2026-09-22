package com.innovationhub.project.infrastructure;

import com.innovationhub.AbstractSqlServerIntegrationTest;
import com.innovationhub.project.domain.InnovationArea;
import com.innovationhub.project.domain.Project;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerPersistenceIntegrationTest extends AbstractSqlServerIntegrationTest {

    @Autowired
    ProjectRepository repository;

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldRunFlywayAndPersistProjectAgainstRealSqlServer() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .containsIgnoringCase("Microsoft SQL Server");
        }

        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(3);

        var project = new Project(
                "PDI-IT-001",
                "Quality Hardening",
                "SQL Server integration test",
                InnovationArea.DIGITAL_TRANSFORMATION,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 3, 31),
                new BigDecimal("250000.00"),
                "Jucelio Coelho"
        );

        var saved = repository.saveAndFlush(project);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();

        var reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCode()).isEqualTo("PDI-IT-001");
        assertThat(reloaded.getBudget()).isEqualByComparingTo("250000.00");
    }
}
