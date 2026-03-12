package com.ghatana.tutorputor.agents.prompts;

import java.util.Map;

/**
 * Prompt template engine for content generation.
 *
 * <p>Builds structured, operation-specific prompts from a typed request object
 * and a context map. Each operation gets a purpose-built prompt that
 * instructs the LLM to return well-structured output for downstream parsing
 * and validation.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>{@code generateClaims} — Bloom's-taxonomy-aligned learning objectives</li>
 *   <li>{@code generateExamples} — real-world, problem-solving, analogy, case-study examples</li>
 *   <li>{@code analyzeContentNeeds} — decision about what content types are needed</li>
 *   <li>{@code generateSimulation} — interactive physics/concept simulation manifest</li>
 *   <li>{@code generateAnimation} — keyframe-based animation specification</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Build operation-specific LLM prompts from request objects and context
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class PromptTemplateEngine {

    /** Shared pedagogical context injected into every prompt. */
    private static final String PEDAGOGY_PREAMBLE =
            "You are an expert educational content designer specialising in K-12 through university STEM.\n" +
            "Always respond in valid JSON. Do not include markdown fences or extra commentary.\n\n";

    public PromptTemplateEngine() {}

    /**
     * Builds an operation-specific prompt from the given request and context map.
     *
     * <p>The {@code operation} key in {@code context} drives template selection.
     * Unrecognised operations fall back to a generic parameter-listing prompt.
     *
     * @param <T>     the request type
     * @param request the agent request (used for type inference; fields come from {@code context})
     * @param context execution context with operation parameters and domain-specific fields
     * @return the fully rendered prompt string ready for LLM consumption
     */
    public <T> String buildPrompt(T request, Map<String, Object> context) {
        String operation = String.valueOf(context.getOrDefault("operation", "unknown"));

        return switch (operation) {
            case "generateClaims"      -> buildGenerateClaimsPrompt(context);
            case "generateExamples"    -> buildGenerateExamplesPrompt(context);
            case "analyzeContentNeeds" -> buildAnalyzeContentNeedsPrompt(context);
            case "generateSimulation"  -> buildGenerateSimulationPrompt(context);
            case "generateAnimation"   -> buildGenerateAnimationPrompt(context);
            default                    -> buildGenericPrompt(operation, context);
        };
    }

    // ── Per-operation templates ──────────────────────────────────────────────

    private String buildGenerateClaimsPrompt(Map<String, Object> ctx) {
        return PEDAGOGY_PREAMBLE +
            "## Task: Generate Learning Claims\n\n" +
            "Topic       : " + ctx.getOrDefault("topic", "unspecified") + "\n" +
            "Grade Level : " + ctx.getOrDefault("gradeLevel", "UNDERGRADUATE") + "\n" +
            "Domain      : " + ctx.getOrDefault("domain", "SCIENCE") + "\n" +
            "Max Claims  : " + ctx.getOrDefault("maxClaims", 5) + "\n\n" +
            "Requirements:\n" +
            "- Each claim must start with an observable Bloom's-taxonomy verb (remember/understand/apply/analyze/evaluate/create).\n" +
            "- Cover a RANGE of Bloom levels; avoid clustering all claims at one level.\n" +
            "- Claims must be measurable and specific, not vague ('understand the topic').\n\n" +
            "Response JSON schema:\n" +
            "{\n" +
            "  \"claims\": [\n" +
            "    {\n" +
            "      \"claim_ref\": \"C1\",\n" +
            "      \"text\": \"The learner can ...\",\n" +
            "      \"bloom_level\": \"understand\",\n" +
            "      \"content_needs\": {\n" +
            "        \"examples\": { \"required\": true, \"types\": [\"real_world\"], \"count\": 2, \"necessity\": 0.8,\n" +
            "                        \"rationale\": \"...\"},\n" +
            "        \"simulation\": { \"required\": false, \"interaction_type\": \"PARAMETER_EXPLORATION\",\n" +
            "                          \"complexity\": \"MEDIUM\", \"necessity\": 0.4, \"rationale\": \"...\"},\n" +
            "        \"animation\": { \"required\": false, \"animation_type\": \"TWO_D\",\n" +
            "                         \"duration_seconds\": 30, \"necessity\": 0.3, \"rationale\": \"...\"}\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String buildGenerateExamplesPrompt(Map<String, Object> ctx) {
        return PEDAGOGY_PREAMBLE +
            "## Task: Generate Learning Examples\n\n" +
            "Claim       : " + ctx.getOrDefault("claimText", "unspecified") + "\n" +
            "Grade Level : " + ctx.getOrDefault("gradeLevel", "UNDERGRADUATE") + "\n" +
            "Domain      : " + ctx.getOrDefault("domain", "SCIENCE") + "\n" +
            "Types       : " + ctx.getOrDefault("exampleTypes", "[\"REAL_WORLD\",\"PROBLEM_SOLVING\"]") + "\n" +
            "Count       : " + ctx.getOrDefault("count", 3) + "\n\n" +
            "Requirements:\n" +
            "- REAL_WORLD examples must reference a named, concrete, verifiable scenario.\n" +
            "- PROBLEM_SOLVING examples must include a clear problem statement and worked solution.\n" +
            "- ANALOGY examples must map the unfamiliar concept to a well-known everyday object.\n" +
            "- CASE_STUDY examples must include context, challenge, and outcome.\n\n" +
            "Response JSON schema:\n" +
            "{\n" +
            "  \"examples\": [\n" +
            "    {\n" +
            "      \"example_id\": \"E1\",\n" +
            "      \"type\": \"REAL_WORLD\",\n" +
            "      \"title\": \"...\",\n" +
            "      \"description\": \"...\",\n" +
            "      \"problem_statement\": \"...\",\n" +
            "      \"solution_content\": \"...\",\n" +
            "      \"key_learning_points\": [\"...\"],\n" +
            "      \"real_world_connection\": \"...\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String buildAnalyzeContentNeedsPrompt(Map<String, Object> ctx) {
        return PEDAGOGY_PREAMBLE +
            "## Task: Analyze Content Needs for a Learning Claim\n\n" +
            "Claim Text  : " + ctx.getOrDefault("claimText", "unspecified") + "\n" +
            "Bloom Level : " + ctx.getOrDefault("bloomLevel", "UNDERSTAND") + "\n" +
            "Grade Level : " + ctx.getOrDefault("gradeLevel", "UNDERGRADUATE") + "\n" +
            "Domain      : " + ctx.getOrDefault("domain", "SCIENCE") + "\n\n" +
            "Decision rules:\n" +
            "- Examples are almost always needed (necessity >= 0.7) for REMEMBER/UNDERSTAND.\n" +
            "- Simulations are valuable for APPLY/ANALYZE claims in STEM domains (necessity >= 0.6).\n" +
            "- Animations add value when the concept involves movement, processes, or sequences.\n" +
            "- necessity is a float in [0, 1]; required=true when necessity >= 0.5.\n\n" +
            "Response JSON schema:\n" +
            "{\n" +
            "  \"content_needs\": {\n" +
            "    \"examples\": { \"required\": true, \"types\": [\"REAL_WORLD\"], \"count\": 2, \"necessity\": 0.85, \"rationale\": \"...\"},\n" +
            "    \"simulation\": { \"required\": false, \"interaction_type\": \"PARAMETER_EXPLORATION\",\n" +
            "                      \"complexity\": \"LOW\", \"necessity\": 0.3, \"rationale\": \"...\"},\n" +
            "    \"animation\": { \"required\": false, \"animation_type\": \"TWO_D\",\n" +
            "                     \"duration_seconds\": 20, \"necessity\": 0.2, \"rationale\": \"...\"}\n" +
            "  },\n" +
            "  \"rationale\": \"Overall reasoning for the content mix.\"\n" +
            "}";
    }

    private String buildGenerateSimulationPrompt(Map<String, Object> ctx) {
        return PEDAGOGY_PREAMBLE +
            "## Task: Generate Interactive Simulation Manifest\n\n" +
            "Claim Text       : " + ctx.getOrDefault("claimText", "unspecified") + "\n" +
            "Grade Level      : " + ctx.getOrDefault("gradeLevel", "UNDERGRADUATE") + "\n" +
            "Domain           : " + ctx.getOrDefault("domain", "SCIENCE") + "\n" +
            "Interaction Type : " + ctx.getOrDefault("interactionType", "PARAMETER_EXPLORATION") + "\n" +
            "Complexity       : " + ctx.getOrDefault("complexity", "MEDIUM") + "\n\n" +
            "Requirements:\n" +
            "- Include at least 1 controllable parameter with realistic min/max/default values.\n" +
            "- Include at least 2 measurable success goals.\n" +
            "- Entities should represent the actual physical or conceptual objects of the claim.\n\n" +
            "Response JSON schema:\n" +
            "{\n" +
            "  \"manifest\": {\n" +
            "    \"manifest_id\": \"SIM-...\",\n" +
            "    \"name\": \"...\",\n" +
            "    \"description\": \"...\",\n" +
            "    \"entities\": [{ \"id\": \"e1\", \"label\": \"Ball\", \"entity_type\": \"BALL\",\n" +
            "                    \"position\": {\"x\": 0, \"y\": 0, \"z\": 0}, \"visual\": \"{}\"}],\n" +
            "    \"controllable_params\": [{ \"param_id\": \"p1\", \"name\": \"Velocity\",\n" +
            "                              \"type\": \"slider\", \"min_value\": 0, \"max_value\": 100,\n" +
            "                              \"default_value\": 10, \"unit\": \"m/s\" }],\n" +
            "    \"goals\": [{ \"goal_id\": \"g1\", \"description\": \"...\", \"success_criteria\": {} }]\n" +
            "  }\n" +
            "}";
    }

    private String buildGenerateAnimationPrompt(Map<String, Object> ctx) {
        return PEDAGOGY_PREAMBLE +
            "## Task: Generate Animation Specification\n\n" +
            "Claim Text       : " + ctx.getOrDefault("claimText", "unspecified") + "\n" +
            "Claim Ref        : " + ctx.getOrDefault("claimRef", "C1") + "\n" +
            "Animation Type   : " + ctx.getOrDefault("animationType", "TWO_D") + "\n" +
            "Duration (s)     : " + ctx.getOrDefault("durationSeconds", 30) + "\n\n" +
            "Requirements:\n" +
            "- Produce a keyframe-based animation spec that visually illustrates the claim.\n" +
            "- Minimum 3 keyframes; distribute them evenly across the duration.\n" +
            "- Each keyframe must describe a concrete visible state change relevant to the concept.\n" +
            "- Config should specify canvas size, frame rate, and background colour.\n\n" +
            "Response JSON schema:\n" +
            "{\n" +
            "  \"animation\": {\n" +
            "    \"animation_id\": \"ANIM-...\",\n" +
            "    \"title\": \"...\",\n" +
            "    \"description\": \"...\",\n" +
            "    \"type\": \"TWO_D\",\n" +
            "    \"duration_seconds\": 30,\n" +
            "    \"keyframes\": [\n" +
            "      { \"time_ms\": 0,     \"description\": \"Initial state\",  \"properties\": {\"x\": \"0\", \"opacity\": \"1\"} },\n" +
            "      { \"time_ms\": 15000, \"description\": \"Mid state\",       \"properties\": {\"x\": \"50%\", \"opacity\": \"0.8\"} },\n" +
            "      { \"time_ms\": 30000, \"description\": \"Final state\",     \"properties\": {\"x\": \"100%\", \"opacity\": \"0.6\"} }\n" +
            "    ],\n" +
            "    \"config\": { \"width\": \"800\", \"height\": \"450\", \"fps\": \"30\", \"background\": \"#ffffff\" }\n" +
            "  }\n" +
            "}";
    }

    private String buildGenericPrompt(String operation, Map<String, Object> ctx) {
        StringBuilder sb = new StringBuilder(PEDAGOGY_PREAMBLE);
        sb.append("## Operation: ").append(operation).append("\n\n");
        for (Map.Entry<String, Object> entry : ctx.entrySet()) {
            if (!"operation".equals(entry.getKey())) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("\nRespond with a valid JSON object relevant to the operation above.");
        return sb.toString();
    }
}
