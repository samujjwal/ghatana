package com.ghatana.platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigManager.
 */
class ConfigManagerTest {

    @Test
    void testConstructorWithName() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");

        assertEquals("test", manager.getName()); // GH-90000
        assertTrue(manager.getSources().isEmpty()); // GH-90000
    }

    @Test
    void testConstructorWithSources() { // GH-90000
        SystemPropertiesConfigSource source = new SystemPropertiesConfigSource(); // GH-90000
        ConfigManager manager = new ConfigManager("test", java.util.List.of(source)); // GH-90000

        assertEquals("test", manager.getName()); // GH-90000
        assertEquals(1, manager.getSources().size()); // GH-90000
    }

    @Test
    void testAddSource() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");

        manager.addSource(new SystemPropertiesConfigSource()); // GH-90000

        assertEquals(1, manager.getSources().size()); // GH-90000
    }

    @Test
    void testAddSources() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");

        manager.addSources(java.util.List.of( // GH-90000
            new SystemPropertiesConfigSource(), // GH-90000
            new EnvironmentConfigSource() // GH-90000
        ));

        assertEquals(2, manager.getSources().size()); // GH-90000
    }

    @Test
    void testRemoveSource() { // GH-90000
        SystemPropertiesConfigSource source = new SystemPropertiesConfigSource(); // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");
        manager.addSource(source); // GH-90000

        manager.removeSource(source); // GH-90000

        assertTrue(manager.getSources().isEmpty()); // GH-90000
    }

    @Test
    void testClearSources() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");
        manager.addSource(new SystemPropertiesConfigSource()); // GH-90000

        manager.clearSources(); // GH-90000

        assertTrue(manager.getSources().isEmpty()); // GH-90000
    }

    @Test
    void testChaining() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]")
            .addSource(new SystemPropertiesConfigSource()) // GH-90000
            .addSource(new EnvironmentConfigSource()); // GH-90000

        assertEquals(2, manager.getSources().size()); // GH-90000
    }

    @Test
    void testGetStringFromFirstSource() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");

        // Create a mock source that returns a value
        ConfigSource mockSource = new ConfigSource() { // GH-90000
            @Override
            public Optional<String> getString(String key) { // GH-90000
                if (key.equals("test.key [GH-90000]")) {
                    return Optional.of("value [GH-90000]");
                }
                return Optional.empty(); // GH-90000
            }

            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override public boolean hasKey(String key) { return false; } // GH-90000
            @Override public String getName() { return "mock"; } // GH-90000
        };

        manager.addSource(mockSource); // GH-90000

        Optional<String> value = manager.getString("test.key [GH-90000]");
        assertTrue(value.isPresent()); // GH-90000
        assertEquals("value", value.get()); // GH-90000
    }

    @Test
    void testSourcePriority() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");

        // First source returns a value
        ConfigSource firstSource = new ConfigSource() { // GH-90000
            @Override
            public Optional<String> getString(String key) { // GH-90000
                return Optional.of("first [GH-90000]");
            }
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override public boolean hasKey(String key) { return true; } // GH-90000
            @Override public String getName() { return "first"; } // GH-90000
        };

        // Second source also returns a value (should be ignored) // GH-90000
        ConfigSource secondSource = new ConfigSource() { // GH-90000
            @Override
            public Optional<String> getString(String key) { // GH-90000
                return Optional.of("second [GH-90000]");
            }
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override public boolean hasKey(String key) { return true; } // GH-90000
            @Override public String getName() { return "second"; } // GH-90000
        };

        manager.addSource(firstSource).addSource(secondSource); // GH-90000

        // Should get value from first source
        Optional<String> value = manager.getString("any.key [GH-90000]");
        assertTrue(value.isPresent()); // GH-90000
        assertEquals("first", value.get()); // GH-90000
    }

    @Test
    void testHasKey() { // GH-90000
        ConfigManager manager = new ConfigManager("test [GH-90000]");

        ConfigSource mockSource = new ConfigSource() { // GH-90000
            @Override
            public boolean hasKey(String key) { // GH-90000
                return key.equals("existing [GH-90000]");
            }
            @Override public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override public String getName() { return "mock"; } // GH-90000
        };

        manager.addSource(mockSource); // GH-90000

        assertTrue(manager.hasKey("existing [GH-90000]"));
        assertFalse(manager.hasKey("non-existing [GH-90000]"));
    }

    @Test
    void testCreateDefault() { // GH-90000
        ConfigManager manager = ConfigManager.createDefault("default-test [GH-90000]");

        assertEquals("default-test", manager.getName()); // GH-90000
        assertEquals(2, manager.getSources().size()); // System properties + Environment // GH-90000
    }

    @Test
    void testGetAll(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a temporary config file
        File configFile = tempDir.resolve("test.conf [GH-90000]").toFile();
        try (FileWriter writer = new FileWriter(configFile)) { // GH-90000
            writer.write("key1 = value1\n [GH-90000]");
            writer.write("key2 = value2\n [GH-90000]");
        }

        ConfigManager manager = new ConfigManager("test [GH-90000]");
        manager.addSource(new FileConfigSource(configFile.getAbsolutePath())); // GH-90000

        Map<String, Object> all = manager.getAll(); // GH-90000

        assertFalse(all.isEmpty()); // GH-90000
        assertEquals("value1", all.get("key1 [GH-90000]"));
        assertEquals("value2", all.get("key2 [GH-90000]"));
    }
}
