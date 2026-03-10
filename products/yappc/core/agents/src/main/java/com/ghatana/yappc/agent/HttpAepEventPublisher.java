package com.ghatana.yappc.agent;

import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real HTTP publisher that pushes workflow events into AEP's event ingest endpoint. 
 * @doc.type class
 * @doc.purpose Handles http aep event publisher operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class HttpAepEventPublisher implements AepEventPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(HttpAepEventPublisher.class);

  private final HttpClient client;
  private final URI eventsUri;
  private final Duration timeout;

  public HttpAepEventPublisher(String baseUrl, Duration timeout) {
    this.timeout = timeout;
    this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.eventsUri = URI.create(baseUrl).resolve("/api/v1/events");
  }

  public static HttpAepEventPublisher fromEnvironment() {
    String mode = System.getenv().getOrDefault("AEP_MODE", "service").toLowerCase();
    if ("library".equals(mode)) {
      LOG.info("AEP_MODE=library set; using service endpoint fallback for SDLC event publication");
    }
    String host = System.getenv().getOrDefault("AEP_SERVICE_HOST", "127.0.0.1");
    String port = System.getenv().getOrDefault("AEP_SERVICE_PORT", "7004");
    String timeoutMs = System.getenv().getOrDefault("AEP_SERVICE_TIMEOUT_MS", "3000");
    String baseUrl = "http://" + host + ":" + port;
    long ms = Long.parseLong(timeoutMs);
    return new HttpAepEventPublisher(baseUrl, Duration.ofMillis(Math.max(ms, 500L)));
  }

  @Override
  public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
    try {
      Map<String, Object> eventBody = new LinkedHashMap<>();
      eventBody.put("tenantId", tenantId == null || tenantId.isBlank() ? "default" : tenantId);
      eventBody.put("type", eventType);
      eventBody.put("payload", payload == null ? Map.of() : payload);

      String jsonBody = JsonUtils.toJson(eventBody);
      HttpRequest request =
          HttpRequest.newBuilder(eventsUri)
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .header("X-Tenant-Id", String.valueOf(eventBody.get("tenantId")))
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warn(
            "AEP publish failed status={} type={} body={}",
            response.statusCode(),
            eventType,
            response.body());
      }
    } catch (Exception e) {
      LOG.warn("AEP publish failed for type={} (continuing): {}", eventType, e.toString());
    }
    return Promise.complete();
  }
}
