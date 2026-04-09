package com.ghatana.yappc.ai.api.security;

import com.ghatana.platform.security.model.User;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for extracting authentication information from HTTP requests.
 *
 * @doc.type class
 * @doc.purpose Authentication utility for YAPPC controllers
 * @doc.layer product
 * @doc.pattern Utility
 * @since 1.0.0
 */
public final class AuthUtil {
  private static final Logger logger = LoggerFactory.getLogger(AuthUtil.class);

  private AuthUtil() {
  }

  public static User getCurrentUser(HttpRequest request) throws AuthenticationException {
    Object principalObj = request.getAttachment("userPrincipal");

    if (principalObj == null) {
      logger.error("No user principal found in request - authentication filter may not have been applied");
      throw new AuthenticationException("User not authenticated");
    }

    if (!(principalObj instanceof User principal)) {
      logger.error("Invalid principal type found in request: {}", principalObj.getClass().getName());
      throw new AuthenticationException("Invalid authentication state");
    }

    return principal;
  }

  public static String getCurrentUserId(HttpRequest request) throws AuthenticationException {
    return getCurrentUser(request).getUserId();
  }

  public static String getCurrentUsername(HttpRequest request) throws AuthenticationException {
    return getCurrentUser(request).getUsername();
  }

  public static String getCurrentTenantId(HttpRequest request) throws AuthenticationException {
    String email = getCurrentUser(request).getEmail();
    if (email != null && email.contains("@")) {
      String domain = email.substring(email.indexOf("@") + 1);
      if (domain.endsWith(".local")) {
        return domain.replace(".local", "");
      }
    }
    throw new AuthenticationException("Unable to resolve tenant ID from user principal - email domain extraction failed");
  }

  public static boolean isAuthenticated(HttpRequest request) {
    Object principalObj = request.getAttachment("userPrincipal");
    return principalObj instanceof User;
  }

  public static final class AuthenticationException extends Exception {
    public AuthenticationException(String message) {
      super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
