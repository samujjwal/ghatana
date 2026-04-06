package com.ghatana.yappc.ai.requirements.application.requirement;

import com.ghatana.yappc.ai.requirements.ai.RequirementQualityResult;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Evaluates requirement quality during write flows so quality data can be returned immediately
 * @doc.layer product
 * @doc.pattern Service
 */
public final class QualityValidator {
  private static final Pattern ACTIONABLE_LANGUAGE =
      Pattern.compile("\\b(must|should|shall|can|will)\\b", Pattern.CASE_INSENSITIVE);

  public Promise<RequirementQualityResult> validate(String requirementText) {
    String normalized = requirementText == null ? "" : requirementText.trim();
    int wordCount = normalized.isBlank() ? 0 : normalized.split("\\s+").length;

    double clarityScore = scoreClarity(normalized, wordCount);
    double completenessScore = scoreCompleteness(normalized, wordCount);
    double testabilityScore = scoreTestability(normalized);
    double consistencyScore = scoreConsistency(normalized);
    double overallScore = round((clarityScore + completenessScore + testabilityScore + consistencyScore) / 4.0);

    List<RequirementQualityResult.QualityIssue> issues = new ArrayList<>();
    List<String> recommendations = new ArrayList<>();

    if (clarityScore < 0.7) {
      issues.add(new RequirementQualityResult.QualityIssue(
          "clarity", "Requirement text should describe one concrete user-visible behavior", false));
      recommendations.add("Use explicit language that states who does what and when.");
    }
    if (completenessScore < 0.7) {
      issues.add(new RequirementQualityResult.QualityIssue(
          "completeness", "Requirement is too short to capture its full intent", false));
      recommendations.add("Add the actor, expected behavior, and any constraints.");
    }
    if (testabilityScore < 0.7) {
      issues.add(new RequirementQualityResult.QualityIssue(
          "testability", "Requirement lacks actionable language for verification", false));
      recommendations.add("Include language like must, should, or can so the requirement is easier to test.");
    }
    if (recommendations.isEmpty()) {
      recommendations.add("Requirement is actionable and ready for deeper review.");
    }

    return Promise.of(RequirementQualityResult.builder()
        .overallScore(overallScore)
        .clarityScore(clarityScore)
        .completenessScore(completenessScore)
        .testabilityScore(testabilityScore)
        .consistencyScore(consistencyScore)
        .issues(issues)
        .recommendations(recommendations)
        .build());
  }

  private double scoreClarity(String normalized, int wordCount) {
    if (normalized.isBlank()) {
      return 0.0;
    }
    double score = wordCount >= 8 ? 0.8 : 0.55;
    if (Character.isUpperCase(normalized.charAt(0))) {
      score += 0.1;
    }
    if (normalized.endsWith(".")) {
      score += 0.05;
    }
    return clamp(score);
  }

  private double scoreCompleteness(String normalized, int wordCount) {
    if (normalized.isBlank()) {
      return 0.0;
    }
    double score = wordCount >= 12 ? 0.85 : wordCount >= 6 ? 0.65 : 0.4;
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.contains("when ") || lower.contains("if ")) {
      score += 0.1;
    }
    return clamp(score);
  }

  private double scoreTestability(String normalized) {
    if (normalized.isBlank()) {
      return 0.0;
    }
    double score = ACTIONABLE_LANGUAGE.matcher(normalized).find() ? 0.85 : 0.45;
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.contains("within ") || lower.contains("error") || lower.contains("success")) {
      score += 0.05;
    }
    return clamp(score);
  }

  private double scoreConsistency(String normalized) {
    if (normalized.isBlank()) {
      return 0.0;
    }
    double score = normalized.length() <= 240 ? 0.8 : 0.6;
    if (!normalized.contains("  ")) {
      score += 0.05;
    }
    return clamp(score);
  }

  private double clamp(double value) {
    return round(Math.max(0.0, Math.min(1.0, value)));
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}