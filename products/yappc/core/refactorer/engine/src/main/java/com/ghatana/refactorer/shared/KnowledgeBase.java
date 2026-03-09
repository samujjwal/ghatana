package com.ghatana.refactorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Knowledge base for mapping diagnostic rules to suggested fix recipes per language.
 *
 * <p>This component provides:
 *
 * <ul>
 *   <li>Rule-to-fix mapping for different languages
 *   <li>Confidence scoring for fix suggestions
 *   <li>Tenant-specific customizations
 *   <li>JSON-based configuration loading
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * KnowledgeBase kb = new KnowledgeBase();
 * kb.loadFromFile(Paths.get("config/knowledge-base.json"));
 *
 * // Get fix suggestions for a Python rule
 * List<FixRecipe> recipes = kb.getFixRecipes("python", "F401");
 *
 * // Add a custom recipe
 * FixRecipe recipe = new FixRecipe("remove-unused-import", 0.9,
 *     Map.of("action", "remove", "target", "import"));
 * kb.addFixRecipe("python", "F401", recipe);
 * }</pre>
 
 * @doc.type class
 * @doc.purpose Handles knowledge base operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    private final Map<String, Map<String, List<FixRecipe>>> knowledgeBase =
            new ConcurrentHashMap<>();
    private final Map<String, TenantConfig> tenantConfigs = new ConcurrentHashMap<>();

    /**
 * Creates an empty knowledge base. */
    public KnowledgeBase() {
        initializeDefaults();
    }

    /**
     * Loads knowledge base from a JSON file.
     *
     * @param configPath Path to the JSON configuration file
     * @throws IOException if the file cannot be read or parsed
     */
    public void loadFromFile(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "configPath cannot be null");

        if (!Files.exists(configPath)) {
            log.warn("Warning: Knowledge base config file not found: {}", configPath);
            return;
        }

        String content = Files.readString(configPath);
        KnowledgeBaseConfig config = objectMapper.readValue(content, KnowledgeBaseConfig.class);

        // Load language rules
        config.languages()
                .forEach(
                        (language, rules) -> {
                            Map<String, List<FixRecipe>> languageRules =
                                    knowledgeBase.computeIfAbsent(
                                            language, k -> new ConcurrentHashMap<>());

                            rules.forEach(
                                    (ruleId, recipes) -> {
                                        languageRules.put(ruleId, new ArrayList<>(recipes));
                                    });
                        });

        // Load tenant configs
        if (config.tenants() != null) {
            tenantConfigs.putAll(config.tenants());
        }

        log.info("Info: Loaded knowledge base from {}: {} languages, {} tenant configs", configPath, knowledgeBase.size(), tenantConfigs.size());
    }

    /**
     * Gets fix recipes for a specific language and rule.
     *
     * @param language The programming language (e.g., "python", "bash", "rust")
     * @param ruleId The diagnostic rule ID (e.g., "F401", "SC2086")
     * @return List of fix recipes, or empty list if none found
     */
    public List<FixRecipe> getFixRecipes(String language, String ruleId) {
        Objects.requireNonNull(language, "language cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");

        Map<String, List<FixRecipe>> languageRules = knowledgeBase.get(language.toLowerCase());
        if (languageRules == null) {
            return List.of();
        }

        List<FixRecipe> recipes = languageRules.get(ruleId);
        return recipes != null ? new ArrayList<>(recipes) : List.of();
    }

    /**
     * Gets fix recipes for a specific language, rule, and tenant.
     *
     * @param language The programming language
     * @param ruleId The diagnostic rule ID
     * @param tenantId The tenant identifier
     * @return List of fix recipes with tenant-specific customizations applied
     */
    public List<FixRecipe> getFixRecipes(String language, String ruleId, String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        List<FixRecipe> baseRecipes = getFixRecipes(language, ruleId);
        TenantConfig tenantConfig = tenantConfigs.get(tenantId);

        if (tenantConfig == null) {
            return baseRecipes;
        }

        // Apply tenant-specific overrides
        return baseRecipes.stream()
                .map(recipe -> applyTenantOverrides(recipe, tenantConfig, language, ruleId))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Adds a fix recipe for a specific language and rule.
     *
     * @param language The programming language
     * @param ruleId The diagnostic rule ID
     * @param recipe The fix recipe to add
     */
    public void addFixRecipe(String language, String ruleId, FixRecipe recipe) {
        Objects.requireNonNull(language, "language cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Objects.requireNonNull(recipe, "recipe cannot be null");

        knowledgeBase
                .computeIfAbsent(language.toLowerCase(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(ruleId, k -> new ArrayList<>())
                .add(recipe);

        // Debug: Added fix recipe for language:ruleId: recipe.name()
    }

    /**
     * Removes all fix recipes for a specific language and rule.
     *
     * @param language The programming language
     * @param ruleId The diagnostic rule ID
     * @return true if recipes were removed, false if none existed
     */
    public boolean removeFixRecipes(String language, String ruleId) {
        Objects.requireNonNull(language, "language cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");

        Map<String, List<FixRecipe>> languageRules = knowledgeBase.get(language.toLowerCase());
        if (languageRules == null) {
            return false;
        }

        List<FixRecipe> removed = languageRules.remove(ruleId);
        return removed != null && !removed.isEmpty();
    }

    /**
     * Gets all supported languages.
     *
     * @return Set of language names
     */
    public Set<String> getSupportedLanguages() {
        return new HashSet<>(knowledgeBase.keySet());
    }

    /**
     * Gets all rules for a specific language.
     *
     * @param language The programming language
     * @return Set of rule IDs, or empty set if language not found
     */
    public Set<String> getRulesForLanguage(String language) {
        Objects.requireNonNull(language, "language cannot be null");

        Map<String, List<FixRecipe>> languageRules = knowledgeBase.get(language.toLowerCase());
        return languageRules != null ? new HashSet<>(languageRules.keySet()) : Set.of();
    }

    /**
     * Exports the knowledge base to JSON format.
     *
     * @return JSON string representation
     * @throws IOException if serialization fails
     */
    public String exportToJson() throws IOException {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig(knowledgeBase, tenantConfigs);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
    }

    /**
     * Saves the knowledge base to a JSON file.
     *
     * @param configPath Path where to save the configuration
     * @throws IOException if the file cannot be written
     */
    public void saveToFile(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "configPath cannot be null");

        String json = exportToJson();
        Files.writeString(configPath, json);

        log.info("Info: Saved knowledge base to {}", configPath);
    }

    private void initializeDefaults() {
        // Python defaults
        addFixRecipe(
                "python",
                "F401",
                new FixRecipe(
                        "remove-unused-import",
                        0.95,
                        Map.of("action", "remove", "type", "import")));
        addFixRecipe(
                "python",
                "F821",
                new FixRecipe(
                        "add-missing-import",
                        0.85,
                        Map.of("action", "add", "type", "import", "strategy", "auto-detect")));
        addFixRecipe(
                "python",
                "E302",
                new FixRecipe("add-blank-lines", 0.9, Map.of("action", "format", "lines", 2)));

        // Bash defaults
        addFixRecipe(
                "bash",
                "SC2086",
                new FixRecipe(
                        "quote-variable", 0.9, Map.of("action", "quote", "type", "variable")));
        addFixRecipe(
                "bash",
                "SC2006",
                new FixRecipe(
                        "modernize-command-substitution",
                        0.95,
                        Map.of("action", "replace", "from", "backticks", "to", "dollar-paren")));
        addFixRecipe(
                "bash",
                "SC2162",
                new FixRecipe("add-read-flag", 0.9, Map.of("action", "add-flag", "flag", "-r")));

        // Rust defaults
        addFixRecipe(
                "rust",
                "unused_imports",
                new FixRecipe(
                        "remove-unused-import",
                        0.95,
                        Map.of("action", "remove", "type", "import")));
        addFixRecipe(
                "rust",
                "dead_code",
                new FixRecipe(
                        "remove-dead-code",
                        0.8,
                        Map.of("action", "remove", "type", "code", "confirm", true)));

        // Debug: Initialized default knowledge base with knowledgeBase.size() languages
    }

    private FixRecipe applyTenantOverrides(
            FixRecipe recipe, TenantConfig tenantConfig, String language, String ruleId) {
        String key = language + ":" + ruleId + ":" + recipe.name();

        // Check if this recipe is disabled for the tenant
        if (tenantConfig.disabledRecipes().contains(key)) {
            return null;
        }

        // Apply confidence overrides
        Double confidenceOverride = tenantConfig.confidenceOverrides().get(key);
        if (confidenceOverride != null) {
            Map<String, Object> newParameters = new HashMap<>(recipe.parameters());
            return new FixRecipe(recipe.name(), confidenceOverride, newParameters);
        }

        return recipe;
    }

    /**
 * Represents a fix recipe for a diagnostic rule. */
    public record FixRecipe(String name, double confidence, Map<String, Object> parameters) {
        @JsonCreator
        public FixRecipe(
                @JsonProperty("name") String name,
                @JsonProperty("confidence") double confidence,
                @JsonProperty("parameters") Map<String, Object> parameters) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.parameters = Objects.requireNonNull(parameters, "parameters cannot be null");

            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException(
                        "Confidence must be between 0.0 and 1.0, got: " + confidence);
            }
            this.confidence = confidence;
        }
    }

    /**
 * Configuration for a specific tenant. */
    public record TenantConfig(
            Set<String> disabledRecipes, Map<String, Double> confidenceOverrides) {
        @JsonCreator
        public TenantConfig(
                @JsonProperty("disabledRecipes") Set<String> disabledRecipes,
                @JsonProperty("confidenceOverrides") Map<String, Double> confidenceOverrides) {
            this.disabledRecipes = disabledRecipes != null ? Set.copyOf(disabledRecipes) : Set.of();
            this.confidenceOverrides =
                    confidenceOverrides != null ? Map.copyOf(confidenceOverrides) : Map.of();
        }
    }

    /**
 * Complete knowledge base configuration. */
    private record KnowledgeBaseConfig(
            Map<String, Map<String, List<FixRecipe>>> languages,
            Map<String, TenantConfig> tenants) {
        @JsonCreator
        public KnowledgeBaseConfig(
                @JsonProperty("languages") Map<String, Map<String, List<FixRecipe>>> languages,
                @JsonProperty("tenants") Map<String, TenantConfig> tenants) {
            this.languages = languages != null ? languages : Map.of();
            this.tenants = tenants != null ? tenants : Map.of();
        }
    }
}
