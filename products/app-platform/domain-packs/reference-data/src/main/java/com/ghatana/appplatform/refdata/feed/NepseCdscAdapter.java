package com.ghatana.appplatform.refdata.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.InstrumentStatus;
import com.ghatana.appplatform.refdata.domain.InstrumentType;
import com.ghatana.appplatform.refdata.domain.MarketEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type       Adapter (T3 Plugin)
 * @doc.purpose    Concrete T3 reference data feed adapter for NEPSE (Nepal Stock
 *                 Exchange) and CDSC (Central Depository and Settlement Company).
 *                 Fetches the instrument list from NEPSE, maps symbols to ISIN via
 *                 CDSC, and normalises both into the instrument master schema.
 *                 D11-009: nepse_sync_updatesAll, cdsc_isin_mapping,
 *                          newListing_detected, sync_schedule_preMarket,
 *                          parse_error_graceful.
 * @doc.layer      Adapter (T3 Plugin)
 * @doc.pattern    External Feed Adapter (T3 Network Plugin)
 */
public class NepseCdscAdapter implements RefDataFeedAdapter {

    private static final Logger log = LoggerFactory.getLogger(NepseCdscAdapter.class);
    private static final String ADAPTER_ID = "nepse-cdsc";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** In dev/test this is overridden to a mock URL via K-02 config. */
    private final String nepseApiUrl;
    private final String cdscApiUrl;
    private final HttpClient httpClient;
    private Consumer<RefDataUpdateEvent> updateCallback;

