package dev.taskbound.core;

import java.util.List;

public record TemplateRecencyPolicy(double previousMultiplier, int recentWindow, double recentMultiplier) {
    public static final TemplateRecencyPolicy DEFAULT = new TemplateRecencyPolicy(0.1, 3, 0.35);
    public static final TemplateRecencyPolicy DISABLED = new TemplateRecencyPolicy(1.0, 0, 1.0);

    public TemplateRecencyPolicy {
        if (!validMultiplier(previousMultiplier) || !validMultiplier(recentMultiplier) || recentWindow < 0) {
            throw new IllegalArgumentException("Invalid template recency policy");
        }
    }

    public double multiplier(String templateId, List<String> recentTemplateIds) {
        int index = recentTemplateIds.indexOf(templateId);
        if (index == 0) {
            return previousMultiplier;
        }
        if (index > 0 && index < recentWindow) {
            return recentMultiplier;
        }
        return 1.0;
    }

    private static boolean validMultiplier(double value) {
        return Double.isFinite(value) && value > 0.0 && value <= 1.0;
    }
}
