package com.ghatana.stt.core.storage;

import com.ghatana.stt.core.api.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProfileStorage.
 *
 * @doc.type test
 * @doc.purpose Test encrypted profile persistence
 * @doc.layer core
 */
@Tag("integration")
@DisplayName("ProfileStorage Tests")
class ProfileStorageTest {

    @TempDir
    Path tempDir;

    private ProfileStorage storage;
    private ProfileEncryption encryption;

    @BeforeEach
    void setUp() throws Exception {
        encryption = new ProfileEncryption();
        storage = new ProfileStorage(tempDir, encryption);
    }

    @Test
    @DisplayName("Should save and load profile successfully")
    void shouldSaveAndLoadProfile() throws IOException {
        // GIVEN
        UserProfile profile = UserProfile.create("Test User");

        // WHEN
        storage.save(profile);
        Optional<UserProfile> loaded = storage.load(profile.getProfileId());

        // THEN
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getProfileId()).isEqualTo(profile.getProfileId());
        assertThat(loaded.get().getDisplayName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should return empty for non-existent profile")
    void shouldReturnEmptyForNonExistent() {
        // WHEN
        Optional<UserProfile> loaded = storage.load("non-existent-id");

        // THEN
        assertThat(loaded).isEmpty();
    }

    @Test
    @DisplayName("Should check profile existence")
    void shouldCheckExistence() throws IOException {
        // GIVEN
        UserProfile profile = UserProfile.create("Test User");

        // WHEN
        boolean existsBefore = storage.exists(profile.getProfileId());
        storage.save(profile);
        boolean existsAfter = storage.exists(profile.getProfileId());

        // THEN
        assertThat(existsBefore).isFalse();
        assertThat(existsAfter).isTrue();
    }

    @Test
    @DisplayName("Should delete profile")
    void shouldDeleteProfile() throws IOException {
        // GIVEN
        UserProfile profile = UserProfile.create("Test User");
        storage.save(profile);

        // WHEN
        boolean deleted = storage.delete(profile.getProfileId());
        Optional<UserProfile> loaded = storage.load(profile.getProfileId());

        // THEN
        assertThat(deleted).isTrue();
        assertThat(loaded).isEmpty();
    }

    @Test
    @DisplayName("Should return false when deleting non-existent profile")
    void shouldReturnFalseForNonExistentDelete() {
        // WHEN
        boolean deleted = storage.delete("non-existent-id");

        // THEN
        assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("Should overwrite existing profile")
    void shouldOverwriteExisting() throws IOException {
        // GIVEN
        UserProfile profile1 = UserProfile.create("Original Name");
        String profileId = profile1.getProfileId();
        storage.save(profile1);

        // Modify profile
        profile1.setDisplayName("Updated Name");

        // WHEN
        storage.save(profile1);
        Optional<UserProfile> loaded = storage.load(profileId);

        // THEN
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getDisplayName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("Should create directory if not exists")
    void shouldCreateDirectoryIfNotExists() throws Exception {
        // GIVEN
        Path newDir = tempDir.resolve("nested/profiles");
        ProfileStorage newStorage = new ProfileStorage(newDir, encryption);

        // THEN
        assertThat(Files.exists(newDir)).isTrue();
        assertThat(Files.isDirectory(newDir)).isTrue();
    }

    @Test
    @DisplayName("Should store encrypted data on disk")
    void shouldStoreEncryptedData() throws IOException {
        // GIVEN
        UserProfile profile = UserProfile.create("Test User");

        // WHEN
        storage.save(profile);

        // Read raw file content
        Path profileFile = tempDir.resolve(profile.getProfileId() + ".enc");
        assertThat(Files.exists(profileFile)).isTrue();

        String rawContent = Files.readString(profileFile);

        // THEN - Raw content should not contain plaintext profile ID or name
        // (It's Base64 encrypted, so should be gibberish)
        assertThat(rawContent).doesNotContain("Test User");
    }

    @Test
    @DisplayName("Should handle multiple profiles")
    void shouldHandleMultipleProfiles() throws IOException {
        // GIVEN
        UserProfile profile1 = UserProfile.create("User 1");
        UserProfile profile2 = UserProfile.create("User 2");
        UserProfile profile3 = UserProfile.create("User 3");

        // WHEN
        storage.save(profile1);
        storage.save(profile2);
        storage.save(profile3);

        // THEN
        assertThat(storage.load(profile1.getProfileId())).isPresent();
        assertThat(storage.load(profile2.getProfileId())).isPresent();
        assertThat(storage.load(profile3.getProfileId())).isPresent();

        // Verify directory has 3 files
        long fileCount = Files.list(tempDir).count();
        assertThat(fileCount).isEqualTo(3);
    }

    @Test
    @DisplayName("Should fail gracefully with corrupted data")
    void shouldFailGracefullyWithCorruptedData() throws IOException {
        // GIVEN
        Path corruptedFile = tempDir.resolve("corrupted-profile.enc");
        Files.writeString(corruptedFile, "This is not encrypted data!");

        // WHEN
        Optional<UserProfile> loaded = storage.load("corrupted-profile");

        // THEN
        assertThat(loaded).isEmpty();
    }
}

