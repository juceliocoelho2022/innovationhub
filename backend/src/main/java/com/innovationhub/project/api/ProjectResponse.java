package com.innovationhub.project.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectResponse(
        Long id,
        String code,
        String name,
        String description,
        String status,
        String innovationArea,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String managerName,
        Long version
) {
}