    public NepseCdscAdapter(String nepseApiUrl, String cdscApiUrl) {
        this.nepseApiUrl = nepseApiUrl;
        this.cdscApiUrl = cdscApiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Override
    public String adapterId() { return ADAPTER_ID; }

    @Override
    public String feedUrl() { return nepseApiUrl; }

    @Override
    public void connect() {
        log.info("nepse-cdsc.connect nepse={} cdsc={}", nepseApiUrl, cdscApiUrl);
    }

    @Override
    public void disconnect() {
        log.info("nepse-cdsc.disconnect");
    }

    /**
     * Fetch the current NEPSE instrument list and enrich with CDSC ISIN mapping.
     * On parse error for any individual record, log and continue (graceful degradation).
     */
    @Override
    public List<Instrument> fetchInstruments() {
        List<Instrument> result = new ArrayList<>();
        try {
            String nepseJson = get(nepseApiUrl + "/api/v1/instruments");
            // Map<symbol, isin> from CDSC
            Map<String, String> isinMap = fetchIsinMap();
            result.addAll(parseNepseInstruments(nepseJson, isinMap));
        } catch (Exception ex) {
            log.warn("nepse.fetch.error error={}", ex.getMessage());
        }
        return result;
    }

    @Override
    public List<MarketEntity> fetchEntities() {
        List<MarketEntity> result = new ArrayList<>();
        try {
            // Fetch brokers
            String brokersJson = get(nepseApiUrl + "/api/v1/brokers");
            result.addAll(parseBrokerEntities(brokersJson));
        } catch (Exception ex) {
            log.warn("nepse.entity.broker.error error={}", ex.getMessage());
        }
        try {
            // Fetch listed issuers (companies)
            String issuersJson = get(nepseApiUrl + "/api/v1/issuers");
            result.addAll(parseIssuerEntities(issuersJson));
        } catch (Exception ex) {
            log.warn("nepse.entity.issuer.error error={}", ex.getMessage());
        }
        return result;
    }

    @Override
    public void onUpdate(Consumer<RefDataUpdateEvent> callback) {
        this.updateCallback = callback;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, String> fetchIsinMap() {
        try {
            String json = get(cdscApiUrl + "/api/v1/isin-map");
            return parseCdscIsinMap(json);
        } catch (Exception ex) {
            log.warn("cdsc.isin_map.error error={}", ex.getMessage());
            return Map.of();
        }
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    /**
     * Parse NEPSE JSON instrument array into Instrument records.
     *
     * <p>Expected shape:
     * <pre>{@code
     * [
     *   {"symbol":"NABIL","sector":"Commercial Banks",
     *    "lot_size":10,"tick_size":"1.00","status":"ACTIVE"}
     * ]
     * }</pre>
     */
    private List<Instrument> parseNepseInstruments(String json, Map<String, String> isinMap) {
        List<Instrument> instruments = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                log.warn("nepse.instruments.parse: expected JSON array, got {}", root.getNodeType());
                return instruments;
            }
            for (JsonNode node : root) {
                try {
                    String symbol = node.path("symbol").asText(null);
                    if (symbol == null || symbol.isBlank()) continue;

                    String sector     = node.path("sector").asText("Unknown");
                    int lotSize       = node.path("lot_size").asInt(10);
                    String tickStr    = node.path("tick_size").asText("1.00");
                    String statusStr  = node.path("status").asText("ACTIVE");

                    InstrumentStatus status;
                    try {
                        status = InstrumentStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        status = InstrumentStatus.PENDING_APPROVAL;
                    }

                    instruments.add(new Instrument(
                            UUID.randomUUID(),
                            symbol, "NEPSE",
                            isinMap.getOrDefault(symbol, ""),
                            symbol,
                            InstrumentType.EQUITY,
                            status,
                            sector,
                            lotSize,
                            new BigDecimal(tickStr),
                            "NPR",
                            LocalDate.now(), null,
                            Instant.now(), "", Map.of()));
                } catch (Exception entryEx) {
                    log.warn("nepse.parse.entry.error symbol={} error={}",
                            node.path("symbol").asText("?"), entryEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("nepse.parse.error error={}", ex.getMessage());
        }
        return instruments;
    }

    /**
     * Parse the CDSC ISIN mapping response.
     *
     * <p>Expected shape: {@code {"NABIL": "NP0000000123", "NICA": "NP0000000456", ...}}
     */
    private Map<String, String> parseCdscIsinMap(String json) {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                log.warn("cdsc.isin_map.parse: expected JSON object, got {}", root.getNodeType());
                return result;
            }
            root.fields().forEachRemaining(entry ->
                result.put(entry.getKey(), entry.getValue().asText("")));
        } catch (Exception ex) {
            log.warn("cdsc.isin_map.parse.error error={}", ex.getMessage());
        }
        return result;
    }

    /**
     * Parse broker entities from NEPSE broker list response.
     *
     * <p>Expected shape:
     * <pre>{@code
     * [{"broker_no":1,"name":"Agrawal Securities","address":"Kathmandu"}]
     * }</pre>
     */
    private List<MarketEntity> parseBrokerEntities(String json) {
        List<MarketEntity> result = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) return result;
            for (JsonNode node : root) {
                String id   = "BROKER-" + node.path("broker_no").asText("0");
                String name = node.path("name").asText(null);
                if (name == null || name.isBlank()) continue;
                result.add(new MarketEntity(id, "BROKER", name,
                        node.path("address").asText(""), "NEPSE", Instant.now()));
            }
        } catch (Exception ex) {
            log.warn("nepse.broker.parse.error error={}", ex.getMessage());
        }
        return result;
    }

    /**
     * Parse issuer (listed company) entities from NEPSE issuers response.
     *
     * <p>Expected shape:
     * <pre>{@code
     * [{"symbol":"NABIL","company_name":"Nabil Bank Ltd","sector":"Commercial Banks"}]
     * }</pre>
     */
    private List<MarketEntity> parseIssuerEntities(String json) {
        List<MarketEntity> result = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) return result;
            for (JsonNode node : root) {
                String symbol = node.path("symbol").asText(null);
                String name   = node.path("company_name").asText(null);
                if (symbol == null || name == null) continue;
                result.add(new MarketEntity(symbol, "ISSUER", name,
                        node.path("sector").asText(""), "NEPSE", Instant.now()));
            }
        } catch (Exception ex) {
            log.warn("nepse.issuer.parse.error error={}", ex.getMessage());
        }
        return result;
    }
