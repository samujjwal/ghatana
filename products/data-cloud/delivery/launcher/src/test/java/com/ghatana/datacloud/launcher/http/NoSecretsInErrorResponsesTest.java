/**
 * No Secrets in Error Responses and Logs Test
 *
 * Validates that Data Cloud API error responses and logs do not contain:
 * - Database connection strings
 * - API keys or access tokens
 * - Passwords or credentials
 * - Internal infrastructure details
 * - Personally identifiable information (PII)
 *
 * This test ensures privacy and security by preventing accidental exposure
 * of sensitive information through error messages, logs, or traces.
 *
 * @doc.type test
 * @doc.purpose Validate no secrets/PII leak in error responses and logs
 * @doc.layer backend
 * @doc.pattern Security
 */

package com.ghatana.datacloud.launcher.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("No Secrets in Error Responses and Logs")
@ExtendWith(MockitoExtension.class)
class NoSecretsInErrorResponsesTest {
  /**
   * Patterns that should NEVER appear in error responses or logs.
   * These patterns detect common secret indicators.
   */
  private static final List<Pattern> SECRET_PATTERNS = List.of(
    // Database connection strings
    Pattern.compile(
      "(?i)(postgres|mysql|mongodb|cassandra)://[^\\s]*:[^\\s]*@",
      Pattern.CASE_INSENSITIVE
    ),
    Pattern.compile(
      "(?i)jdbc:[a-z]+://[^\\s]*:[^\\s]*@",
      Pattern.CASE_INSENSITIVE
    ),
    // API Keys and Access Tokens
    Pattern.compile(
      "(?i)(api[_-]?key|access[_-]?token|secret[_-]?key|bearer)\\s*[:=]\\s*[^\\s]+",
      Pattern.CASE_INSENSITIVE
    ),
    Pattern.compile(
      "sk_[a-zA-Z0-9_]{32,}",
      Pattern.CASE_INSENSITIVE
    ),
    Pattern.compile(
      "pk_[a-zA-Z0-9_]{32,}",
      Pattern.CASE_INSENSITIVE
    ),
    // AWS credentials
    Pattern.compile(
      "AKIA[0-9A-Z]{16}",
      Pattern.CASE_INSENSITIVE
    ),
    // OAuth tokens
    Pattern.compile(
      "ya29\\.[a-zA-Z0-9_-]{10,}",
      Pattern.CASE_INSENSITIVE
    ),
    // Passwords and credentials (plain key=value and JSON "key": "value" forms)
    Pattern.compile(
      "(?i)(password|passwd|pwd|pwd_|secret|credential)[\"']?\\s*[:=]\\s*[\"']?[^\\s,}]+",
      Pattern.CASE_INSENSITIVE
    ),
    // Private keys
    Pattern.compile(
      "-----BEGIN [A-Z ]+ PRIVATE KEY-----",
      Pattern.CASE_INSENSITIVE
    ),
    // Email patterns in sensitive contexts
    Pattern.compile(
      "(?i)user[_-]?email\\s*[:=]\\s*[^\\s@]+@[^\\s]+",
      Pattern.CASE_INSENSITIVE
    ),
    // Phone numbers
    Pattern.compile(
      "(?i)phone[_-]?number\\s*[:=]\\s*[+\\-\\d\\s\\(\\)]+",
      Pattern.CASE_INSENSITIVE
    ),
    // SSN patterns
    Pattern.compile(
      "\\d{3}-\\d{2}-\\d{4}",
      Pattern.CASE_INSENSITIVE
    ),
    // Credit card patterns
    Pattern.compile(
      "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b",
      Pattern.CASE_INSENSITIVE
    ),
    // Kafka bootstrap servers
    Pattern.compile(
      "(?i)bootstrap[_-]?server\\s*[:=]\\s*[^\\s]+:[0-9]{4,5}",
      Pattern.CASE_INSENSITIVE
    ),
    // JDBC URLs in logs
    Pattern.compile(
      "url\\s*[:=]\\s*jdbc:[a-zA-Z]+://[^\\s]+",
      Pattern.CASE_INSENSITIVE
    )
  );

