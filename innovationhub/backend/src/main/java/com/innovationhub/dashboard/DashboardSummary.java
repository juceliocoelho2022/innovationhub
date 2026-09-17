package com.innovationhub.dashboard;

import java.math.BigDecimal;

public record DashboardSummary(
        long totalProjects,
        long activeProjects,
        long inProgress,
        long atRisk,
        long completed,
        BigDecimal totalBudget
) {
}
