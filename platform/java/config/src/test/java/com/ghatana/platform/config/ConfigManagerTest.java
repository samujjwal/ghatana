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
    void testConstructorWithName() {
        ConfigManager manager = new ConfigManager("test");
        
        assertEquals("test", manager.getName());
        assertTrue(manager.getSources().isEmpty());
    }

    @Test
    void testConstructorWithSources() {
        SystemPropertiesConfigSource source = new SystemPropertiesConfigSource();
        ConfigManager manager = new ConfigManager("test", java.util.List.of(source));
        
        assertEquals("test", manager.getName());
        assertEquals(1, manager.getSources().size());
    }

    @Test
    void testAddSource() {
        ConfigManager manager = new ConfigManager("test");
        
        manager.addSource(new SystemPropertiesConfigSource());
        
        assertEquals(1, manager.getSources().size());
    }

    @Test
    void testAddSources() {
        ConfigManager manager = new ConfigManager("test");
        
        manager.addSources(java.util.List.of(
            new SystemPropertiesConfigSource(),
            new EnvironmentConfigSource()
        ));
        
        assertEquals(2, manager.getSources().size());
    }

    @Test
    void testRemoveSource() {
        SystemPropertiesConfigSource source = new SystemPropertiesConfigSource();
        ConfigManager manager = new ConfigManager("test");
        manager.addSource(source);
        
        manager.removeSource(source);
        
        assertTrue(manager.getSources().isEmpty());
    }

    @Test
    void testClearSources() {
        ConfigManager manager = new ConfigManager("test");
        manager.addSource(new SystemPropertiesConfigSource());
        
        manager.clearSources();
        
        assertTrue(manager.getSources().isEmpty());
    }

    @Test
    void testChaining() {
        ConfigManager manager = new ConfigManager("test")
            .addSource(new SystemPropertiesConfigSource())
            .addSource(new EnvironmentConfigSource());
        
        assertEquals(2, manager.getSources().size());
    }

    @Test
    void testGetStringFromFirstSource() {
        ConfigManager manager = new ConfigManager("test");
        
        // Create a mock source that returns a value
        ConfigSource mockSource = new ConfigSource() {
            @Override
            public Optional<String> getString(String key) {
                if (key.equals("test.key")) {
                    return Optional.of("value");
                }
                return Optional.empty();
            }
            
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override public Map<String, Object> getAll() { return Map.of(); }
            @Override public boolean hasKey(String key) { return false; }
            @Override public String getName() { return "mock"; }
        };
        
        manager.addSource(mockSource);
        
        Optional<String> value = manager.getString("test.key");
        assertTrue(value.isPresent());
        assertEquals("value", value.get());
    }

    @Test
    void testSourcePriority() {
        ConfigManager manager = new ConfigManager("test");
        
        // First source returns a value
        ConfigSource firstSource = new ConfigSource() {
            @Override
            public Optional<String> getString(String key) {
                return Optional.of("first");
            }
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override public Map<String, Object> getAll() { return Map.of(); }
            @Override public boolean hasKey(String key) { return true; }
            @Override public String getName() { return "first"; }
        };
        
        // Second source also returns a value (should be ignored)
        ConfigSource secondSource = new ConfigSource() {
            @Override
            public Optional<String> getString(String key) {
                return Optional.of("second");
            }
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override public Map<String, Object> getAll() { return Map.of(); }
            @Override public boolean hasKey(String key) { return true; }
            @Override public String getName() { return "second"; }
        };
        
        manager.addSource(firstSource).addSource(secondSource);
        
        // Should get value from first source
        Optional<String> value = manager.getString("any.key");
        assertTrue(value.isPresent());
        assertEquals("first", value.get());
    }

    @Test
    void testHasKey() {
        ConfigManager manager = new ConfigManager("test");
        
        ConfigSource mockSource = new ConfigSource() {
            @Override
            public boolean hasKey(String key) {
                return key.equals("existing");
            }
            @Override public Optional<String> getString(String key) { return Optional.empty(); }
            @Override public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override public Map<String, Object> getAll() { return Map.of(); }
            @Override public String getName() { return "mock"; }
        };
        
        manager.addSource(mockSource);
        
        assertTrue(manager.hasKey("existing"));
        assertFalse(manager.hasKey("non-existing"));
    }

    @Test
    void testCreateDefault() {
        ConfigManager manager = ConfigManager.createDefault("default-test");
        
        assertEquals("default-test", manager.getName());
        assertEquals(2, manager.getSources().size()); // System properties + Environment
    }

    @Test
    void testGetAll(@TempDir Path tempDir) throws Exception {
        // Create a temporary config file
        File configFile = tempDir.resolve("test.conf").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("key1 = value1\n");
            writer.write("key2 = value2\n");
        }
        
        ConfigManager manager = new ConfigManager("test");
        manager.addSource(new FileConfigSource(configFile.getAbsolutePath()));
        
        Map<String, Object> all = manager.getAll();
        
        assertFalse(all.isEmpty());
        assertEquals("value1", all.get("key1"));
        assertEquals("value2", all.get("key2"));
    }
}