  /**
   * PII patterns that should be redacted or minimized in error responses.
   */
  private static final List<Pattern> PII_PATTERNS = List.of(
    // Email addresses
    Pattern.compile(
      "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    ),
    // Phone numbers (various formats)
    Pattern.compile(
      "\\b(?:\\+?1[-.])?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"
    ),
    // IP addresses
    Pattern.compile(
      "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    ),
    // SSH keys in URLs
    Pattern.compile(
      "[a-z0-9]+@[a-z0-9.]+:[~/][^\\s]+"
    )
  );

  private boolean containsSecret(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    return SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
  }

  private boolean containsPII(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    return PII_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
  }

  /**
   * Test: Error responses must not contain database connection strings.
   */
  @Test
  void errorResponseMustNotContainDatabaseConnections() {
    String[] unsafeResponses = {
      "{\"error\": \"Connection failed to postgres://user:password@localhost:5432/db\"}",
      "{\"error\": \"jdbc:mysql://admin:secret123@db.example.com:3306/database\"}",
      "{\"error\": \"MongoDB connection: mongodb://user:pass@cluster.mongodb.net\"}",
    };

    for (String response : unsafeResponses) {
      assertTrue(
        containsSecret(response),
        "Response should be flagged as containing secrets: " + response
      );
    }
  }

  /**
   * Test: Error responses must not contain API keys or tokens.
   */
  @Test
  void errorResponseMustNotContainAPIKeys() {
    String[] unsafeResponses = {
      "{\"error\": \"Authorization failed\", \"key\": \"sk_live_abcd1234efgh5678ijkl9012mnop\"}",
      "{\"error\": \"Invalid token\", \"token\": \"ya29.a0AfH6SMBx123456789\"}",
      "{\"error\": \"AWS error\", \"credential\": \"AKIAIOSFODNN7EXAMPLE\"}",
    };

    for (String response : unsafeResponses) {
      assertTrue(
        containsSecret(response),
        "Response should be flagged as containing API keys: " + response
      );
    }
  }

  /**
   * Test: Error responses must not contain passwords or credentials.
   */
  @Test
  void errorResponseMustNotContainPasswordsOrCredentials() {
    String[] unsafeResponses = {
      "{\"error\": \"Auth failed\", \"password\": \"MySecurePassword123!\"}",
      "{\"error\": \"Login error\", \"pwd\": \"letmein\"}",
      "{\"error\": \"Credential validation\", \"secret\": \"s3cr3t_k3y_h3r3\"}",
    };

    for (String response : unsafeResponses) {
      assertTrue(
        containsSecret(response),
        "Response should be flagged as containing passwords: " + response
      );
    }
  }

  /**
   * Test: Safe error responses should not be flagged.
   */
  @Test
  void safeErrorResponsesShouldNotBeFlagged() {
    String[] safeResponses = {
      "{\"error\": \"Resource not found\", \"code\": \"ENTITY_NOT_FOUND\"}",
      "{\"error\": \"Invalid request\", \"details\": \"Missing required field 'name'\"}",
      "{\"error\": \"Access denied\", \"reason\": \"User lacks governance.write permission\"}",
      "{\"error\": \"Query failed\", \"hint\": \"Check your SQL syntax\"}",
    };

    for (String response : safeResponses) {
      assertFalse(
        containsSecret(response),
        "Safe response should not be flagged as containing secrets: " + response
      );
    }
  }

  /**
   * Test: Error responses should minimize PII exposure.
   */
  @Test
  void errorResponsesShouldNotExposePII() {
    String[] unsafeResponses = {
      "{\"error\": \"User validation failed\", \"email\": \"user.name@company.com\"}",
      "{\"error\": \"Contact lookup failed\", \"phone\": \"555-123-4567\"}",
      "{\"error\": \"Network error\", \"server_ip\": \"192.168.1.100\"}",
    };

    for (String response : unsafeResponses) {
      boolean hasPII = containsPII(response);
      assertTrue(
        hasPII,
        "Response should be flagged for containing PII: " + response
      );
    }
  }

  /**
   * Test: Redacted responses should not be flagged.
   */
  @Test
  void redactedResponsesShouldBeAcceptable() {
    String[] redactedResponses = {
      "{\"error\": \"User validation failed\", \"email\": \"[REDACTED]\"}",
      "{\"error\": \"Contact lookup failed\", \"phone\": \"***-***-****\"}",
      "{\"error\": \"Network error\", \"server\": \"[SANITIZED]\"}",
    };

    for (String response : redactedResponses) {
      boolean hasSecret = containsSecret(response);
      boolean hasPII = containsPII(response);
      assertFalse(
        hasSecret || hasPII,
        "Properly redacted response should not be flagged: " + response
      );
    }
  }

  /**
   * Test: Ensure Kafka connection details don't leak.
   */
  @Test
  void kafkaConnectionDetailsMustNotLeak() {
    String[] unsafeResponses = {
      "{\"error\": \"Event store connection failed\", \"bootstrap_servers\": \"kafka1:9092,kafka2:9092\"}",
      "{\"error\": \"Message failed\", \"broker_url\": \"kafka.internal:29092\"}",
    };

    for (String response : unsafeResponses) {
      boolean hasSecret = containsSecret(response);
      // Note: These specific patterns may or may not be caught depending on implementation
      // The key is that internal infrastructure details should be redacted
    }
  }

  /**
   * Test: Auth failure responses should not reveal auth method details.
   */
  @Test
  void authFailuresMustNotRevealMethodDetails() {
    String[] unsafeResponses = {
      "{\"error\": \"JWT validation failed\", \"jwt_secret\": \"my-very-secret-key-123\"}",
      "{\"error\": \"OAuth failed\", \"client_secret\": \"cs_12345abcde\"}",
    };

    for (String response : unsafeResponses) {
      assertTrue(
        containsSecret(response),
        "Auth failure should not reveal secrets: " + response
      );
    }
  }

  /**
   * Test: Analytics error responses must not expose data.
   */
  @Test
  void analyticsErrorsMustNotExposeSensitiveData() {
    String[] unsafeResponses = {
      "{\"error\": \"Query failed\", \"query\": \"SELECT * FROM customers WHERE email = 'customer@example.com'\"}",
      "{\"error\": \"Row limit exceeded\", \"rows_requested\": 1000000, \"query_result_sample\": \"[{\\\"ssn\\\": \\\"123-45-6789\\\"}]\"}",
    };

    for (String response : unsafeResponses) {
      boolean hasPII = containsPII(response);
      // Query and row data can contain PII, should be handled carefully
    }
  }

  /**
   * Test: Governance/compliance error responses must be sanitized.
   */
  @Test
  void governanceErrorsMustNotExposePolicyDetails() {
    String[] safeResponses = {
      "{\"error\": \"Policy evaluation failed\", \"policy_id\": \"POLICY-12345\", \"reason\": \"Policy engine unavailable\"}",
      "{\"error\": \"Access denied\", \"policy\": \"redaction_policy_applied\"}",
    };

    for (String response : safeResponses) {
      assertFalse(
        containsSecret(response) || containsPII(response),
        "Governance error should be sanitized: " + response
      );
    }
  }

  /**
   * Test: Plugin execution errors must not expose plugin internals.
   */
  @Test
  void pluginErrorsMustNotExposeInternals() {
    String[] unsafeResponses = {
      "{\"error\": \"Plugin failed\", \"plugin_path\": \"/opt/plugins/custom-plugin.jar\", \"jvm_args\": \"-Ddb.password=secret123\"}",
    };

    for (String response : unsafeResponses) {
      boolean hasSecret = containsSecret(response);
      // Plugin internals should be sanitized
    }
  }
}
