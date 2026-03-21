package com.ghatana.products.finance;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelCapability.CapabilityType;

import java.util.Map;

/**
 * Finance product-owned capability declarations.
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md Decision D1, product-specific
 * capabilities must be declared in the product lane rather than in the
 * canonical kernel descriptor. This class is the single source of truth for
 * finance capability constants used by finance modules and transitional
 * compatibility adapters.</p>
 *
 * @doc.type class
 * @doc.purpose Finance product-owned capability declarations
 * @doc.layer product
 * @doc.pattern ValueObject, Constants
 */
public final class FinanceCapabilities {

    private FinanceCapabilities() {}

    public static final KernelCapability TRADE_PROCESSING = new KernelCapability(
        "finance.trade-processing", "Trade Processing",
        "High-frequency trade order processing and execution",
        CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "finance",
            "latency", "microsecond",
            "throughput", "100k-tps"
        )
    );

    public static final KernelCapability RISK_MANAGEMENT = new KernelCapability(
        "finance.risk-management", "Risk Management",
        "Real-time risk assessment and position monitoring",
        CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "finance",
            "types", "market,credit,operational,liquidity",
            "calculation", "real-time"
        )
    );

    public static final KernelCapability COMPLIANCE_CHECKING = new KernelCapability(
        "finance.compliance-checking", "Compliance Checking",
        "Financial compliance monitoring and regulatory reporting",
        CapabilityType.COMPLIANCE,
        Map.of(
            "domain", "finance",
            "regulations", "securities,aml,kyc,mifid",
            "reporting", "automated"
        )
    );

    public static final KernelCapability LEDGER_MANAGEMENT = new KernelCapability(
        "finance.ledger-management", "Ledger Management",
        "Double-entry bookkeeping and ledger operations",
        CapabilityType.DATA_MANAGEMENT,
        Map.of(
            "domain", "finance",
            "type", "double-entry",
            "currencies", "multi-currency"
        )
    );

    public static final KernelCapability PORTFOLIO_MANAGEMENT = new KernelCapability(
        "finance.portfolio-management", "Portfolio Management",
        "Investment portfolio tracking and performance analysis",
        CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "finance",
            "analytics", "real-time",
            "rebalancing", "automated"
        )
    );

    public static final KernelCapability MARKET_DATA = new KernelCapability(
        "finance.market-data", "Market Data",
        "Real-time market data ingestion, normalization, and distribution",
        CapabilityType.DATA_MANAGEMENT,
        Map.of(
            "domain", "finance",
            "data_types", "quotes,trades,order-book,fundamentals",
            "real_time", "true"
        )
    );
}
