package com.innovationhub.project.infrastructure;

import com.innovationhub.AbstractSqlServerIntegrationTest;
import com.innovationhub.project.domain.InnovationArea;
import com.innovationhub.project.domain.Project;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectOptimisticLockingIntegrationTest extends AbstractSqlServerIntegrationTest {

    @Autowired
    ProjectRepository repository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Long projectId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        var project = repository.saveAndFlush(new Project(
                "PDI-LOCK-001",
                "Concurrent Project",
                "Optimistic locking scenario",
                InnovationArea.INDUSTRY_4_0,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 6, 30),
                new BigDecimal("500000.00"),
                "Manager A"
        ));

        projectId = project.getId();
    }

    @Test
    void shouldRejectStaleConcurrentUpdate() {
        var em1 = entityManagerFactory.createEntityManager();
        var em2 = entityManagerFactory.createEntityManager();

        try {
            em1.getTransaction().begin();
            em2.getTransaction().begin();

            var firstCopy = em1.find(Project.class, projectId);
            var staleCopy = em2.find(Project.class, projectId);

            assertThat(firstCopy.getVersion()).isEqualTo(staleCopy.getVersion());

            ReflectionTestUtils.setField(firstCopy, "name", "First committed update");
            ReflectionTestUtils.setField(staleCopy, "name", "Stale update");

            em1.getTransaction().commit();

            assertThatThrownBy(() -> em2.getTransaction().commit())
                    .isInstanceOf(RollbackException.class);
        } finally {
            if (em1.getTransaction().isActive()) {
                em1.getTransaction().rollback();
            }
            if (em2.getTransaction().isActive()) {
                em2.getTransaction().rollback();
            }
            em1.close();
            em2.close();
        }

        var persisted = repository.findById(projectId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("First committed update");
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }
}
