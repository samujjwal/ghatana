package com.ghatana.refactorer.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating and managing FixSuggester instances. 
 * @doc.type class
 * @doc.purpose Handles fix suggester factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public class FixSuggesterFactory {

    private static final Logger log = LoggerFactory.getLogger(FixSuggesterFactory.class);
    private static final FixSuggesterFactory INSTANCE = new FixSuggesterFactory();
    private final Map<String, FixSuggester> suggesters;
    private final FixSuggester defaultSuggester;

    private FixSuggesterFactory() {
        this.suggesters = new HashMap<>();
        this.defaultSuggester = new FixSuggester();
        initializeBuiltInSuggesters();
        loadPluginSuggesters();
    }

    public static FixSuggesterFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Get a FixSuggester for the specified language. Returns the default suggester if no
     * language-specific one is found.
     */
    public FixSuggester getSuggester(String language) {
        return suggesters.getOrDefault(language.toLowerCase(), defaultSuggester);
    }

    /**
 * Register a custom FixSuggester for a specific language. */
    public void registerSuggester(String language, FixSuggester suggester) {
        suggesters.put(language.toLowerCase(), suggester);
    }

    private void initializeBuiltInSuggesters() {
        // Java suggestions
        FixSuggester javaSuggester = new FixSuggester();
        registerSuggester("java", javaSuggester);

        // Python suggestions
        FixSuggester pythonSuggester = new FixSuggester();
        registerSuggester("python", pythonSuggester);

        // JavaScript/TypeScript suggestions
        FixSuggester jsTsSuggester = new FixSuggester();
        registerSuggester("javascript", jsTsSuggester);
        registerSuggester("typescript", jsTsSuggester);
    }

    private void loadPluginSuggesters() {
        // Load any plugin-provided suggesters using Java ServiceLoader
        ServiceLoader<FixSuggesterProvider> loader = ServiceLoader.load(FixSuggesterProvider.class);

        for (FixSuggesterProvider provider : loader) {
            try {
                provider.registerSuggesters(this);
            } catch (Exception e) {
                log.error("Failed to load fix suggestions from provider: {}", provider.getClass().getName());
                e.printStackTrace();
            }
        }
    }

    /**
 * Interface for plugin-provided fix suggesters. */
    public interface FixSuggesterProvider {
        /**
 * Register custom fix suggesters with the factory. */
        void registerSuggesters(FixSuggesterFactory factory);
    }
}
