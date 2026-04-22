package com.ghatana.audio.video.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FileSystemModelStore}.
 *
 * @doc.type class
 * @doc.purpose Verifies ModelRegistry: registration, load, unload, LRU eviction, listing
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("FileSystemModelStore [GH-90000]")
class FileSystemModelStoreTest {

    @TempDir
    Path tempDir;

    private FileSystemModelStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new FileSystemModelStore(3); // small capacity for eviction tests // GH-90000
    }

    // ── register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: stores metadata and is findable by ID [GH-90000]")
    void register_storesMetadata() { // GH-90000
        ModelMetadata meta = buildMeta("model-a", ModelMetadata.ModelType.STT); // GH-90000
        store.register(meta); // GH-90000

        Optional<ModelMetadata> found = store.findById("model-a [GH-90000]");
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().modelId()).isEqualTo("model-a [GH-90000]");
        assertThat(found.get().loaded()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("register: duplicate model ID throws ModelRegistryException [GH-90000]")
    void register_duplicate_throws() { // GH-90000
        ModelMetadata meta = buildMeta("dup", ModelMetadata.ModelType.TTS); // GH-90000
        store.register(meta); // GH-90000

        assertThatThrownBy(() -> store.register(meta)) // GH-90000
                .isInstanceOf(ModelRegistry.ModelRegistryException.class) // GH-90000
                .hasMessageContaining("already registered [GH-90000]");
    }

    // ── load ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("load: model without path loads successfully [GH-90000]")
    void load_withoutPath_succeeds() { // GH-90000
        store.register(buildMeta("m1", ModelMetadata.ModelType.STT)); // GH-90000

        ModelMetadata loaded = store.load("m1 [GH-90000]");

        assertThat(loaded.loaded()).isTrue(); // GH-90000
        assertThat(store.isLoaded("m1 [GH-90000]")).isTrue();
        assertThat(store.loadedCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("load: model with existing file loads successfully and captures size [GH-90000]")
    void load_withExistingFile_capturesSize() throws IOException { // GH-90000
        Path modelFile = tempDir.resolve("whisper.onnx [GH-90000]");
        Files.write(modelFile, new byte[1024]); // GH-90000

        ModelMetadata meta = ModelMetadata.builder() // GH-90000
                .modelId("whisper [GH-90000]").type(ModelMetadata.ModelType.STT)
                .modelPath(modelFile).build(); // GH-90000
        store.register(meta); // GH-90000

        ModelMetadata loaded = store.load("whisper [GH-90000]");

        assertThat(loaded.loaded()).isTrue(); // GH-90000
        assertThat(loaded.sizeBytes()).isEqualTo(1024L); // GH-90000
    }

    @Test
    @DisplayName("load: model with missing file throws ModelRegistryException [GH-90000]")
    void load_missingFile_throws() { // GH-90000
        Path missingFile = tempDir.resolve("missing.onnx [GH-90000]");
        ModelMetadata meta = ModelMetadata.builder() // GH-90000
                .modelId("missing [GH-90000]").type(ModelMetadata.ModelType.STT)
                .modelPath(missingFile).build(); // GH-90000
        store.register(meta); // GH-90000

        assertThatThrownBy(() -> store.load("missing [GH-90000]"))
                .isInstanceOf(ModelRegistry.ModelRegistryException.class) // GH-90000
                .hasMessageContaining("not found [GH-90000]");
    }

    @Test
    @DisplayName("load: unregistered model ID throws ModelRegistryException [GH-90000]")
    void load_unregistered_throws() { // GH-90000
        assertThatThrownBy(() -> store.load("ghost [GH-90000]"))
                .isInstanceOf(ModelRegistry.ModelRegistryException.class) // GH-90000
                .hasMessageContaining("not registered [GH-90000]");
    }

    @Test
    @DisplayName("load: already-loaded model is idempotent [GH-90000]")
    void load_alreadyLoaded_idempotent() { // GH-90000
        store.register(buildMeta("m1", ModelMetadata.ModelType.STT)); // GH-90000
        store.load("m1 [GH-90000]");
        ModelMetadata second = store.load("m1 [GH-90000]");

        assertThat(second.loaded()).isTrue(); // GH-90000
        assertThat(store.loadedCount()).isEqualTo(1); // GH-90000
    }

    // ── unload ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unload: unloads loaded model and returns 0 size for path-less model [GH-90000]")
    void unload_loadedModel_succeeds() { // GH-90000
        store.register(buildMeta("m1", ModelMetadata.ModelType.STT)); // GH-90000
        store.load("m1 [GH-90000]");

        long freed = store.unload("m1 [GH-90000]");

        assertThat(store.isLoaded("m1 [GH-90000]")).isFalse();
        assertThat(store.loadedCount()).isEqualTo(0); // GH-90000
        assertThat(freed).isGreaterThanOrEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("unload: calling on unloaded model returns 0 [GH-90000]")
    void unload_notLoaded_returnsZero() { // GH-90000
        store.register(buildMeta("m1", ModelMetadata.ModelType.STT)); // GH-90000

        long freed = store.unload("m1 [GH-90000]");

        assertThat(freed).isEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("unload: unregistered model ID throws ModelRegistryException [GH-90000]")
    void unload_unregistered_throws() { // GH-90000
        assertThatThrownBy(() -> store.unload("ghost [GH-90000]"))
                .isInstanceOf(ModelRegistry.ModelRegistryException.class) // GH-90000
                .hasMessageContaining("not registered [GH-90000]");
    }

    // ── LRU eviction ────────────────────────────────────────────────────────

    @Test
    @DisplayName("LRU eviction: exceeding capacity evicts oldest model [GH-90000]")
    void lruEviction_evictsOldest() { // GH-90000
        for (String id : List.of("a", "b", "c", "d")) { // GH-90000
            store.register(buildMeta(id, ModelMetadata.ModelType.STT)); // GH-90000
        }
        store.load("a [GH-90000]");
        store.load("b [GH-90000]");
        store.load("c [GH-90000]");
        // Now at capacity=3; loading "d" should evict "a" (oldest) // GH-90000
        store.load("d [GH-90000]");

        assertThat(store.loadedCount()).isEqualTo(3); // GH-90000
        assertThat(store.isLoaded("a [GH-90000]")).isFalse();
        assertThat(store.isLoaded("b [GH-90000]")).isTrue();
        assertThat(store.isLoaded("c [GH-90000]")).isTrue();
        assertThat(store.isLoaded("d [GH-90000]")).isTrue();
        // "a" should still be registered (just unloaded) // GH-90000
        assertThat(store.findById("a [GH-90000]")).isPresent();
    }

    // ── listModels ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listModels: returns all models when type is null [GH-90000]")
    void listModels_nullType_returnsAll() { // GH-90000
        store.register(buildMeta("stt1", ModelMetadata.ModelType.STT)); // GH-90000
        store.register(buildMeta("tts1", ModelMetadata.ModelType.TTS)); // GH-90000

        List<ModelMetadata> all = store.listAllModels(); // GH-90000
        assertThat(all).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("listModels: filters by type correctly [GH-90000]")
    void listModels_byType_filtersCorrectly() { // GH-90000
        store.register(buildMeta("stt1", ModelMetadata.ModelType.STT)); // GH-90000
        store.register(buildMeta("tts1", ModelMetadata.ModelType.TTS)); // GH-90000

        List<ModelMetadata> sttOnly = store.listModels(ModelMetadata.ModelType.STT); // GH-90000
        assertThat(sttOnly).hasSize(1); // GH-90000
        assertThat(sttOnly.get(0).modelId()).isEqualTo("stt1 [GH-90000]");
    }

    @Test
    @DisplayName("listModels: loaded models appear before unloaded [GH-90000]")
    void listModels_loadedFirst() { // GH-90000
        store.register(buildMeta("unloaded", ModelMetadata.ModelType.STT)); // GH-90000
        store.register(buildMeta("loaded", ModelMetadata.ModelType.STT)); // GH-90000
        store.load("loaded [GH-90000]");

        List<ModelMetadata> models = store.listModels(ModelMetadata.ModelType.STT); // GH-90000
        assertThat(models.get(0).modelId()).isEqualTo("loaded [GH-90000]");
    }

    // ── getActiveModel ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveModel: returns most recently loaded model of given type [GH-90000]")
    void getActiveModel_returnsMostRecent() { // GH-90000
        store.register(buildMeta("stt1", ModelMetadata.ModelType.STT)); // GH-90000
        store.register(buildMeta("stt2", ModelMetadata.ModelType.STT)); // GH-90000
        store.load("stt1 [GH-90000]");
        store.load("stt2 [GH-90000]");

        Optional<ModelMetadata> active = store.getActiveModel(ModelMetadata.ModelType.STT); // GH-90000
        assertThat(active).isPresent(); // GH-90000
        assertThat(active.get().modelId()).isEqualTo("stt2 [GH-90000]");
    }

    @Test
    @DisplayName("getActiveModel: returns empty when no model loaded [GH-90000]")
    void getActiveModel_noneLoaded_empty() { // GH-90000
        Optional<ModelMetadata> active = store.getActiveModel(ModelMetadata.ModelType.VISION); // GH-90000
        assertThat(active).isEmpty(); // GH-90000
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static ModelMetadata buildMeta(String id, ModelMetadata.ModelType type) { // GH-90000
        return ModelMetadata.builder() // GH-90000
                .modelId(id) // GH-90000
                .name(id + "-model") // GH-90000
                .type(type) // GH-90000
                .build(); // GH-90000
    }
}
