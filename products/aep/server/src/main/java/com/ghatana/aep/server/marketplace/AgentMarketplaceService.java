package com.ghatana.aep.server.marketplace;

import com.ghatana.aep.catalog.AepCentralCatalogService;
import com.ghatana.aep.catalog.CatalogValidationReport;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Marketplace discovery, publishing, and review aggregation for AEP agents
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AgentMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(AgentMarketplaceService.class);

    static final String MARKETPLACE_AGENT_COLLECTION = "aep_marketplace_agents";
    static final String MARKETPLACE_REVIEW_COLLECTION = "aep_marketplace_reviews";
    static final String MARKETPLACE_INSTALL_COLLECTION = "aep_marketplace_installs";

    @Nullable
    private final DataCloudClient dataCloudClient;
    private final List<CatalogAgentEntry> catalogEntries;
    private final Map<String, PublishedAgent> inMemoryListings = new ConcurrentHashMap<>();
    private final Map<String, List<MarketplaceReview>> inMemoryReviews = new ConcurrentHashMap<>();

    public AgentMarketplaceService(@Nullable DataCloudClient dataCloudClient) {
        this(dataCloudClient, loadCatalogEntries(Path.of("").toAbsolutePath().normalize()));
    }

    AgentMarketplaceService(
            @Nullable DataCloudClient dataCloudClient,
            Collection<CatalogAgentEntry> catalogEntries) {
        this.dataCloudClient = dataCloudClient;
        this.catalogEntries = List.copyOf(catalogEntries);
    }

    public Promise<List<MarketplaceAgentListing>> listAgents(
            String tenantId,
            @Nullable String search,
            @Nullable String capability,
            int limit) {
        return loadPublishedAgents(tenantId).then(published ->
                loadReviews(tenantId).map(reviews -> {
                    Map<String, ReviewAggregate> reviewSummary = aggregateReviews(reviews);
                    List<MarketplaceAgentListing> listings = mergeListings(published, reviewSummary);
                    return listings.stream()
                            .filter(listing -> matchesSearch(listing, search))
                            .filter(listing -> matchesCapability(listing, capability))
                            .sorted(Comparator
                                    .comparingDouble(MarketplaceAgentListing::averageRating).reversed()
                                    .thenComparingInt(MarketplaceAgentListing::reviewCount).reversed()
                                    .thenComparing(MarketplaceAgentListing::name, String.CASE_INSENSITIVE_ORDER))
                            .limit(Math.max(limit, 1))
                            .toList();
                }));
    }

    public Promise<Optional<MarketplaceAgentDetail>> getAgent(String tenantId, String agentId) {
        return loadPublishedAgents(tenantId).then(published ->
                loadReviews(tenantId).map(reviews -> {
                    Map<String, ReviewAggregate> reviewSummary = aggregateReviews(reviews);
                    List<MarketplaceAgentListing> listings = mergeListings(published, reviewSummary);
                    Optional<MarketplaceAgentListing> listing = listings.stream()
                            .filter(candidate -> candidate.id().equals(agentId))
                            .findFirst();
                    if (listing.isEmpty()) {
                        return Optional.<MarketplaceAgentDetail>empty();
                    }
                    List<MarketplaceReview> agentReviews = reviews.stream()
                            .filter(review -> review.agentId().equals(agentId))
                            .sorted(Comparator.comparing(MarketplaceReview::createdAt).reversed())
                            .toList();
                    return Optional.of(new MarketplaceAgentDetail(listing.get(), agentReviews));
                }));
    }

    public Promise<MarketplaceAgentListing> publishAgent(String tenantId, PublishAgentRequest request) {
        PublishedAgent listing = PublishedAgent.fromRequest(tenantId, request);
        if (dataCloudClient == null) {
            inMemoryListings.put(listing.id(), listing);
            return resolvePublishedListing(tenantId, listing.id());
        }

        return dataCloudClient.save(tenantId, MARKETPLACE_AGENT_COLLECTION, listing.toData())
                .map(saved -> toPublishedAgent(saved.data()))
                .then(saved -> resolvePublishedListing(tenantId, saved.id()));
    }

    public Promise<List<MarketplaceReview>> listReviews(String tenantId, String agentId) {
        return loadReviews(tenantId).map(reviews -> reviews.stream()
                .filter(review -> review.agentId().equals(agentId))
                .sorted(Comparator.comparing(MarketplaceReview::createdAt).reversed())
                .toList());
    }

    /**
     * Simulates marketplace installation so the UI can truthfully show version pinning,
     * compatibility posture, and the allowed execution path before registration.
     */
    public Promise<MarketplaceInstallSimulation> simulateInstallAgent(
            String tenantId,
            String agentId,
            InstallAgentRequest request) {
        return getAgent(tenantId, agentId).then(detail -> {
            if (detail.isEmpty()) {
                return Promise.ofException(new IllegalArgumentException(
                    "Marketplace agent not found: " + agentId));
            }
            return Promise.of(simulateInstall(detail.get().listing(), request));
        });
    }

    /**
     * T-07: Records a marketplace agent installation for a tenant.
     *
     * <p>Validates that the requested agent exists in the marketplace, then persists an
     * install record to Data Cloud (or to the in-memory fallback in dev mode).
     *
     * @param tenantId tenant performing the install
     * @param agentId  marketplace agent to install
     * @param request  optional install-time configuration
     * @return install confirmation record
     */
    public Promise<MarketplaceInstallRecord> installAgent(
            String tenantId,
            String agentId,
            InstallAgentRequest request) {
        return getAgent(tenantId, agentId).then(detail -> {
            if (detail.isEmpty()) {
                return Promise.ofException(new IllegalArgumentException(
                    "Marketplace agent not found: " + agentId));
            }
            MarketplaceAgentListing listing = detail.get().listing();
            MarketplaceInstallSimulation simulation = simulateInstall(listing, request);
            if (!simulation.allowedToInstall()) {
                return Promise.ofException(new IllegalArgumentException(
                    "Marketplace install blocked: " + String.join("; ", simulation.compatibilityNotes())));
            }
            MarketplaceInstallRecord record = MarketplaceInstallRecord.create(tenantId, listing, request, simulation);

            if (dataCloudClient == null) {
                log.info("[marketplace] install recorded in-memory tenantId={}, agentId={}", tenantId, agentId);
                return Promise.of(record);
            }
            return dataCloudClient.save(tenantId, MARKETPLACE_INSTALL_COLLECTION, record.toData())
                    .map(saved -> record)
                    .then(Promise::of, err -> {
                        log.warn("[marketplace] DataCloud install persist failed for tenantId={}, agentId={}: {}",
                            tenantId, agentId, err.getMessage());
                        return Promise.of(record);
                    });
        });
    }

    public Promise<MarketplaceReview> addReview(String tenantId, String agentId, CreateReviewRequest request) {
        MarketplaceReview review = MarketplaceReview.fromRequest(tenantId, agentId, request);
        if (dataCloudClient == null) {
            inMemoryReviews.computeIfAbsent(agentId, ignored -> new CopyOnWriteArrayList<>()).add(review);
            return Promise.of(review);
        }

        return dataCloudClient.save(tenantId, MARKETPLACE_REVIEW_COLLECTION, review.toData())
                .map(saved -> toMarketplaceReview(saved.data()));
    }

    private static MarketplaceInstallSimulation simulateInstall(
            MarketplaceAgentListing listing,
            InstallAgentRequest request) {
        String requestedVersion = defaultString(request.expectedVersion(), listing.version());
        String targetEnvironment = normalizeTargetEnvironment(request.targetEnvironment());
        boolean versionPinned = requestedVersion.equals(listing.version());
        boolean productionTarget = "production".equals(targetEnvironment);
        boolean unsupportedEnvironment = "unknown".equals(targetEnvironment);
        boolean elevatedRisk = isElevatedRisk(listing);

        List<String> notes = new ArrayList<>();
        if (!versionPinned) {
            notes.add("Requested version " + requestedVersion + " does not match published version " + listing.version() + ".");
        }
        if (unsupportedEnvironment) {
            notes.add("Target environment must be one of sandbox, staging, or production.");
        }
        if (productionTarget) {
            notes.add("Production execution must go through a pipeline with HITL review; direct marketplace execution is sandbox-only.");
        } else {
            notes.add("Direct execution remains limited to sandbox or lower-risk validation environments.");
        }
        if (elevatedRisk) {
            notes.add("This agent exposes elevated-risk capabilities or level and should be promoted through governed pipeline rollout.");
        }
        if ("catalog".equalsIgnoreCase(listing.source()) || "catalog+tenant".equalsIgnoreCase(listing.source())) {
            notes.add("Catalog provenance is available for compatibility review.");
        } else {
            notes.add("Tenant-published listing should be reviewed against local governance policy before broad rollout.");
        }

        String compatibilityStatus;
        if (!versionPinned || unsupportedEnvironment) {
            compatibilityStatus = "BLOCKED";
        } else if (productionTarget || elevatedRisk) {
            compatibilityStatus = "REVIEW_REQUIRED";
        } else {
            compatibilityStatus = "COMPATIBLE";
        }

        return new MarketplaceInstallSimulation(
                listing.id(),
                listing.name(),
                requestedVersion,
                listing.version(),
                targetEnvironment,
                versionPinned,
                compatibilityStatus,
                List.copyOf(notes),
                "SANDBOX_ONLY",
                "PIPELINE_HITL_REQUIRED",
                productionTarget || elevatedRisk,
                productionTarget || elevatedRisk ? "pipeline_hitl" : "sandbox_direct",
                versionPinned && !unsupportedEnvironment);
    }

    private static String normalizeTargetEnvironment(@Nullable String targetEnvironment) {
        if (targetEnvironment == null || targetEnvironment.isBlank()) {
            return "sandbox";
        }
        String normalized = targetEnvironment.trim().toLowerCase();
        return switch (normalized) {
            case "sandbox", "staging", "production" -> normalized;
            default -> "unknown";
        };
    }

    private static boolean isElevatedRisk(MarketplaceAgentListing listing) {
        if ("strategic".equalsIgnoreCase(listing.level())) {
            return true;
        }
        return listing.capabilities().stream()
                .map(value -> value.toLowerCase())
                .anyMatch(value -> value.contains("deploy")
                        || value.contains("write")
                        || value.contains("delete")
                        || value.contains("execute")
                        || value.contains("provision"));
    }

    private Promise<MarketplaceAgentListing> resolvePublishedListing(String tenantId, String agentId) {
        return getAgent(tenantId, agentId).map(detail -> detail
                .map(MarketplaceAgentDetail::listing)
                .orElseThrow(() -> new IllegalStateException("Published marketplace agent not found: " + agentId)));
    }

    private Promise<List<PublishedAgent>> loadPublishedAgents(String tenantId) {
        if (dataCloudClient == null) {
            return Promise.of(List.copyOf(inMemoryListings.values()));
        }
        return dataCloudClient.query(tenantId, MARKETPLACE_AGENT_COLLECTION, DataCloudClient.Query.limit(500))
                .map(entities -> entities.stream()
                        .map(entity -> toPublishedAgent(entity.data()))
                        .toList())
                .whenException(error -> log.error("[marketplace] failed to load listings tenant={}", tenantId, error));
    }

    private Promise<List<MarketplaceReview>> loadReviews(String tenantId) {
        if (dataCloudClient == null) {
            return Promise.of(inMemoryReviews.values().stream()
                    .flatMap(List::stream)
                    .toList());
        }
        return dataCloudClient.query(tenantId, MARKETPLACE_REVIEW_COLLECTION, DataCloudClient.Query.limit(1_000))
                .map(entities -> entities.stream()
                        .map(entity -> toMarketplaceReview(entity.data()))
                        .toList())
                .whenException(error -> log.error("[marketplace] failed to load reviews tenant={}", tenantId, error));
    }

    private List<MarketplaceAgentListing> mergeListings(
            List<PublishedAgent> published,
            Map<String, ReviewAggregate> reviewSummary) {
        Map<String, MarketplaceAgentListing> merged = new LinkedHashMap<>();

        for (CatalogAgentEntry entry : catalogEntries) {
            ReviewAggregate aggregate = reviewSummary.getOrDefault(entry.getId(), ReviewAggregate.empty());
            merged.put(entry.getId(), new MarketplaceAgentListing(
                    entry.getId(),
                    defaultString(entry.getName(), entry.getId()),
                    defaultString(entry.getDescription(), "Catalog-backed agent"),
                    defaultString(entry.getVersion(), "1.0.0"),
                    entry.getDomain(),
                    entry.getLevel(),
                    entry.getCapabilities().stream().sorted().toList(),
                    stringList(entry.getMetadata().get("tags")),
                    "catalog",
                    defaultString(entry.getCatalogId(), "platform"),
                    null,
                    null,
                    aggregate.averageRating(),
                    aggregate.reviewCount()));
        }

        for (PublishedAgent agent : published) {
            ReviewAggregate aggregate = reviewSummary.getOrDefault(agent.id(), ReviewAggregate.empty());
            String source = merged.containsKey(agent.id()) ? "catalog+tenant" : "tenant";
            merged.put(agent.id(), new MarketplaceAgentListing(
                    agent.id(),
                    agent.name(),
                    agent.description(),
                    agent.version(),
                    agent.domain(),
                    agent.level(),
                    agent.capabilities(),
                    agent.tags(),
                    source,
                    agent.owner(),
                    agent.publishedAt().toString(),
                    agent.updatedAt().toString(),
                    aggregate.averageRating(),
                    aggregate.reviewCount()));
        }

        return new ArrayList<>(merged.values());
    }

    private static Map<String, ReviewAggregate> aggregateReviews(List<MarketplaceReview> reviews) {
        return reviews.stream().collect(Collectors.groupingBy(
                MarketplaceReview::agentId,
                Collectors.collectingAndThen(Collectors.toList(), items -> {
                    double average = items.stream()
                            .mapToInt(MarketplaceReview::rating)
                            .average()
                            .orElse(0.0);
                    return new ReviewAggregate(average, items.size());
                })));
    }

    private static boolean matchesSearch(MarketplaceAgentListing listing, @Nullable String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.toLowerCase();
        return listing.id().toLowerCase().contains(normalized)
                || listing.name().toLowerCase().contains(normalized)
                || listing.description().toLowerCase().contains(normalized)
                || listing.capabilities().stream().anyMatch(capability -> capability.toLowerCase().contains(normalized))
                || listing.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalized));
    }

    private static boolean matchesCapability(MarketplaceAgentListing listing, @Nullable String capability) {
        if (capability == null || capability.isBlank()) {
            return true;
        }
        return listing.capabilities().stream().anyMatch(value -> value.equalsIgnoreCase(capability));
    }

    private static List<CatalogAgentEntry> loadCatalogEntries(Path repositoryRoot) {
        AepCentralCatalogService catalogService = AepCentralCatalogService.fromRepositoryRoot(repositoryRoot);
        CatalogValidationReport report = catalogService.loadAndValidate();
        if (!report.errors().isEmpty()) {
            log.warn("[marketplace] central catalog loaded with {} validation errors", report.errors().size());
        }
        return catalogService.getRegistry().allDefinitions().stream().toList();
    }

    private static PublishedAgent toPublishedAgent(Map<String, Object> data) {
        return new PublishedAgent(
                stringValue(data, "id"),
                defaultString(stringValue(data, "name"), stringValue(data, "id")),
                defaultString(stringValue(data, "description"), "Tenant-published marketplace agent"),
                defaultString(stringValue(data, "version"), "1.0.0"),
                defaultString(stringValue(data, "domain"), "general"),
                defaultString(stringValue(data, "level"), "worker"),
                stringList(data.get("capabilities")),
                stringList(data.get("tags")),
                defaultString(stringValue(data, "owner"), "tenant"),
                parseInstant(data.get("publishedAt")),
                parseInstant(data.get("updatedAt")));
    }

    private static MarketplaceReview toMarketplaceReview(Map<String, Object> data) {
        return new MarketplaceReview(
                stringValue(data, "id"),
                stringValue(data, "agentId"),
                stringValue(data, "tenantId"),
                defaultString(stringValue(data, "reviewer"), "operator"),
                intValue(data.get("rating"), 0),
                defaultString(stringValue(data, "title"), "Review"),
                defaultString(stringValue(data, "comment"), ""),
                parseInstant(data.get("createdAt")));
    }

    private static String stringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : "";
    }

    private static String defaultString(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Instant parseInstant(@Nullable Object value) {
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private static int intValue(@Nullable Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> stringList(@Nullable Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(item -> !item.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (value instanceof String raw && !raw.isBlank()) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }

    /**
     * @doc.type record
     * @doc.purpose Marketplace card payload returned to the AEP UI
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record MarketplaceAgentListing(
            String id,
            String name,
            String description,
            String version,
            String domain,
            String level,
            List<String> capabilities,
            List<String> tags,
            String source,
            String owner,
            @Nullable String publishedAt,
            @Nullable String updatedAt,
            double averageRating,
            int reviewCount) {
    }

    /**
     * @doc.type record
     * @doc.purpose Marketplace detail payload with listing metadata and reviews
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record MarketplaceAgentDetail(
            MarketplaceAgentListing listing,
            List<MarketplaceReview> reviews) {
    }

    /**
     * @doc.type record
     * @doc.purpose Publish request for tenant-managed marketplace listings
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record PublishAgentRequest(
            String id,
            String name,
            @Nullable String description,
            @Nullable String version,
            @Nullable String domain,
            @Nullable String level,
            @Nullable List<String> capabilities,
            @Nullable List<String> tags,
            @Nullable String owner) {
    }

    /**
     * @doc.type record
     * @doc.purpose Review submission request for marketplace agents
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record CreateReviewRequest(
            @Nullable String reviewer,
            int rating,
            @Nullable String title,
            @Nullable String comment) {
    }

    /**
     * @doc.type record
     * @doc.purpose Marketplace review payload exposed to the UI
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record MarketplaceReview(
            String id,
            String agentId,
            String tenantId,
            String reviewer,
            int rating,
            String title,
            String comment,
            Instant createdAt) {

        static MarketplaceReview fromRequest(String tenantId, String agentId, CreateReviewRequest request) {
            return new MarketplaceReview(
                    agentId + "-review-" + Instant.now().toEpochMilli(),
                    agentId,
                    tenantId,
                    defaultString(request.reviewer(), "operator"),
                    request.rating(),
                    defaultString(request.title(), "Marketplace review"),
                    defaultString(request.comment(), ""),
                    Instant.now());
        }

        Map<String, Object> toData() {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("agentId", agentId);
            data.put("tenantId", tenantId);
            data.put("reviewer", reviewer);
            data.put("rating", rating);
            data.put("title", title);
            data.put("comment", comment);
            data.put("createdAt", createdAt.toString());
            return data;
        }
    }

    private record ReviewAggregate(double averageRating, int reviewCount) {
        static ReviewAggregate empty() {
            return new ReviewAggregate(0.0, 0);
        }
    }

    private record PublishedAgent(
            String id,
            String name,
            String description,
            String version,
            String domain,
            String level,
            List<String> capabilities,
            List<String> tags,
            String owner,
            Instant publishedAt,
            Instant updatedAt) {

        static PublishedAgent fromRequest(String tenantId, PublishAgentRequest request) {
            Instant now = Instant.now();
            String id = defaultString(request.id(), request.name().toLowerCase().replace(' ', '-'));
            return new PublishedAgent(
                    id,
                    defaultString(request.name(), id),
                    defaultString(request.description(), "Tenant-published marketplace agent"),
                    defaultString(request.version(), "1.0.0"),
                    defaultString(request.domain(), "general"),
                    defaultString(request.level(), "worker"),
                    request.capabilities() != null ? List.copyOf(request.capabilities()) : List.of(),
                    request.tags() != null ? List.copyOf(request.tags()) : List.of(),
                    defaultString(request.owner(), tenantId),
                    now,
                    now);
        }

        Map<String, Object> toData() {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("description", description);
            data.put("version", version);
            data.put("domain", domain);
            data.put("level", level);
            data.put("capabilities", capabilities);
            data.put("tags", tags);
            data.put("owner", owner);
            data.put("publishedAt", publishedAt.toString());
            data.put("updatedAt", updatedAt.toString());
            return data;
        }
    }

    /**
     * @doc.type record
     * @doc.purpose Install request payload for marketplace agent installation
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record InstallAgentRequest(
            @Nullable String targetEnvironment,
            @Nullable Map<String, Object> config,
            @Nullable String expectedVersion) {
    }

    /**
     * @doc.type record
     * @doc.purpose Marketplace install simulation payload for governed preflight review
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record MarketplaceInstallSimulation(
            String agentId,
            String agentName,
            String requestedVersion,
            String availableVersion,
            String targetEnvironment,
            boolean versionPinned,
            String compatibilityStatus,
            List<String> compatibilityNotes,
            String directExecutionMode,
            String productionExecutionMode,
            boolean requiresHitl,
            String recommendedPath,
            boolean allowedToInstall) {
    }

    /**
     * @doc.type record
     * @doc.purpose Durable install confirmation record persisted to DataCloud
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record MarketplaceInstallRecord(
            String installId,
            String agentId,
            String agentName,
            String agentVersion,
            String tenantId,
            String compatibilityStatus,
            String recommendedPath,
            String directExecutionMode,
            String productionExecutionMode,
            @Nullable String targetEnvironment,
            Instant installedAt) {

        static MarketplaceInstallRecord create(
                String tenantId,
                MarketplaceAgentListing listing,
                InstallAgentRequest request,
                MarketplaceInstallSimulation simulation) {
            return new MarketplaceInstallRecord(
                    java.util.UUID.randomUUID().toString(),
                    listing.id(),
                    listing.name(),
                    listing.version(),
                    tenantId,
                    simulation.compatibilityStatus(),
                    simulation.recommendedPath(),
                    simulation.directExecutionMode(),
                    simulation.productionExecutionMode(),
                    request.targetEnvironment(),
                    Instant.now());
        }

        Map<String, Object> toData() {
            Map<String, Object> data = new HashMap<>();
            data.put("installId", installId);
            data.put("agentId", agentId);
            data.put("agentName", agentName);
            data.put("agentVersion", agentVersion);
            data.put("tenantId", tenantId);
            data.put("compatibilityStatus", compatibilityStatus);
            data.put("recommendedPath", recommendedPath);
            data.put("directExecutionMode", directExecutionMode);
            data.put("productionExecutionMode", productionExecutionMode);
            data.put("installedAt", installedAt.toString());
            if (targetEnvironment != null) {
                data.put("targetEnvironment", targetEnvironment);
            }
            return data;
        }
    }
}
