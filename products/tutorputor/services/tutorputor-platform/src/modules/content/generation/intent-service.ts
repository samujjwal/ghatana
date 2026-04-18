/**
 * Content Generation Intent Inference Service
 *
 * AI-powered inference of audience, subject, learning objectives,
 * and content types from topic input.
 *
 * @doc.type service
 * @doc.purpose Infer generation parameters from topic input
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";

export interface InferredIntent {
  topic: string;
  subject: string;
  gradeLevel: string;
  difficultyLevel: string;
  contentTypes: string[];
  learningObjectives: string[];
  confidence: number;
  reasoning: string[];
}

export interface InferenceOptions {
  tenantId: string;
  topic: string;
  userId: string | undefined;
  preferredDomain: string | undefined;
}

/**
 * Service for inferring content generation intent from topic input
 */
export class IntentInferenceService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Infer all generation parameters from a topic string
   */
  async inferIntent(options: InferenceOptions): Promise<InferredIntent> {
    const { topic, tenantId, preferredDomain } = options;
    const normalizedTopic = topic.toLowerCase().trim();

    // Get tenant context for better inference
    const tenantContext = await this.getTenantContext(tenantId);

    // Run inference in parallel
    const [subject, gradeLevel, difficultyLevel, contentTypes] = await Promise.all([
      this.inferSubject(normalizedTopic, tenantContext, preferredDomain),
      this.inferGradeLevel(normalizedTopic),
      this.inferDifficultyLevel(normalizedTopic),
      this.inferContentTypes(normalizedTopic, tenantContext),
    ]);

    // Generate learning objectives based on inferred parameters
    const learningObjectives = this.generateLearningObjectives(topic, subject, gradeLevel);

    // Calculate overall confidence
    const confidence = this.calculateConfidence(subject, gradeLevel, contentTypes, tenantContext);

    // Build reasoning
    const reasoning = this.buildReasoning(subject, gradeLevel, contentTypes, tenantContext);

    return {
      topic,
      subject,
      gradeLevel,
      difficultyLevel,
      contentTypes,
      learningObjectives,
      confidence,
      reasoning,
    };
  }

  /**
   * Infer subject from topic keywords
   */
  private async inferSubject(
    topic: string,
    tenantContext: TenantContext,
    preferredDomain?: string,
  ): Promise<string> {
    // Subject keyword mappings
    const subjectKeywords: Record<string, string[]> = {
      Mathematics: [
        "math",
        "algebra",
        "geometry",
        "calculus",
        "equation",
        "fraction",
        "number",
        "arithmetic",
        "statistics",
        "probability",
      ],
      Physics: [
        "physics",
        "force",
        "motion",
        "energy",
        "gravity",
        "electricity",
        "magnetism",
        "thermodynamics",
        "quantum",
        "optics",
      ],
      Chemistry: [
        "chemistry",
        "element",
        "molecule",
        "atom",
        "reaction",
        "compound",
        "acid",
        "base",
        "periodic",
        "bond",
      ],
      Biology: [
        "biology",
        "cell",
        "organism",
        "ecosystem",
        "photosynthesis",
        "dna",
        "gene",
        "evolution",
        "anatomy",
        "physiology",
      ],
      "Computer Science": [
        "programming",
        "code",
        "algorithm",
        "software",
        "computer",
        "data",
        "ai",
        "machine learning",
        "database",
        "network",
        "cybersecurity",
      ],
      History: [
        "history",
        "war",
        "civilization",
        "empire",
        "revolution",
        "ancient",
        "medieval",
        "world war",
        "renaissance",
        "industrial",
      ],
      English: [
        "grammar",
        "literature",
        "writing",
        "essay",
        "poem",
        "novel",
        "shakespeare",
        "vocabulary",
        "composition",
      ],
      Geography: [
        "geography",
        "climate",
        "continent",
        "country",
        "map",
        "landscape",
        "weather",
        "population",
        "ecosystem",
      ],
      Economics: [
        "economics",
        "market",
        "supply",
        "demand",
        "gdp",
        "inflation",
        "trade",
        "finance",
        "investment",
      ],
      Psychology: [
        "psychology",
        "behavior",
        "cognition",
        "memory",
        "learning",
        "personality",
        "mental",
        "therapy",
      ],
    };

    // Check for subject matches
    const matches: Array<{ subject: string; score: number }> = [];

    for (const [subject, keywords] of Object.entries(subjectKeywords)) {
      let score = 0;
      for (const keyword of keywords) {
        if (topic.includes(keyword)) {
          score += keyword.split(" ").length > 1 ? 2 : 1; // Multi-word keywords get higher score
        }
      }
      if (score > 0) {
        matches.push({ subject, score });
      }
    }

    // Sort by score and return best match
    matches.sort((a, b) => b.score - a.score);

    if (matches.length > 0) {
      // Boost preferred domain if specified
      if (preferredDomain && matches.some(m => m.subject === preferredDomain)) {
        return preferredDomain;
      }
      return matches[0].subject;
    }

    // Default to tenant's preferred domain or General
    return tenantContext.preferredDomain || "General";
  }

  /**
   * Infer grade level from topic complexity
   */
  private async inferGradeLevel(topic: string): Promise<string> {
    const advancedTerms = [
      "advanced",
      "complex",
      "calculus",
      "differential",
      "quantum",
      "molecular",
      "university",
      "graduate",
      "research",
      "theory",
    ];

    const intermediateTerms = [
      "intermediate",
      "high school",
      "algebra",
      "geometry",
      "chemistry",
      "physics",
    ];

    const elementaryTerms = [
      "basic",
      "introduction",
      "elementary",
      "primary",
      "simple",
      "fundamentals",
      "beginner",
      "grade 1",
      "grade 2",
      "grade 3",
      "grade 4",
      "grade 5",
    ];

    // Count term matches
    let advancedCount = advancedTerms.filter(term => topic.includes(term)).length;
    let intermediateCount = intermediateTerms.filter(term => topic.includes(term)).length;
    let elementaryCount = elementaryTerms.filter(term => topic.includes(term)).length;

    // Determine level
    if (advancedCount > 0) {
      return "College";
    } else if (elementaryCount > 0) {
      return "Grade 5";
    } else if (intermediateCount > 0) {
      return "Grade 9";
    }

    // Default based on topic length/complexity
    const wordCount = topic.split(" ").length;
    if (wordCount > 5) {
      return "Grade 9";
    }
    return "Grade 6";
  }

  /**
   * Infer difficulty level from topic
   */
  private async inferDifficultyLevel(topic: string): Promise<string> {
    const gradeLevel = await this.inferGradeLevel(topic);

    // Map grade levels to difficulty
    const difficultyMap: Record<string, string> = {
      "Grade 1": "beginner",
      "Grade 2": "beginner",
      "Grade 3": "beginner",
      "Grade 4": "elementary",
      "Grade 5": "elementary",
      "Grade 6": "elementary",
      "Grade 7": "intermediate",
      "Grade 8": "intermediate",
      "Grade 9": "intermediate",
      "Grade 10": "intermediate",
      "Grade 11": "advanced",
      "Grade 12": "advanced",
      College: "advanced",
      Professional: "expert",
    };

    return difficultyMap[gradeLevel] || "intermediate";
  }

  /**
   * Smart content type defaults based on subject, grade level, and pedagogical patterns
   */
  private readonly contentTypeDefaults: Record<string, Record<string, string[]>> = {
    // STEM subjects
    Mathematics: {
      elementary: ["practiceWorksheets", "visualDemonstrations", "quizzes", "interactiveExercises"],
      middle: ["practiceWorksheets", "realWorldUseCases", "quizzes", "stepByStepSolutions"],
      high: ["practiceWorksheets", "realWorldUseCases", "quizzes", "problemSets", "proofExercises"],
      college: ["practiceWorksheets", "realWorldUseCases", "problemSets", "proofExercises", "quizzes"],
    },
    Physics: {
      elementary: ["animations", "visualDemonstrations", "quizzes", "simpleExperiments"],
      middle: ["simulations", "animations", "realWorldUseCases", "quizzes", "labGuides"],
      high: ["simulations", "animations", "realWorldUseCases", "labGuides", "problemSets", "quizzes"],
      college: ["simulations", "labGuides", "problemSets", "realWorldUseCases", "researchPapers"],
    },
    Chemistry: {
      elementary: ["animations", "visualDemonstrations", "quizzes", "safeExperiments"],
      middle: ["simulations", "animations", "labGuides", "quizzes", "molecularVisualizations"],
      high: ["simulations", "labGuides", "animations", "problemSets", "realWorldUseCases", "quizzes"],
      college: ["simulations", "labGuides", "researchPapers", "problemSets", "realWorldUseCases"],
    },
    Biology: {
      elementary: ["animations", "visualDemonstrations", "quizzes", "observationGuides"],
      middle: ["animations", "virtualLabs", "realWorldUseCases", "quizzes", "diagrams"],
      high: ["animations", "virtualLabs", "caseStudies", "realWorldUseCases", "quizzes", "diagrams"],
      college: ["virtualLabs", "caseStudies", "researchPapers", "realWorldUseCases", "advancedDiagrams"],
    },
    "Computer Science": {
      elementary: ["interactiveExercises", "visualDemonstrations", "quizzes", "codingGames"],
      middle: ["interactiveExercises", "codingProjects", "quizzes", "stepByStepTutorials"],
      high: ["codingProjects", "interactiveExercises", "realWorldUseCases", "quizzes", "debuggingExercises"],
      college: ["codingProjects", "realWorldUseCases", "researchPapers", "advancedProjects", "quizzes"],
    },
    // Humanities
    History: {
      elementary: ["visualDemonstrations", "timelines", "quizzes", "storyBasedContent"],
      middle: ["timelines", "caseStudies", "primarySources", "quizzes", "maps"],
      high: ["caseStudies", "primarySources", "researchProjects", "documentAnalysis", "quizzes"],
      college: ["researchProjects", "primarySources", "documentAnalysis", "seminarMaterials", "essays"],
    },
    Literature: {
      elementary: ["visualDemonstrations", "readAlouds", "quizzes", "comprehensionExercises"],
      middle: ["analysisGuides", "discussionPrompts", "quizzes", "characterStudies", "vocabularyExercises"],
      high: ["analysisGuides", "discussionPrompts", "essays", "criticalAnalysis", "quizzes"],
      college: ["criticalAnalysis", "essays", "researchPapers", "seminarMaterials", "literaryTheory"],
    },
    // Languages
    Languages: {
      elementary: ["interactiveExercises", "audioContent", "quizzes", "flashcards", "games"],
      middle: ["interactiveExercises", "audioContent", "conversationPractice", "quizzes", "readingExercises"],
      high: ["conversationPractice", "readingExercises", "writingExercises", "quizzes", "culturalContent"],
      college: ["advancedConversation", "literatureAnalysis", "writingExercises", "culturalContent", "researchPapers"],
    },
    // Arts
    "Visual Arts": {
      elementary: ["visualDemonstrations", "stepByStepTutorials", "interactiveExercises", "quizzes"],
      middle: ["stepByStepTutorials", "projectGuides", "techniqueDemonstrations", "quizzes", "portfolioExercises"],
      high: ["projectGuides", "techniqueDemonstrations", "portfolioExercises", "artAnalysis", "quizzes"],
      college: ["portfolioExercises", "artAnalysis", "researchPapers", "advancedProjects", "critiqueMaterials"],
    },
    // Business/Social Sciences
    Economics: {
      middle: ["realWorldUseCases", "visualDemonstrations", "quizzes", "calculations"],
      high: ["realWorldUseCases", "caseStudies", "quizzes", "dataAnalysis", "calculations"],
      college: ["caseStudies", "dataAnalysis", "realWorldUseCases", "researchPapers", "marketSimulations"],
    },
    // Default for other subjects
    General: {
      elementary: ["practiceWorksheets", "visualDemonstrations", "quizzes", "interactiveExercises"],
      middle: ["practiceWorksheets", "realWorldUseCases", "quizzes", "interactiveExercises"],
      high: ["practiceWorksheets", "realWorldUseCases", "quizzes", "caseStudies", "projects"],
      college: ["realWorldUseCases", "caseStudies", "researchPapers", "projects", "quizzes"],
    },
  };

  /**
   * Map grade level to education level category
   */
  private mapGradeToLevel(gradeLevel: string): string {
    const elementary = ["Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5"];
    const middle = ["Grade 6", "Grade 7", "Grade 8"];
    const high = ["Grade 9", "Grade 10", "Grade 11", "Grade 12"];

    if (elementary.includes(gradeLevel)) return "elementary";
    if (middle.includes(gradeLevel)) return "middle";
    if (high.includes(gradeLevel)) return "high";
    if (gradeLevel === "College" || gradeLevel === "Professional") return "college";
    return "middle"; // default
  }

  /**
   * Infer appropriate content types with smart defaults based on subject and grade
   */
  private async inferContentTypes(
    topic: string,
    tenantContext: TenantContext,
  ): Promise<string[]> {
    const subject = await this.inferSubject(topic.toLowerCase(), tenantContext);
    const gradeLevel = await this.inferGradeLevel(topic.toLowerCase());
    const level = this.mapGradeToLevel(gradeLevel);

    // Get smart defaults for subject and level
    const subjectDefaults = this.contentTypeDefaults[subject] || this.contentTypeDefaults.General;
    let types = subjectDefaults[level] || subjectDefaults.middle || this.contentTypeDefaults.General.middle;

    // Topic-specific enhancements
    const enhancedTypes = [...types];

    // Add simulations for science subjects with specific topics
    const simulationTopics = ["motion", "forces", "electricity", "chemical reaction", "cell", "ecosystem"];
    if (simulationTopics.some(t => topic.includes(t)) && !enhancedTypes.includes("simulations")) {
      enhancedTypes.push("simulations");
    }

    // Add animations for visual/conceptual topics
    const animationTopics = ["cell", "molecule", "anatomy", "earth", "planet", "process", "cycle"];
    if (animationTopics.some(t => topic.includes(t)) && !enhancedTypes.includes("animations")) {
      enhancedTypes.push("animations");
    }

    // Add projects for applied topics
    const projectTopics = ["programming", "engineering", "design", "business", "entrepreneurship"];
    if (projectTopics.some(t => topic.includes(t)) && !enhancedTypes.includes("projects")) {
      enhancedTypes.push("projects");
    }

    // Add case studies for real-world application topics
    const caseStudyTopics = ["business", "economics", "law", "ethics", "policy", "history"];
    if (caseStudyTopics.some(t => topic.includes(t)) && !enhancedTypes.includes("caseStudies")) {
      enhancedTypes.push("caseStudies");
    }

    // Remove duplicates and limit to most relevant 6 types
    const uniqueTypes = [...new Set(enhancedTypes)].slice(0, 6);

    return uniqueTypes;
  }

  /**
   * Generate learning objectives based on inferred parameters
   */
  private generateLearningObjectives(
    topic: string,
    subject: string,
    gradeLevel: string,
  ): string[] {
    const objectives: string[] = [];

    // Bloom's taxonomy based objectives
    objectives.push(`Understand the fundamental concepts of ${topic}`);

    // Add application objective for intermediate+ levels
    const advancedGrades = ["Grade 9", "Grade 10", "Grade 11", "Grade 12", "College", "Professional"];
    if (advancedGrades.includes(gradeLevel)) {
      objectives.push(`Apply ${topic} principles to solve real-world problems`);
      objectives.push(`Analyze complex scenarios involving ${topic}`);
    } else {
      objectives.push(`Apply ${topic} concepts in practical exercises`);
    }

    // Add evaluation objective for high levels
    if (["College", "Professional"].includes(gradeLevel)) {
      objectives.push(`Evaluate different approaches to ${topic} problems`);
    }

    // Subject-specific objectives
    const subjectObjectives: Record<string, string[]> = {
      Mathematics: [`Solve problems using ${topic} techniques`],
      Physics: [`Demonstrate understanding of ${topic} through experiments`],
      Chemistry: [`Identify and explain ${topic} in chemical reactions`],
      Biology: [`Describe the role of ${topic} in living organisms`],
      "Computer Science": [`Implement ${topic} solutions using code`],
    };

    if (subjectObjectives[subject]) {
      objectives.push(...subjectObjectives[subject]);
    }

    return objectives.slice(0, 4); // Max 4 objectives
  }

  /**
   * Calculate overall confidence score
   */
  private calculateConfidence(
    subject: string,
    gradeLevel: string,
    contentTypes: string[],
    tenantContext: TenantContext,
  ): number {
    let confidence = 0.5; // Base confidence

    // Boost if subject was clearly identified
    if (subject !== "General") {
      confidence += 0.2;
    }

    // Boost if content types are diverse
    if (contentTypes.length >= 3) {
      confidence += 0.1;
    }

    // Boost if tenant has established patterns
    if (tenantContext.hasEstablishedPatterns) {
      confidence += 0.1;
    }

    // Cap at 0.95
    return Math.min(confidence, 0.95);
  }

  /**
   * Build reasoning explanation for the inference
   */
  private buildReasoning(
    subject: string,
    gradeLevel: string,
    contentTypes: string[],
    tenantContext: TenantContext,
  ): string[] {
    const reasoning: string[] = [];

    reasoning.push(`Inferred subject: ${subject}`);
    reasoning.push(`Target audience: ${gradeLevel}`);

    if (contentTypes.includes("simulations")) {
      reasoning.push("Added simulations for hands-on science learning");
    }

    if (contentTypes.includes("projects")) {
      reasoning.push("Added project-based learning for applied skills");
    }

    if (tenantContext.preferredDomain) {
      reasoning.push(`Aligned with tenant's focus on ${tenantContext.preferredDomain}`);
    }

    return reasoning;
  }

  /**
   * Get tenant context for better inference
   */
  private async getTenantContext(tenantId: string): Promise<TenantContext> {
    try {
      // Get tenant's most common domains
      const recentContent = await this.prisma.contentAsset.findMany({
        where: { tenantId },
        orderBy: { createdAt: "desc" },
        take: 20,
        select: { domain: true, subject: true },
      });

      // Calculate domain frequency
      const domainCounts = new Map<string, number>();
      for (const content of recentContent) {
        if (content.domain) {
          domainCounts.set(content.domain, (domainCounts.get(content.domain) || 0) + 1);
        }
      }

      // Find most common domain
      let preferredDomain: string | undefined;
      let maxCount = 0;
      for (const [domain, count] of domainCounts) {
        if (count > maxCount) {
          maxCount = count;
          preferredDomain = domain;
        }
      }

      return {
        preferredDomain,
        hasEstablishedPatterns: recentContent.length >= 5,
        contentCount: recentContent.length,
      };
    } catch {
      // Return empty context on error
      return {
        preferredDomain: undefined,
        hasEstablishedPatterns: false,
        contentCount: 0,
      };
    }
  }
}

interface TenantContext {
  preferredDomain: string | undefined;
  hasEstablishedPatterns: boolean;
  contentCount: number;
}
