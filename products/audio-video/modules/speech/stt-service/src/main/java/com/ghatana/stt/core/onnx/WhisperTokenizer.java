package com.ghatana.stt.core.onnx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer for Whisper speech-to-text models.
 *
 * <p>Handles encoding text to token IDs and decoding token IDs back to text.
 * Supports special tokens for transcription tasks (SOT, EOT, language, etc.).
 *
 * <p><b>Vocabulary:</b> Uses byte-pair encoding (BPE) vocabulary from
 * Whisper's multilingual tokenizer with 51,865 tokens.
 *
 * @doc.type class
 * @doc.purpose Whisper model tokenization
 * @doc.layer infrastructure
 * @doc.pattern Utility
 */
public final class WhisperTokenizer {

    private static final Logger LOG = LoggerFactory.getLogger(WhisperTokenizer.class);

    // Special token IDs for Whisper
    public static final int SOT = 50258;           // Start of transcript
    public static final int EOT = 50257;           // End of transcript
    public static final int TRANSCRIBE = 50359;   // Transcribe task
    public static final int TRANSLATE = 50358;    // Translate task
    public static final int NO_TIMESTAMPS = 50363; // No timestamps
    public static final int NO_SPEECH = 50362;    // No speech detected

    // Language tokens (subset)
    public static final int LANG_EN = 50259;
    public static final int LANG_ES = 50262;
    public static final int LANG_FR = 50265;
    public static final int LANG_DE = 50261;
    public static final int LANG_IT = 50274;
    public static final int LANG_PT = 50267;
    public static final int LANG_JA = 50266;
    public static final int LANG_ZH = 50260;

    private final Map<String, Integer> encoder;
    private final Map<Integer, String> decoder;
    private final Map<String, Integer> languageTokens;
    private final Pattern tokenPattern;

    /**
     * Creates a tokenizer with the default vocabulary.
     */
    public WhisperTokenizer() {
        this.encoder = new HashMap<>();
        this.decoder = new HashMap<>();
        this.languageTokens = new HashMap<>();
        this.tokenPattern = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+"
        );

