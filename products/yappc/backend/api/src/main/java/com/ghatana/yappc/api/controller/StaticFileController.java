/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.controller;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.http.MediaType;
import io.activej.promise.Promise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static file controller for serving frontend assets and static content.
 *
 * <p><b>Purpose</b><br>
 * Serves static files from a configured directory with proper MIME type detection,
 * caching headers, and security protections.
 *
 * <p><b>Features</b><br>
 * - MIME type detection based on file extension - Cache-Control headers for performance - Path
 * traversal protection - 404 handling for missing files - Directory listing prevention
 *
 * <p><b>Security</b><br>
 * - Prevents access to files outside the static root - Blocks hidden files (starting with .) -
 * Sanitizes file paths to prevent directory traversal
 *
 * @doc.type class
 * @doc.purpose Serve static files (HTML, CSS, JS, images, etc.)
 * @doc.layer infrastructure
 * @doc.pattern Controller
 */
public class StaticFileController {

  private static final Logger logger = LoggerFactory.getLogger(StaticFileController.class);

  /** Default static files directory - can be overridden via configuration. */
  private static final String DEFAULT_STATIC_ROOT = "./static";

  /** Cache duration for static assets (1 hour in seconds). */
  private static final long CACHE_MAX_AGE = 3600;

  /** Map of file extensions to MIME types. */
  private static final Map<String, String> MIME_TYPES = new HashMap<>();

  static {
    // Text types
    MIME_TYPES.put("html", "text/html");
    MIME_TYPES.put("htm", "text/html");
    MIME_TYPES.put("css", "text/css");
    MIME_TYPES.put("js", "application/javascript");
    MIME_TYPES.put("json", "application/json");
    MIME_TYPES.put("xml", "application/xml");
    MIME_TYPES.put("txt", "text/plain");
    MIME_TYPES.put("md", "text/markdown");

    // Image types
    MIME_TYPES.put("png", "image/png");
    MIME_TYPES.put("jpg", "image/jpeg");
    MIME_TYPES.put("jpeg", "image/jpeg");
    MIME_TYPES.put("gif", "image/gif");
    MIME_TYPES.put("svg", "image/svg+xml");
    MIME_TYPES.put("ico", "image/x-icon");
    MIME_TYPES.put("webp", "image/webp");

    // Font types
    MIME_TYPES.put("woff", "font/woff");
    MIME_TYPES.put("woff2", "font/woff2");
    MIME_TYPES.put("ttf", "font/ttf");
    MIME_TYPES.put("otf", "font/otf");

    // Application types
    MIME_TYPES.put("pdf", "application/pdf");
    MIME_TYPES.put("zip", "application/zip");
    MIME_TYPES.put("gz", "application/gzip");
  }

  private final Path staticRoot;

  /** Creates a StaticFileController with the default static root directory. */
  public StaticFileController() {
    this(DEFAULT_STATIC_ROOT);
  }

  /**
   * Creates a StaticFileController with a custom static root directory.
   *
   * @param staticRootPath the path to the static files directory
   */
  public StaticFileController(String staticRootPath) {
    this.staticRoot = Paths.get(staticRootPath).toAbsolutePath().normalize();
    logger.info("Static file controller initialized with root: {}", this.staticRoot);
  }

  /**
   * Serves a static file based on the request path.
   *
   * @param request the HTTP request
   * @return a promise resolving to the HTTP response
   */
  public Promise<HttpResponse> serveFile(HttpRequest request) {
    String path = request.getRelativePath();

    // Remove leading slash if present
    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    // Default to index.html for root path
    if (path.isEmpty() || path.endsWith("/")) {
      path = path + "index.html";
    }

    // Validate and resolve the file path
    Path filePath;
    try {
      filePath = resolveFilePath(path);
    } catch (SecurityException e) {
      logger.warn("Security violation for path: {} - {}", path, e.getMessage());
      return Promise.of(HttpResponse.ofCode(403).withPlainText("Forbidden").build());
    }

    // Check if file exists and is readable
    if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
      logger.debug("File not found: {}", filePath);
      return Promise.of(HttpResponse.ofCode(404).withPlainText("Not Found").build());
    }

    // Prevent directory listing
    if (Files.isDirectory(filePath)) {
      logger.debug("Directory access blocked: {}", filePath);
      return Promise.of(HttpResponse.ofCode(403).withPlainText("Forbidden").build());
    }

    // Read and serve the file
    try {
      byte[] content = Files.readAllBytes(filePath);
      String contentType = getContentType(filePath);

      HttpResponse response =
          HttpResponse.ok200()
              .withBody(content)
            .withHeader(HttpHeaders.CONTENT_TYPE, contentType)
            .withHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=" + CACHE_MAX_AGE)
              .build();

      logger.debug("Served file: {} ({} bytes, {})", filePath, content.length, contentType);
      return Promise.of(response);

    } catch (IOException e) {
      logger.error("Error reading file: {}", filePath, e);
      return Promise.of(
          HttpResponse.ofCode(500).withPlainText("Internal Server Error").build());
    }
  }

  /**
   * Resolves a requested path to a safe file path within the static root.
   *
   * @param requestedPath the path from the request
   * @return the resolved Path object
   * @throws SecurityException if the path attempts directory traversal
   */
  private Path resolveFilePath(String requestedPath) throws SecurityException {
    // Block hidden files and paths containing ..
    if (requestedPath.contains("..") || requestedPath.contains("~")) {
      throw new SecurityException("Path traversal attempt detected");
    }

    // Block paths starting with .
    String fileName = Paths.get(requestedPath).getFileName().toString();
    if (fileName.startsWith(".")) {
      throw new SecurityException("Hidden files are not accessible");
    }

    Path resolvedPath = staticRoot.resolve(requestedPath).normalize();

    // Ensure the resolved path is within the static root
    if (!resolvedPath.startsWith(staticRoot)) {
      throw new SecurityException("Path escapes static root directory");
    }

    return resolvedPath;
  }

  /**
   * Determines the content type for a file based on its extension.
   *
   * @param filePath the path to the file
   * @return the MIME type string
   */
  private String getContentType(Path filePath) {
    String fileName = filePath.getFileName().toString().toLowerCase();
    int lastDotIndex = fileName.lastIndexOf('.');

    if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
      String extension = fileName.substring(lastDotIndex + 1);
      String mimeType = MIME_TYPES.get(extension);
      if (mimeType != null) {
        return mimeType;
      }
    }

    // Default to binary stream for unknown types
    return "application/octet-stream";
  }

  /**
   * Returns the configured static root path.
   *
   * @return the static root path
   */
  public Path getStaticRoot() {
    return staticRoot;
  }
}
