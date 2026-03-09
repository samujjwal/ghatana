/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * Auth Service Tests
 *
 * Tests authentication service methods including:
 * - User registration
 * - User login
 * - Token generation and verification
 * - Password hashing and comparison
 * - Token refresh
 * - Logout
 * - User profile operations
 * - Password reset
 */

import * as authService from "../../services/auth.service";
import { pool, query } from "../../db";
import { userFixtures } from "../fixtures";
import { randomEmail } from "../setup";

describe("AuthService", () => {
  describe("hashPassword", () => {
    it("should hash password successfully", async () => {
      // GIVEN: A plain text password
      const password = "TestPassword123!";

      // WHEN: Password is hashed
      const hash = await authService.hashPassword(password);

      // THEN: Hash is generated and different from original password
      expect(hash).toBeDefined();
      expect(hash).not.toBe(password);
      expect(hash.length).toBeGreaterThan(20);
    });

    it("should generate different hashes for same password", async () => {
      // GIVEN: Same password hashed twice
      const password = "TestPassword123!";

      // WHEN: Password is hashed multiple times
      const hash1 = await authService.hashPassword(password);
      const hash2 = await authService.hashPassword(password);

      // THEN: Different hashes are generated (bcrypt uses salt)
      expect(hash1).not.toBe(hash2);
    });
  });

  describe("comparePassword", () => {
    it("should return true for correct password", async () => {
      // GIVEN: A password and its hash
      const password = "TestPassword123!";
      const hash = await authService.hashPassword(password);

      // WHEN: Password is compared with its hash
      const isMatch = await authService.comparePassword(password, hash);

      // THEN: Comparison returns true
      expect(isMatch).toBe(true);
    });

    it("should return false for incorrect password", async () => {
      // GIVEN: A password hash and different password
      const password = "TestPassword123!";
      const hash = await authService.hashPassword(password);

      // WHEN: Wrong password is compared with hash
      const isMatch = await authService.comparePassword("WrongPassword", hash);

      // THEN: Comparison returns false
      expect(isMatch).toBe(false);
    });
  });

  describe("generateAccessToken", () => {
    it("should generate valid access token", () => {
      // GIVEN: A user ID
      const userId = "test-user-id";

      // WHEN: Access token is generated
      const token = authService.generateAccessToken(userId);

      // THEN: Valid JWT token is returned (3 parts separated by dots)
      expect(token).toBeDefined();
      expect(typeof token).toBe("string");
      expect(token.split(".").length).toBe(3);
    });

    it("should generate different tokens for different users", () => {
      // GIVEN: Two different user IDs
      // WHEN: Access tokens are generated for each user
      const token1 = authService.generateAccessToken("user-1");
      const token2 = authService.generateAccessToken("user-2");

      // THEN: Tokens are different
      expect(token1).not.toBe(token2);
    });
  });

  describe("generateRefreshToken", () => {
    it("should generate valid refresh token", () => {
      // GIVEN: A user ID
      const userId = "test-user-id";

      // WHEN: Refresh token is generated
      const token = authService.generateRefreshToken(userId);

      // THEN: Valid JWT token is returned
      expect(token).toBeDefined();
      expect(typeof token).toBe("string");
      expect(token.split(".").length).toBe(3);
    });

    it("should generate unique refresh tokens even for same user", () => {
      // GIVEN: Same user ID used twice
      const userId = "test-user-id";

      // WHEN: Refresh tokens are generated multiple times
      const token1 = authService.generateRefreshToken(userId);
      const token2 = authService.generateRefreshToken(userId);

      // THEN: Tokens are unique (jti ensures uniqueness)
      expect(token1).not.toBe(token2);
    });
  });

  describe("verifyAccessToken", () => {
    it("should verify valid access token", () => {
      // GIVEN: A valid access token
      const userId = "test-user-id";
      const token = authService.generateAccessToken(userId);

      // WHEN: Token is verified
      const decoded = authService.verifyAccessToken(token);

      // THEN: Decoded payload contains user ID
      expect(decoded).not.toBeNull();
      expect(decoded?.userId).toBe(userId);
    });

    it("should return null for invalid token", () => {
      // GIVEN: An invalid token string
      // WHEN: Token verification is attempted
      const decoded = authService.verifyAccessToken("invalid-token");

      // THEN: Null is returned
      expect(decoded).toBeNull();
    });

    it("should return null for refresh token", () => {
      // GIVEN: A refresh token (wrong type)
      const userId = "test-user-id";
      const refreshToken = authService.generateRefreshToken(userId);

      // WHEN: Refresh token is verified as access token
      const decoded = authService.verifyAccessToken(refreshToken);

      // THEN: Null is returned (wrong token type)
      expect(decoded).toBeNull();
    });
  });

  describe("verifyRefreshToken", () => {
    it("should verify valid refresh token", () => {
      const userId = "test-user-id";
      const token = authService.generateRefreshToken(userId);
      const decoded = authService.verifyRefreshToken(token);

      expect(decoded).not.toBeNull();
      expect(decoded?.userId).toBe(userId);
    });

    it("should return null for invalid token", () => {
      const decoded = authService.verifyRefreshToken("invalid-token");

      expect(decoded).toBeNull();
    });

    it("should return null for access token", () => {
      const userId = "test-user-id";
      const accessToken = authService.generateAccessToken(userId);
      const decoded = authService.verifyRefreshToken(accessToken);

      expect(decoded).toBeNull(); // Wrong token type
    });
  });

  describe("register", () => {
    it("should register new user with valid data", async () => {
      // GIVEN: Valid user registration data
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
        displayName: "Test User",
      };

      // WHEN: User registration is called
      const result = await authService.register(userData);

      // THEN: User is created with tokens and unverified email
      expect(result).toBeDefined();
      expect(result.user).toBeDefined();
      expect(result.user.email).toBe(userData.email.toLowerCase());
      expect(result.user.display_name).toBe(userData.displayName);
      expect(result.accessToken).toBeDefined();
      expect(result.refreshToken).toBeDefined();
      expect(result.user.email_verified).toBe(false);
    });

    it("should register user without display name", async () => {
      // GIVEN: User data without display name
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };

      // WHEN: User registration is called
      const result = await authService.register(userData);

      // THEN: User is created with null display name
      expect(result.user).toBeDefined();
      expect(result.user.display_name).toBeNull();
    });

    it("should convert email to lowercase", async () => {
      // GIVEN: Email in uppercase
      const userData = {
        email: "TEST@EXAMPLE.COM",
        password: "ValidPassword123!",
      };

      // WHEN: User registration is called
      const result = await authService.register(userData);

      // THEN: Email is stored in lowercase
      expect(result.user.email).toBe("test@example.com");
    });

    it("should reject duplicate email", async () => {
      // GIVEN: A registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      await authService.register(userData);

      // WHEN: Same email is used for another registration
      // THEN: Error is thrown
      await expect(authService.register(userData)).rejects.toThrow(
        "User with this email already exists"
      );
    });

    it("should store hashed password not plain text", async () => {
      // GIVEN: User registration data
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };

      const result = await authService.register(userData);

      // Query database directly to check password is hashed
      const dbUser = await query(
        "SELECT password_hash FROM users WHERE id = $1",
        [result.user.id]
      );

      expect(dbUser[0].password_hash).not.toBe(userData.password);
      expect(dbUser[0].password_hash).toContain("$2"); // bcrypt hash prefix
    });

    it("should create refresh token in database", async () => {
      // GIVEN: Newly registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const result = await authService.register(userData);

      // WHEN: Querying refresh tokens
      const tokens = await query(
        "SELECT * FROM refresh_tokens WHERE user_id = $1",
        [result.user.id]
      );

      // THEN: Refresh token is stored in database
      expect(tokens.length).toBeGreaterThan(0);
      expect(tokens[0].token).toBe(result.refreshToken);
    });
  });

  describe("login", () => {
    it("should login with valid credentials", async () => {
      // GIVEN: Registered user
      const password = "ValidPassword123!";
      const userData = {
        email: randomEmail(),
        password,
      };
      await authService.register(userData);

      // WHEN: User logs in with correct credentials
      const result = await authService.login({
        email: userData.email,
        password,
      });

      // THEN: User receives access and refresh tokens
      expect(result).toBeDefined();
      expect(result.user.email).toBe(userData.email.toLowerCase());
      expect(result.accessToken).toBeDefined();
      expect(result.refreshToken).toBeDefined();
    });

    it("should reject wrong password", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      await authService.register(userData);

      // WHEN: Login with wrong password
      // THEN: Login is rejected with error
      await expect(
        authService.login({
          email: userData.email,
          password: "WrongPassword123!",
        })
      ).rejects.toThrow("Invalid email or password");
    });

    it("should reject non-existent email", async () => {
      // GIVEN: No registered user

      // WHEN: Login with non-existent email
      // THEN: Login is rejected with error
      await expect(
        authService.login({
          email: "nonexistent@example.com",
          password: "Password123!",
        })
      ).rejects.toThrow("Invalid email or password");
    });

    it("should be case-insensitive for email", async () => {
      // GIVEN: User registered with lowercase email
      const email = randomEmail();
      const userData = {
        email: email.toLowerCase(),
        password: "ValidPassword123!",
      };
      await authService.register(userData);

      // WHEN: Login with uppercase email
      const result = await authService.login({
        email: email.toUpperCase(),
        password: "ValidPassword123!",
      });

      // THEN: Login succeeds and email is normalized
      expect(result.user.email).toBe(email.toLowerCase());
    });

    it("should update last login timestamp", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // Wait a bit
      await new Promise((resolve) => setTimeout(resolve, 100));

      // WHEN: User logs in
      await authService.login({
        email: userData.email,
        password: userData.password,
      });

      // THEN: last_login timestamp is updated
      const user = await query("SELECT last_login FROM users WHERE id = $1", [
        registered.user.id,
      ]);

      expect(user[0].last_login).toBeDefined();
    });
  });

  describe("refreshAccessToken", () => {
    it("should refresh access token with valid refresh token", async () => {
      // GIVEN: Registered user with valid refresh token
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // Wait 1ms to ensure different timestamp
      await new Promise((resolve) => setTimeout(resolve, 1));

      // WHEN: Access token is refreshed
      const result = await authService.refreshAccessToken(
        registered.refreshToken
      );

      // THEN: New access token is generated and valid
      expect(result.accessToken).toBeDefined();
      const decoded = authService.verifyAccessToken(result.accessToken);
      expect(decoded?.userId).toBe(registered.user.id);
    });

    it("should reject invalid refresh token", async () => {
      // GIVEN: Invalid refresh token

      // WHEN: Token refresh attempted with invalid token
      // THEN: Error is thrown
      await expect(
        authService.refreshAccessToken("invalid-token")
      ).rejects.toThrow("Invalid refresh token");
    });

    it("should reject non-existent refresh token", async () => {
      // GIVEN: Valid JWT format but not in database
      const userId = "test-user-id";
      const fakeToken = authService.generateRefreshToken(userId);

      // WHEN: Token refresh attempted
      // THEN: Error is thrown
      await expect(authService.refreshAccessToken(fakeToken)).rejects.toThrow(
        "Refresh token not found"
      );
    });

    it("should reject access token as refresh token", async () => {
      // GIVEN: Registered user with access token
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: Access token used as refresh token
      // THEN: Error is thrown
      await expect(
        authService.refreshAccessToken(registered.accessToken)
      ).rejects.toThrow("Invalid refresh token");
    });
  });

  describe("logout", () => {
    it("should delete refresh token", async () => {
      // GIVEN: Logged in user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: User logs out
      await authService.logout(registered.refreshToken);

      // THEN: Refresh token is deleted from database
      const tokens = await query(
        "SELECT * FROM refresh_tokens WHERE token = $1",
        [registered.refreshToken]
      );
      expect(tokens.length).toBe(0);
    });

    it("should not throw error for non-existent token", async () => {
      // GIVEN: Non-existent refresh token

      // WHEN: Logout attempted with non-existent token
      // THEN: No error is thrown (graceful handling)
      await expect(
        authService.logout("non-existent-token")
      ).resolves.not.toThrow();
    });
  });

  describe("getUserById", () => {
    it("should return user by ID", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
        displayName: "Test User",
      };
      const registered = await authService.register(userData);

      // WHEN: User is fetched by ID
      const user = await authService.getUserById(registered.user.id);

      // THEN: User profile is returned
      expect(user).toBeDefined();
      expect(user?.id).toBe(registered.user.id);
      expect(user?.email).toBe(userData.email.toLowerCase());
      expect(user?.display_name).toBe(userData.displayName);
    });

    it("should return null for non-existent user", async () => {
      // GIVEN: Non-existent user ID (valid UUID format)
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: User is fetched by non-existent ID
      const user = await authService.getUserById(nonExistentId);

      // THEN: Null is returned
      expect(user).toBeNull();
    });
  });

  describe("updateProfile", () => {
    it("should update display name", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: Display name is updated
      const updated = await authService.updateProfile(registered.user.id, {
        displayName: "Updated Name",
      });

      // THEN: Profile is updated with new display name
      expect(updated.display_name).toBe("Updated Name");
    });

    it("should update photo URL", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: Photo URL is updated
      const updated = await authService.updateProfile(registered.user.id, {
        photoUrl: "https://example.com/photo.jpg",
      });

      // THEN: Profile is updated with new photo URL
      expect(updated.photo_url).toBe("https://example.com/photo.jpg");
    });

    it("should update both display name and photo URL", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: Both display name and photo URL are updated
      const updated = await authService.updateProfile(registered.user.id, {
        displayName: "New Name",
        photoUrl: "https://example.com/new.jpg",
      });

      // THEN: Profile is updated with both changes
      expect(updated.display_name).toBe("New Name");
      expect(updated.photo_url).toBe("https://example.com/new.jpg");
    });

    it("should reject empty updates", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: Update profile with empty object
      // THEN: Error is thrown
      await expect(
        authService.updateProfile(registered.user.id, {})
      ).rejects.toThrow("No fields to update");
    });

    it("should reject non-existent user", async () => {
      // GIVEN: Non-existent user ID

      // WHEN: Update profile for non-existent user
      // THEN: Error is thrown
      await expect(
        authService.updateProfile("00000000-0000-0000-0000-000000000000", {
          displayName: "Test",
        })
      ).rejects.toThrow("User not found");
    });
  });

  describe("requestPasswordReset", () => {
    it("should generate reset token for existing user", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      await authService.register(userData);

      // WHEN: Password reset is requested
      const resetToken = await authService.requestPasswordReset(userData.email);

      // THEN: Reset token is generated
      expect(resetToken).toBeDefined();
      expect(typeof resetToken).toBe("string");
      expect(resetToken.length).toBeGreaterThan(20);
    });

    it("should store reset token in database", async () => {
      // GIVEN: Registered user
      const userData = {
        email: randomEmail(),
        password: "ValidPassword123!",
      };
      const registered = await authService.register(userData);

      // WHEN: Password reset is requested
      const resetToken = await authService.requestPasswordReset(userData.email);

      // THEN: Reset token is stored in database with expiry
      const user = await query(
        "SELECT password_reset_token, password_reset_expires_at FROM users WHERE id = $1",
        [registered.user.id]
      );
      expect(user[0].password_reset_token).toBe(resetToken);
      expect(user[0].password_reset_expires_at).toBeDefined();
    });

    it("should reject non-existent email", async () => {
      // GIVEN: Non-existent email

      // WHEN: Password reset requested for non-existent email
      // THEN: Error is thrown
      await expect(
        authService.requestPasswordReset("nonexistent@example.com")
      ).rejects.toThrow("User not found");
    });

    it("should be case-insensitive for email", async () => {
      // GIVEN: User registered with lowercase email
      const email = randomEmail();
      const userData = {
        email: email.toLowerCase(),
        password: "ValidPassword123!",
      };
      await authService.register(userData);

      // WHEN: Password reset requested with uppercase email
      const resetToken = await authService.requestPasswordReset(
        email.toUpperCase()
      );

      // THEN: Reset token is generated
      expect(resetToken).toBeDefined();
    });
  });

  describe("resetPassword", () => {
    it("should reset password with valid token", async () => {
      // GIVEN: User with password reset token
      const userData = {
        email: randomEmail(),
        password: "OldPassword123!",
      };
      await authService.register(userData);
      const resetToken = await authService.requestPasswordReset(userData.email);

      // WHEN: Password is reset with valid token
      await authService.resetPassword(resetToken, "NewPassword123!");

      // THEN: User can login with new password
      const result = await authService.login({
        email: userData.email,
        password: "NewPassword123!",
      });
      expect(result).toBeDefined();
    });

    it("should reject old password after reset", async () => {
      // GIVEN: User who has reset password
      const userData = {
        email: randomEmail(),
        password: "OldPassword123!",
      };
      await authService.register(userData);
      const resetToken = await authService.requestPasswordReset(userData.email);
      await authService.resetPassword(resetToken, "NewPassword123!");

      // WHEN: Login attempted with old password
      // THEN: Login fails
      await expect(
        authService.login({
          email: userData.email,
          password: "OldPassword123!",
        })
      ).rejects.toThrow("Invalid email or password");
    });

    it("should reject invalid reset token", async () => {
      // GIVEN: Invalid reset token

      // WHEN: Password reset attempted with invalid token
      // THEN: Error is thrown
      await expect(
        authService.resetPassword("invalid-token", "NewPassword123!")
      ).rejects.toThrow("Invalid or expired reset token");
    });

    it("should clear reset token after use", async () => {
      // GIVEN: User with password reset token
      const userData = {
        email: randomEmail(),
        password: "OldPassword123!",
      };
      const registered = await authService.register(userData);
      const resetToken = await authService.requestPasswordReset(userData.email);

      // WHEN: Password is reset
      await authService.resetPassword(resetToken, "NewPassword123!");

      // THEN: Reset token is cleared from database
      const user = await query(
        "SELECT password_reset_token, password_reset_expires_at FROM users WHERE id = $1",
        [registered.user.id]
      );
      expect(user[0].password_reset_token).toBeNull();
      expect(user[0].password_reset_expires_at).toBeNull();
    });

    it("should reject expired reset token", async () => {
      // GIVEN: User with expired reset token (manually set to past)
      const userData = {
        email: randomEmail(),
        password: "OldPassword123!",
      };
      const registered = await authService.register(userData);
      const resetToken = await authService.requestPasswordReset(userData.email);

      // Manually expire the token by setting expiry to past
      await query(
        "UPDATE users SET password_reset_expires_at = $1 WHERE id = $2",
        [new Date(Date.now() - 1000), registered.user.id]
      );

      // WHEN: Password reset attempted with expired token
      // THEN: Error is thrown
      await expect(
        authService.resetPassword(resetToken, "NewPassword123!")
      ).rejects.toThrow("Invalid or expired reset token");
    });
  });
});
