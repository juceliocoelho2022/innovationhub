package com.innovationhub.shared.config;

import com.innovationhub.project.domain.InnovationArea;
import com.innovationhub.project.domain.Project;
import com.innovationhub.project.infrastructure.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@Profile("dev")
public class DataSeeder {

    @Bean
    CommandLineRunner seedProjects(ProjectRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }

            repository.save(new Project(
                    "PDI-2026-AIVISION",
                    "AI Vision",
                    "Visão computacional para inspeção industrial",
                    InnovationArea.ARTIFICIAL_INTELLIGENCE,
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2027, 4, 30),
                    new BigDecimal("1200000"),
                    "Mariana Costa"
            ));

            repository.save(new Project(
                    "PDI-2026-SMARTFAC",
                    "Smart Factory",
                    "Otimização de processos industriais",
                    InnovationArea.INDUSTRY_4_0,
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2027, 5, 22),
                    new BigDecimal("980000"),
                    "Carlos Mendes"
            ));

            repository.save(new Project(
                    "PDI-2026-GREENENG",
                    "Green Energy",
                    "Soluções sustentáveis em energia",
                    InnovationArea.SUSTAINABILITY,
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2027, 4, 28),
                    new BigDecimal("850000"),
                    "Ana Oliveira"
            ));

            repository.save(new Project(
                    "PDI-2026-DATALAB",
                    "DataLab",
                    "Plataforma de dados para PD&I",
                    InnovationArea.DIGITAL_TRANSFORMATION,
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2027, 4, 10),
                    new BigDecimal("420000"),
                    "Rafael Lima"
            ));
        };
    }
}