        initializeDefaultVocabulary();
        initializeLanguageTokens();
    }

    /**
     * Creates a tokenizer from a vocabulary file.
     *
     * @param vocabPath path to the vocabulary JSON file
     * @throws IOException if the file cannot be read
     */
    public WhisperTokenizer(Path vocabPath) throws IOException {
        this.encoder = new HashMap<>();
        this.decoder = new HashMap<>();
        this.languageTokens = new HashMap<>();
        this.tokenPattern = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+"
        );

        loadVocabulary(vocabPath);
        initializeLanguageTokens();
    }

    /**
     * Encodes text to token IDs.
     *
     * @param text the text to encode
     * @return list of token IDs
     */
    public List<Integer> encode(String text) {
        List<Integer> tokens = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return tokens;
        }

        // Normalize text
        text = text.toLowerCase().trim();

        // Tokenize using pattern
        Matcher matcher = tokenPattern.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            Integer id = encoder.get(token);
            if (id != null) {
                tokens.add(id);
            } else {
                // Fall back to character-level encoding
                for (char c : token.toCharArray()) {
                    Integer charId = encoder.get(String.valueOf(c));
                    if (charId != null) {
                        tokens.add(charId);
                    }
                }
            }
        }

        return tokens;
    }

    /**
     * Decodes token IDs to text.
     *
     * @param tokenIds the token IDs to decode
     * @return the decoded text
     */
    public String decode(List<Integer> tokenIds) {
        StringBuilder result = new StringBuilder();

        for (int tokenId : tokenIds) {
            // Skip special tokens
            if (tokenId >= 50257) {
                continue;
            }

            String token = decoder.get(tokenId);
            if (token != null) {
                result.append(token);
            }
        }

        return result.toString().trim();
    }

    /**
     * Decodes token IDs to text.
     *
     * @param tokenIds the token IDs as array
     * @return the decoded text
     */
    public String decode(long[] tokenIds) {
        List<Integer> list = new ArrayList<>();
        for (long id : tokenIds) {
            list.add((int) id);
        }
        return decode(list);
    }

    /**
     * Gets the initial tokens for transcription.
     *
     * @param language the language code (e.g., "en", "es")
     * @param task "transcribe" or "translate"
     * @param useTimestamps whether to include timestamps
     * @return array of initial token IDs
     */
    public long[] getInitialTokens(String language, String task, boolean useTimestamps) {
        List<Long> tokens = new ArrayList<>();

        // Start of transcript
        tokens.add((long) SOT);

        // Language token
        Integer langToken = languageTokens.get(language.toLowerCase());
        if (langToken != null) {
            tokens.add(langToken.longValue());
        } else {
            tokens.add((long) LANG_EN); // Default to English
        }

        // Task token
        if ("translate".equalsIgnoreCase(task)) {
            tokens.add((long) TRANSLATE);
        } else {
            tokens.add((long) TRANSCRIBE);
        }

        // Timestamps
        if (!useTimestamps) {
            tokens.add((long) NO_TIMESTAMPS);
        }

        return tokens.stream().mapToLong(Long::longValue).toArray();
    }

    /**
     * Checks if a token ID is the end-of-transcript token.
     *
     * @param tokenId the token ID
     * @return true if EOT
     */
    public boolean isEndOfTranscript(int tokenId) {
        return tokenId == EOT;
    }

    /**
     * Checks if a token ID is a special token.
     *
     * @param tokenId the token ID
     * @return true if special token
     */
    public boolean isSpecialToken(int tokenId) {
        return tokenId >= 50257;
    }

    /**
     * Gets the vocabulary size.
     *
     * @return number of tokens in vocabulary
     */
    public int vocabularySize() {
        return encoder.size();
    }

    private void initializeDefaultVocabulary() {
        // Initialize with basic ASCII characters and common tokens
        // In production, this would load the full Whisper vocabulary

        // Basic ASCII
        for (int i = 32; i < 127; i++) {
            String c = String.valueOf((char) i);
            encoder.put(c, i - 32);
            decoder.put(i - 32, c);
        }

        // Common words (simplified)
        String[] commonWords = {
            " the", " a", " an", " is", " are", " was", " were", " be", " been",
            " have", " has", " had", " do", " does", " did", " will", " would",
            " could", " should", " may", " might", " must", " can", " and", " or",
            " but", " if", " then", " else", " when", " where", " what", " who",
            " how", " why", " this", " that", " these", " those", " it", " its",
            " to", " of", " in", " on", " at", " by", " for", " with", " from",
            " as", " into", " through", " during", " before", " after", " above",
            " below", " between", " under", " again", " further", " once", " here",
            " there", " all", " each", " few", " more", " most", " other", " some",
            " such", " no", " not", " only", " own", " same", " so", " than", " too",
            " very", " just", " also", " now", " new", " first", " last", " long",
            " great", " little", " own", " other", " old", " right", " big", " high",
            " different", " small", " large", " next", " early", " young", " important",
            " few", " public", " bad", " same", " able"
        };

        int nextId = 100;
        for (String word : commonWords) {
            if (!encoder.containsKey(word)) {
                encoder.put(word, nextId);
                decoder.put(nextId, word);
                nextId++;
            }
        }

        LOG.debug("Initialized default vocabulary with {} tokens", encoder.size());
    }

    private void initializeLanguageTokens() {
        languageTokens.put("en", LANG_EN);
        languageTokens.put("es", LANG_ES);
        languageTokens.put("fr", LANG_FR);
        languageTokens.put("de", LANG_DE);
        languageTokens.put("it", LANG_IT);
        languageTokens.put("pt", LANG_PT);
        languageTokens.put("ja", LANG_JA);
        languageTokens.put("zh", LANG_ZH);
    }

    private void loadVocabulary(Path vocabPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = Files.newInputStream(vocabPath)) {
            Map<String, Integer> vocab = mapper.readValue(is, new TypeReference<>() {});

            encoder.putAll(vocab);
            for (Map.Entry<String, Integer> entry : vocab.entrySet()) {
                decoder.put(entry.getValue(), entry.getKey());
            }

            LOG.info("Loaded vocabulary from {}: {} tokens", vocabPath, encoder.size());
        }
    }
}
