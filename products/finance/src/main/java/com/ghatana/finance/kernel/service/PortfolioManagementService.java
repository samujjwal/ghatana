package com.ghatana.finance.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Portfolio Management Service for investment tracking and performance analysis.
 *
 * <p>Manages investment portfolios with:
 * <ul>
 *   <li>Portfolio creation and lifecycle management</li>
 *   <li>Position tracking and valuation</li>
 *   <li>Performance analytics and reporting</li>
 *   <li>Rebalancing recommendations</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance portfolio management service
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class PortfolioManagementService extends AbstractDataService {

    private static final String PORTFOLIO_DATASET = "finance.portfolios";

    private final Map<String, Portfolio> portfolioCache = new ConcurrentHashMap<>();

    public PortfolioManagementService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "portfolio-management";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            PORTFOLIO_DATASET,
            Map.of(
                "portfolioId", "string",
                "accountId", "string",
                "name", "string",
                "status", "string",
                "totalValue", "decimal",
                "createdAt", "timestamp"
            ),
            Map.of("retention", "10years")
        ).whenException(e -> {});
    }

    @Override
    public Promise<Void> stop() {
        portfolioCache.clear();
        return Promise.complete();
    }

    public Promise<Portfolio> createPortfolio(PortfolioRequest request) {
        ensureRunning();

        String portfolioId = generateId("pf");
        Instant now = Instant.now();

        Portfolio portfolio = new Portfolio(
            portfolioId,
            request.getAccountId(),
            request.getName(),
            request.getDescription(),
            request.getCurrency(),
            BigDecimal.ZERO,
            "ACTIVE",
            now,
            now
        );

        return createRecord(
            PORTFOLIO_DATASET,
            portfolioId,
            portfolio,
            Map.of(
                "accountId", portfolio.getAccountId(),
                "status", portfolio.getStatus(),
                "createdAt", now.toString()
            ),
            "Portfolio",
            1
        ).then(stored -> audit("PORTFOLIO_CREATE", stored.getAccountId(),
            "Portfolio " + portfolioId + " created")
            .map($ -> stored));
    }

    public Promise<Optional<Portfolio>> getPortfolio(String portfolioId) {
        ensureRunning();

        Portfolio cached = portfolioCache.get(portfolioId);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }

        return readRecord(PORTFOLIO_DATASET, portfolioId, Portfolio.class)
            .whenException(e -> Promise.of(Optional.empty()));
    }

    public Promise<List<Portfolio>> getAccountPortfolios(String accountId) {
        ensureRunning();

        return queryRecords(
            PORTFOLIO_DATASET,
            "accountId = :accountId AND status = 'ACTIVE'",
            Map.of("accountId", accountId),
            1000,
            0,
            Portfolio.class
        );
    }

    public Promise<Portfolio> updatePortfolioValue(String portfolioId, BigDecimal newValue) {
        return getPortfolio(portfolioId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Portfolio not found"));
                }

                Portfolio portfolio = opt.get();
                Portfolio updated = portfolio.withTotalValue(newValue).withUpdatedAt(Instant.now());

                portfolioCache.put(portfolioId, updated);

                return updateRecord(
                    PORTFOLIO_DATASET,
                    portfolioId,
                    updated,
                    Map.of("totalValue", newValue.toString(), "updatedAt", Instant.now().toString()),
                    "Portfolio",
                    1
                ).then(saved -> audit("PORTFOLIO_VALUE_UPDATE", saved.getAccountId(),
                    "Portfolio " + portfolioId + " value updated to " + newValue)
                    .map($ -> saved));
            });
    }

    // ==================== Inner Types ====================
    public static class Portfolio {
        private final String id;
        private final String accountId;
        private final String name;
        private final String description;
        private final String currency;
        private final BigDecimal totalValue;
        private final String status;
        private final Instant createdAt;
        private final Instant updatedAt;

        public Portfolio(String id, String accountId, String name, String description,
                        String currency, BigDecimal totalValue, String status,
                        Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.accountId = accountId;
            this.name = name;
            this.description = description;
            this.currency = currency;
            this.totalValue = totalValue;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getId() { return id; }
        public String getAccountId() { return accountId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCurrency() { return currency; }
        public BigDecimal getTotalValue() { return totalValue; }
        public String getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        public Portfolio withTotalValue(BigDecimal newValue) {
            return new Portfolio(id, accountId, name, description, currency, newValue, status, createdAt, updatedAt);
        }

        public Portfolio withUpdatedAt(Instant newUpdatedAt) {
            return new Portfolio(id, accountId, name, description, currency, totalValue, status, createdAt, newUpdatedAt);
        }
    }

    public static class PortfolioRequest {
        private final String accountId;
        private final String name;
        private final String description;
        private final String currency;

        public PortfolioRequest(String accountId, String name, String description, String currency) {
            this.accountId = accountId;
            this.name = name;
            this.description = description;
            this.currency = currency;
        }

        public String getAccountId() { return accountId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCurrency() { return currency; }
    }
}
