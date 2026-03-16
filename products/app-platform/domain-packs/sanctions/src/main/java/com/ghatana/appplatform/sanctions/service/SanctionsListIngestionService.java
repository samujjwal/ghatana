package com.ghatana.appplatform.sanctions.service;

import com.ghatana.appplatform.sanctions.domain.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Ingests sanctions lists from OFAC (XML), UN (XML), EU (XML), and NRB (CSV)
 *              and performs an atomic swap on the in-memory list (D14-009).
 *              Parses each list format into a canonical {@link SanctionsEntry} and delegates
 *              the atomic swap to {@link ScreeningEngineService#loadList}.
 * @doc.layer   Application Service
 * @doc.pattern Atomic Reference Swap (lock-free hot reload)
 */
public class SanctionsListIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsListIngestionService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final ScreeningEngineService engine;
    private final SanctionsEntryStore entryStore;
    private final HttpClient httpClient;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public SanctionsListIngestionService(ScreeningEngineService engine,
                                          SanctionsEntryStore entryStore,
                                          Executor executor,
                                          Consumer<Object> eventPublisher) {
        this.engine = engine;
        this.entryStore = entryStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /** Ingest all configured lists and perform atomic swap (D14-009). */
    public Promise<Void> ingestAll(IngestionConfig config) {
        return Promise.ofBlocking(executor, () -> {
            var all = new ArrayList<SanctionsEntry>();

            if (config.ofacUrl() != null) {
                all.addAll(ingestOfac(config.ofacUrl()));
                log.info("OFAC entries loaded: {}", all.size());
            }
            if (config.unUrl() != null) {
                var unEntries = ingestUn(config.unUrl());
                all.addAll(unEntries);
                log.info("UN entries loaded: {}", unEntries.size());
            }
            if (config.euUrl() != null) {
                var euEntries = ingestEu(config.euUrl());
                all.addAll(euEntries);
                log.info("EU entries loaded: {}", euEntries.size());
            }
            if (config.nrbCsvUrl() != null) {
                var nrbEntries = ingestNrbCsv(config.nrbCsvUrl());
                all.addAll(nrbEntries);
                log.info("NRB entries loaded: {}", nrbEntries.size());
            }

            // Persist to DB for audit trail, then atomic swap into memory
            entryStore.replaceAll(all);
            engine.loadList(all);

            eventPublisher.accept(new SanctionsListRefreshedEvent(all.size()));
            log.info("Sanctions list atomic swap complete: total={}", all.size());
            return (Void) null;
        });
    }

    // ─── OFAC SDN XML Parser ─────────────────────────────────────────────────

    private List<SanctionsEntry> ingestOfac(String url) throws Exception {
        try (var stream = fetch(url)) {
            var doc = parseXml(stream);
            var entries = new ArrayList<SanctionsEntry>();
            NodeList sdnEntries = doc.getElementsByTagName("sdnEntry");
            for (int i = 0; i < sdnEntries.getLength(); i++) {
                var el = (Element) sdnEntries.item(i);
                String entryId = "OFAC-" + text(el, "uid");
                String lastName = text(el, "lastName");
                String firstName = text(el, "firstName");
                String primaryName = (firstName.isEmpty() ? lastName : firstName + " " + lastName).trim();
                String entityType = text(el, "sdnType").equalsIgnoreCase("Individual")
                        ? "INDIVIDUAL" : "ENTITY";

                var aliases = new ArrayList<String>();
                NodeList akas = el.getElementsByTagName("aka");
                for (int j = 0; j < akas.getLength(); j++) {
                    var aka = (Element) akas.item(j);
                    String akaLast = text(aka, "lastName");
                    String akaFirst = text(aka, "firstName");
                    String akaName = (akaFirst.isEmpty() ? akaLast : akaFirst + " " + akaLast).trim();
                    if (!akaName.isEmpty()) aliases.add(akaName);
                }

                entries.add(new SanctionsEntry(entryId, SanctionsListType.OFAC_SDN, primaryName,
                        List.copyOf(aliases), ScreeningEntityType.valueOf(entityType),
                        null, null, "latest"));
            }
            return entries;
        }
    }

    // ─── UN Consolidated XML Parser ──────────────────────────────────────────

    private List<SanctionsEntry> ingestUn(String url) throws Exception {
        try (var stream = fetch(url)) {
            var doc = parseXml(stream);
            var entries = new ArrayList<SanctionsEntry>();
            NodeList individuals = doc.getElementsByTagName("INDIVIDUAL");
            for (int i = 0; i < individuals.getLength(); i++) {
                var el = (Element) individuals.item(i);
                String dataid = text(el, "DATAID");
                String first = text(el, "FIRST_NAME");
                String second = text(el, "SECOND_NAME");
                String third = text(el, "THIRD_NAME");
                String fourth = text(el, "FOURTH_NAME");
                String primary = (first + " " + second + " " + third + " " + fourth).trim()
                        .replaceAll("\\s+", " ");

                var aliases = new ArrayList<String>();
                NodeList akas = el.getElementsByTagName("ALIAS");
                for (int j = 0; j < akas.getLength(); j++) {
                    var aka = (Element) akas.item(j);
                    String akaName = text(aka, "ALIAS_NAME");
                    if (!akaName.isEmpty()) aliases.add(akaName);
                }

                String nationality = text(el, "NATIONALITY");
                String dob = text(el, "DATE_OF_BIRTH");
                entries.add(new SanctionsEntry("UN-" + dataid, SanctionsListType.UN_CONSOLIDATED,
                        primary, List.copyOf(aliases), ScreeningEntityType.INDIVIDUAL,
                        dob.isEmpty() ? null : dob, nationality.isEmpty() ? null : nationality, "latest"));
            }
            return entries;
        }
    }

    // ─── EU Asset Freeze XML Parser ──────────────────────────────────────────

    private List<SanctionsEntry> ingestEu(String url) throws Exception {
        try (var stream = fetch(url)) {
            var doc = parseXml(stream);
            var entries = new ArrayList<SanctionsEntry>();
            NodeList subjects = doc.getElementsByTagName("sanctionEntity");
            for (int i = 0; i < subjects.getLength(); i++) {
                var el = (Element) subjects.item(i);
                String logicalId = el.getAttribute("logicalId");
                String nameEl = text(el, "wholeName");
                if (nameEl.isEmpty()) {
                    String first = text(el, "firstName");
                    String last = text(el, "lastName");
                    nameEl = (first + " " + last).trim();
                }
                String subjectType = el.getAttribute("subjectType").contains("person")
                        ? "INDIVIDUAL" : "ENTITY";

                entries.add(new SanctionsEntry("EU-" + logicalId, SanctionsListType.EU_ASSET_FREEZE,
                        nameEl, List.of(), ScreeningEntityType.valueOf(subjectType),
                        null, null, "latest"));
            }
            return entries;
        }
    }

    // ─── NRB CSV Parser ──────────────────────────────────────────────────────

    private List<SanctionsEntry> ingestNrbCsv(String url) throws Exception {
        try (var stream = fetch(url)) {
            var entries = new ArrayList<SanctionsEntry>();
            var content = new String(stream.readAllBytes());
            var lines = content.split("\\r?\\n");
            for (int i = 1; i < lines.length; i++) {           // skip header
                var cols = lines[i].split(",", -1);
                if (cols.length < 2) continue;
                String id = cols[0].trim();
                String name = cols[1].trim();
                if (name.isEmpty()) continue;
                entries.add(new SanctionsEntry("NRB-" + id, SanctionsListType.NRB_LOCAL,
                        name, List.of(), ScreeningEntityType.INDIVIDUAL,
                        null, null, "latest"));
            }
            return entries;
        }
    }

    // ─── Utilities ───────────────────────────────────────────────────────────

    private InputStream fetch(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch sanctions list: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Document parseXml(InputStream stream) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        // Disable external entity processing to prevent XXE injection
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(stream);
    }

    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().trim();
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record IngestionConfig(String ofacUrl, String unUrl, String euUrl, String nrbCsvUrl) {}

    public interface SanctionsEntryStore {
        void replaceAll(List<SanctionsEntry> entries);
        List<SanctionsEntry> findAll();
    }

    public record SanctionsListRefreshedEvent(int entryCount) {}
}
