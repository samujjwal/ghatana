// filepath: /Users/samujjwal/Development/ghatana/products/shared-services/speech-to-text/libs/stt-core-java/src/main/java/com/ghatana/stt/core/storage/ProfileStorage.java
package com.ghatana.stt.core.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.stt.core.api.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Encrypted storage for user profiles.
 *
 * <p>Stores user profiles with AES-GCM encryption to protect
 * sensitive voice adaptation data.
 *
 * @doc.type class
 * @doc.purpose Encrypted profile persistence
 * @doc.layer core
 * @doc.pattern Repository
 */
public class ProfileStorage {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileStorage.class);

    private final Path profilesDirectory;
    private final ProfileEncryption encryption;
    private final ObjectMapper objectMapper;

    /**
     * Create a profile storage with encryption.
     *
     * @param profilesDirectory Directory to store encrypted profiles
     * @param encryption Encryption instance
     */
    public ProfileStorage(Path profilesDirectory, ProfileEncryption encryption) throws IOException {
        this.profilesDirectory = profilesDirectory;
        this.encryption = encryption;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Ensure directory exists
        Files.createDirectories(profilesDirectory);
    }

    /**
     * Save a user profile with encryption.
     *
     * @param profile The profile to save
     */
    public void save(UserProfile profile) throws IOException {
        try {
            Path profilePath = getProfilePath(profile.getProfileId());

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(profile);

            // Encrypt
            String encrypted = encryption.encryptString(json);

            // Write to file
            Files.writeString(
                profilePath,
                encrypted,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );

            LOG.info("Saved encrypted profile: {}", profile.getProfileId());

        } catch (Exception e) {
            throw new IOException("Failed to save profile: " + profile.getProfileId(), e);
        }
    }

    /**
     * Load a user profile with decryption.
     *
     * @param profileId The profile ID
     * @return The loaded profile, or empty if not found
     */
    public Optional<UserProfile> load(String profileId) {
        try {
            Path profilePath = getProfilePath(profileId);

            if (!Files.exists(profilePath)) {
                LOG.debug("Profile not found: {}", profileId);
                return Optional.empty();
            }

            // Read encrypted data
            String encrypted = Files.readString(profilePath);

            // Decrypt
            String json = encryption.decryptString(encrypted);

            // Deserialize
            UserProfile profile = objectMapper.readValue(json, UserProfile.class);

            LOG.info("Loaded encrypted profile: {}", profileId);
            return Optional.of(profile);

        } catch (Exception e) {
            LOG.error("Failed to load profile: {}", profileId, e);
            return Optional.empty();
        }
    }

    /**
     * Delete a profile.
     *
     * @param profileId The profile ID
     * @return true if deleted, false if not found
     */
    public boolean delete(String profileId) {
        try {
            Path profilePath = getProfilePath(profileId);

            if (Files.exists(profilePath)) {
                Files.delete(profilePath);
                LOG.info("Deleted profile: {}", profileId);
                return true;
            }

            return false;

        } catch (IOException e) {
            LOG.error("Failed to delete profile: {}", profileId, e);
            return false;
        }
    }

    /**
     * Check if a profile exists.
     *
     * @param profileId The profile ID
     * @return true if the profile exists
     */
    public boolean exists(String profileId) {
        Path profilePath = getProfilePath(profileId);
        return Files.exists(profilePath);
    }

    /**
     * Get the file path for a profile.
     */
    private Path getProfilePath(String profileId) {
        return profilesDirectory.resolve(profileId + ".enc");
    }
}

