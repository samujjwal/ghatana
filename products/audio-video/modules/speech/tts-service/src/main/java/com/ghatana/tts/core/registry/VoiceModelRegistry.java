package com.ghatana.tts.core.registry;

import com.ghatana.tts.core.api.VoiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent registry for voice models, including cloned voices.
 *
 * <p>Stores voice metadata in a simple JSON-lines file under the models directory.
 * Each line is a self-contained JSON object describing one registered voice.
 * The in-memory map acts as a write-through cache — reads always hit memory,
 * and every write is synchronously flushed to disk.
 *
 * <p>This design intentionally avoids an external database dependency so the
 * TTS service can run standalone without infrastructure prerequisites.
 *
 * @doc.type class
 * @doc.purpose Persistent voice model registry with write-through cache
 * @doc.layer product
 * @doc.pattern Repository
 */
public class VoiceModelRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceModelRegistry.class);

    private static final String REGISTRY_FILENAME = "voice-registry.jsonl";

    private final Path registryFile;
    private final Map<String, VoiceRegistryEntry> entries = new ConcurrentHashMap<>();

    public VoiceModelRegistry(Path modelsDirectory) {
        this.registryFile = modelsDirectory.resolve(REGISTRY_FILENAME);
        loadFromDisk();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Register a new voice (built-in or cloned) with its metadata.
     *
     * @param voice      voice descriptor
     * @param modelPath  path to the model artefact on disk (may be null for stubs)
     */
    public void register(VoiceInfo voice, Path modelPath) {
        VoiceRegistryEntry entry = new VoiceRegistryEntry(
                voice.voiceId(),
                voice.name(),
                voice.description(),
                voice.languages(),
                voice.gender(),
                voice.sizeBytes(),
                voice.isLoaded(),
                voice.isCloned(),
                modelPath != null ? modelPath.toString() : "",
                Instant.now().toEpochMilli());

        entries.put(voice.voiceId(), entry);
        appendToDisk(entry);
        LOG.info("Registered voice '{}' (id={}, cloned={})", voice.name(), voice.voiceId(), voice.isCloned());
    }

    /**
     * Look up a voice by ID.
     *
     * @param voiceId voice ID
     * @return the registry entry, or empty if not found
     */
    public Optional<VoiceRegistryEntry> find(String voiceId) {
        return Optional.ofNullable(entries.get(voiceId));
    }

    /**
     * Convert a registry entry back to the API's {@link VoiceInfo} value object.
     */
    public Optional<VoiceInfo> findVoiceInfo(String voiceId) {
        return find(voiceId).map(e -> new VoiceInfo(
                e.voiceId(),
                e.name(),
                e.description(),
                e.languages(),
                e.gender(),
                e.sizeBytes(),
                e.loaded(),
                e.cloned()));
    }

    /**
     * List all registered voices, optionally filtered to cloned-only.
     */
    public List<VoiceRegistryEntry> listAll(boolean clonedOnly) {
        List<VoiceRegistryEntry> result = new ArrayList<>();
        for (VoiceRegistryEntry e : entries.values()) {
            if (!clonedOnly || e.cloned()) {
                result.add(e);
            }
        }
        result.sort((a, b) -> Long.compare(b.registeredAtMs(), a.registeredAtMs()));
        return result;
    }

    /**
     * Mark a voice as loaded (model files are resident in memory).
     */
    public void markLoaded(String voiceId) {
        VoiceRegistryEntry existing = entries.get(voiceId);
        if (existing == null) return;

        VoiceRegistryEntry updated = new VoiceRegistryEntry(
                existing.voiceId(), existing.name(), existing.description(),
                existing.languages(), existing.gender(), existing.sizeBytes(),
                true, existing.cloned(), existing.modelPath(), existing.registeredAtMs());

        entries.put(voiceId, updated);
        rewriteToDisk();
    }

    /**
     * Remove a voice from the registry.
     *
     * @param voiceId voice to remove
     * @return true if the voice existed and was removed
     */
    public boolean deregister(String voiceId) {
        boolean removed = entries.remove(voiceId) != null;
        if (removed) {
            rewriteToDisk();
            LOG.info("Deregistered voice: {}", voiceId);
        }
        return removed;
    }

    public int size() { return entries.size(); }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    private void loadFromDisk() {
        if (!Files.exists(registryFile)) {
            LOG.debug("No voice registry file found at {}, starting empty", registryFile);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(registryFile, StandardCharsets.UTF_8);
            int loaded = 0;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    VoiceRegistryEntry entry = parseJsonLine(line);
                    entries.put(entry.voiceId(), entry);
                    loaded++;
                } catch (Exception e) {
                    LOG.warn("Skipping malformed registry entry: {}", line, e);
                }
            }
            LOG.info("Loaded {} voice(s) from registry at {}", loaded, registryFile);
        } catch (IOException e) {
            LOG.error("Failed to load voice registry from {}", registryFile, e);
        }
    }

    private void appendToDisk(VoiceRegistryEntry entry) {
        try {
            Files.createDirectories(registryFile.getParent());
            String line = toJsonLine(entry) + System.lineSeparator();
            Files.writeString(registryFile, line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.error("Failed to append voice entry to registry file", e);
        }
    }

    private void rewriteToDisk() {
        try {
            Files.createDirectories(registryFile.getParent());
            StringBuilder sb = new StringBuilder();
            for (VoiceRegistryEntry entry : entries.values()) {
                sb.append(toJsonLine(entry)).append(System.lineSeparator());
            }
            Files.writeString(registryFile, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.error("Failed to rewrite voice registry file", e);
        }
    }

    // -------------------------------------------------------------------------
    // Minimal JSON serialisation (avoids external library dependency)
    // -------------------------------------------------------------------------

    private String toJsonLine(VoiceRegistryEntry e) {
        String langs = String.join(",", e.languages());
        return String.format(
                "{\"voiceId\":\"%s\",\"name\":\"%s\",\"description\":\"%s\"," +
                "\"languages\":\"%s\",\"gender\":\"%s\",\"sizeBytes\":%d," +
                "\"loaded\":%b,\"cloned\":%b,\"modelPath\":\"%s\",\"registeredAtMs\":%d}",
                escape(e.voiceId()), escape(e.name()), escape(e.description()),
                escape(langs), escape(e.gender()), e.sizeBytes(),
                e.loaded(), e.cloned(),
                escape(e.modelPath()), e.registeredAtMs());
    }

    private VoiceRegistryEntry parseJsonLine(String json) {
        String voiceId      = extractString(json, "voiceId");
        String name         = extractString(json, "name");
        String description  = extractString(json, "description");
        String langsStr     = extractString(json, "languages");
        String gender       = extractString(json, "gender");
        long sizeBytes      = extractLong(json, "sizeBytes");
        boolean loaded      = extractBool(json, "loaded");
        boolean cloned      = extractBool(json, "cloned");
        String modelPath    = extractString(json, "modelPath");
        long registeredAtMs = extractLong(json, "registeredAtMs");

        List<String> languages = langsStr.isEmpty()
                ? new ArrayList<>()
                : List.of(langsStr.split(","));

        return new VoiceRegistryEntry(voiceId, name, description, languages,
                gender, sizeBytes, loaded, cloned, modelPath, registeredAtMs);
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long extractLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0L;
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try { return Long.parseLong(json.substring(start, end)); } catch (NumberFormatException ex) { return 0L; }
    }

    private static boolean extractBool(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return false;
        start += search.length();
        return json.startsWith("true", start);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
