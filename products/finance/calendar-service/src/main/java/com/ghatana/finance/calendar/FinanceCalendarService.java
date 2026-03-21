/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.calendar;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * Finance Calendar Service.
 *
 * <p>Finance-specific calendar service with regulatory compliance and financial market operations.
 * Provides business day calculations, settlement date computations, and fiscal year management
 * for financial operations according to various regulatory calendars.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>T+n settlement date calculation with regulatory calendars</li>
 *   <li>Multi-jurisdiction business day calculations</li>
 *   <li>Fiscal year management with dual calendar support</li>
 *   <li>Financial market holiday management</li>
 *   <li>Regulatory reporting date calculations</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance calendar - settlement dates, business days, fiscal years, regulatory compliance
 * @doc.layer finance
 * @doc.pattern Service, Calendar
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceCalendarService {

    // ── Finance-Specific Inner Ports ───────────────────────────────────────────────

    /** Financial holiday calendar management with regulatory compliance. */
    public interface FinanceHolidayCalendarPort {
        Promise<Boolean> isHoliday(LocalDate date, String jurisdiction);
        Promise<Boolean> isBusinessDay(LocalDate date, String jurisdiction);
        Promise<LocalDate> nextBusinessDay(LocalDate date, String jurisdiction);
        Promise<LocalDate> previousBusinessDay(LocalDate date, String jurisdiction);
        Promise<List<LocalDate>> getHolidaysInMonth(YearMonth month, String jurisdiction);
    }

    /** Fiscal year calculation with financial regulatory requirements. */
    public interface FinanceFiscalYearPort {
        Promise<FinanceFiscalYear> getCurrentFiscalYear(String jurisdiction);
        Promise<FinanceFiscalYear> getFiscalYear(int year, String jurisdiction);
        Promise<List<FinanceFiscalQuarter>> getFiscalQuarters(int year, String jurisdiction);
    }

    /** Settlement date calculation with market-specific rules. */
    public interface FinanceSettlementPort {
        Promise<LocalDate> calculateSettlementDate(LocalDate tradeDate, int tPlusDays, String market);
        Promise<LocalDate> calculateCorporateActionDate(LocalDate announcementDate, int days, String market);
        Promise<LocalDate> calculateDividendPaymentDate(LocalDate exDividendDate, String market);
    }

    /** Regulatory reporting date calculations. */
    public interface FinanceReportingPort {
        Promise<LocalDate> getReportingDeadline(LocalDate periodEnd, String reportType, String jurisdiction);
        Promise<List<LocalDate>> getReportingSchedule(int year, String jurisdiction);
        Promise<LocalDate> adjustForWeekend(LocalDate date, String jurisdiction);
    }

    // ── Finance-Specific Value Types ─────────────────────────────────────────────

    public enum FinanceMarket {
        NYSE, NASDAQ, LSE, EURONEXT, HKEX, SGX, ASX, TSE, BSE, NSE
    }

    public enum FinanceJurisdiction {
        US, UK, EU, SINGAPORE, HONG_KONG, AUSTRALIA, JAPAN, INDIA, NEPAL
    }

    public enum FinanceReportType {
        QUARTERLY, ANNUAL, FORM_10K, FORM_10Q, PROSPECTUS, PROXY, REGULATION_A
    }

    public record FinanceFiscalYear(
        int year,
        LocalDate startDate,
        LocalDate endDate,
        FinanceJurisdiction jurisdiction,
        List<FinanceFiscalQuarter> quarters
    ) {}

    public record FinanceFiscalQuarter(
        int quarter,
        LocalDate startDate,
        LocalDate endDate,
        String label,
        int businessDays
    ) {}

    public record FinanceSettlementResult(
        LocalDate tradeDate,
        LocalDate settlementDate,
        int tPlusDays,
        FinanceMarket market,
        boolean adjustedForHoliday,
        String regulatoryReference
    ) {}

    public record FinanceBusinessDayResult(
        LocalDate date,
        boolean isBusinessDay,
        FinanceJurisdiction jurisdiction,
        String holidayReason,
        List<FinanceMarket> affectedMarkets
    ) {}

    public record FinanceReportingSchedule(
        int year,
        FinanceJurisdiction jurisdiction,
        Map<FinanceReportType, List<LocalDate>> reportingDeadlines
    ) {}

    public record FinanceCalendarEvent(
        String eventId,
        String eventType,
        LocalDate eventDate,
        String description,
        FinanceMarket market,
        FinanceJurisdiction jurisdiction,
        boolean affectsSettlement,
        Instant createdAt
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final FinanceHolidayCalendarPort holidayCalendar;
    private final FinanceFiscalYearPort fiscalYear;
    private final FinanceSettlementPort settlement;
    private final FinanceReportingPort reporting;
    private final Executor executor;
    
    private final Counter settlementCalculatedCounter;
    private final Counter businessDayCheckedCounter;
    private final Counter fiscalYearQueriedCounter;
    private final Counter reportingDateCalculatedCounter;

    // ── Constructor ─────────────────────────────────────────────────────────────

    public FinanceCalendarService(
        FinanceHolidayCalendarPort holidayCalendar,
        FinanceFiscalYearPort fiscalYear,
        FinanceSettlementPort settlement,
        FinanceReportingPort reporting,
        MeterRegistry registry,
        Executor executor
    ) {
        this.holidayCalendar = holidayCalendar;
        this.fiscalYear = fiscalYear;
        this.settlement = settlement;
        this.reporting = reporting;
        this.executor = executor;
        
        this.settlementCalculatedCounter = Counter.builder("finance.calendar.settlement.calculated_total").register(registry);
        this.businessDayCheckedCounter = Counter.builder("finance.calendar.business_day.checked_total").register(registry);
        this.fiscalYearQueriedCounter = Counter.builder("finance.calendar.fiscal_year.queried_total").register(registry);
        this.reportingDateCalculatedCounter = Counter.builder("finance.calendar.reporting_date.calculated_total").register(registry);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Calculate T+n settlement date with regulatory compliance.
     */
    public Promise<FinanceSettlementResult> calculateSettlementDate(
            LocalDate tradeDate, int tPlusDays, FinanceMarket market) {
        
        return Promise.ofBlocking(executor, () -> {
            String jurisdiction = getJurisdictionForMarket(market);
            
            return settlement.calculateSettlementDate(tradeDate, tPlusDays, market.name())
                .then(settlementDate -> {
                    boolean adjustedForHoliday = !tradeDate.plusDays(tPlusDays).equals(settlementDate);
                    
                    settlementCalculatedCounter.increment();
                    
                    return Promise.of(new FinanceSettlementResult(
                        tradeDate, settlementDate, tPlusDays, market,
                        adjustedForHoliday, "SEC-RULE-15c3-1"
                    ));
                });
        }).getResult();
    }

    /**
     * Check if a date is a business day for financial operations.
     */
    public Promise<FinanceBusinessDayResult> checkBusinessDay(LocalDate date, FinanceJurisdiction jurisdiction) {
        return Promise.ofBlocking(executor, () -> {
            return holidayCalendar.isBusinessDay(date, jurisdiction.name())
                .then(isBusinessDay -> {
                    businessDayCheckedCounter.increment();
                    
                    if (isBusinessDay) {
                        return Promise.of(new FinanceBusinessDayResult(
                            date, true, jurisdiction, null, List.of()
                        ));
                    } else {
                        // Check if it's a holiday and get affected markets
                        return holidayCalendar.isHoliday(date, jurisdiction.name())
                            .then(isHoliday -> {
                                if (isHoliday) {
                                    return Promise.of(new FinanceBusinessDayResult(
                                        date, false, jurisdiction, "Public holiday", 
                                        getMarketsForJurisdiction(jurisdiction)
                                    ));
                                } else {
                                    return Promise.of(new FinanceBusinessDayResult(
                                        date, false, jurisdiction, "Weekend", 
                                        getMarketsForJurisdiction(jurisdiction)
                                    ));
                                }
                            });
                    }
                });
        }).getResult();
    }

    /**
     * Get current fiscal year with financial regulatory compliance.
     */
    public Promise<FinanceFiscalYear> getCurrentFiscalYear(FinanceJurisdiction jurisdiction) {
        return fiscalYear.getCurrentFiscalYear(jurisdiction.name())
            .then(fiscalYear -> {
                fiscalYearQueriedCounter.increment();
                return Promise.of(fiscalYear);
            });
    }

    /**
     * Calculate quarterly reporting deadlines.
     */
    public Promise<FinanceReportingSchedule> getReportingSchedule(int year, FinanceJurisdiction jurisdiction) {
        return Promise.ofBlocking(executor, () -> {
            Map<FinanceReportType, List<LocalDate>> deadlines = new EnumMap<>(FinanceReportType.class);
            
            // Calculate deadlines for different report types
            for (FinanceReportType reportType : FinanceReportType.values()) {
                List<LocalDate> reportDeadlines = new ArrayList<>();
                
                for (int quarter = 1; quarter <= 4; quarter++) {
                    LocalDate quarterEnd = getQuarterEndDate(year, quarter, jurisdiction);
                    LocalDate deadline = reporting.getReportingDeadline(quarterEnd, reportType.name(), jurisdiction.name()).getResult();
                    reportDeadlines.add(deadline);
                }
                
                deadlines.put(reportType, reportDeadlines);
            }
            
            reportingDateCalculatedCounter.increment();
            
            return Promise.of(new FinanceReportingSchedule(year, jurisdiction, deadlines));
        }).getResult();
    }

    /**
     * Calculate corporate action dates with regulatory compliance.
     */
    public Promise<LocalDate> calculateCorporateActionDate(
            LocalDate announcementDate, int days, FinanceMarket market) {
        
        return settlement.calculateCorporateActionDate(announcementDate, days, market.name());
    }

    /**
     * Get business days between two dates for a specific jurisdiction.
     */
    public Promise<Integer> getBusinessDaysBetween(
            LocalDate startDate, LocalDate endDate, FinanceJurisdiction jurisdiction) {
        
        return Promise.ofBlocking(executor, () -> {
            int businessDays = 0;
            LocalDate current = startDate;
            
            while (!current.isAfter(endDate)) {
                boolean isBusinessDay = holidayCalendar.isBusinessDay(current, jurisdiction.name()).getResult();
                if (isBusinessDay) {
                    businessDays++;
                }
                current = current.plusDays(1);
            }
            
            return Promise.of(businessDays);
        }).getResult();
    }

    /**
     * Add business days to a date with regulatory compliance.
     */
    public Promise<LocalDate> addBusinessDays(
            LocalDate date, int businessDays, FinanceJurisdiction jurisdiction) {
        
        return Promise.ofBlocking(executor, () -> {
            if (businessDays == 0) {
                return holidayCalendar.isBusinessDay(date, jurisdiction.name())
                    .then(isBusinessDay -> isBusinessDay ? Promise.of(date) : 
                        holidayCalendar.nextBusinessDay(date, jurisdiction.name()));
            }
            
            if (businessDays > 0) {
                return addPositiveBusinessDays(date, businessDays, jurisdiction);
            } else {
                return addNegativeBusinessDays(date, -businessDays, jurisdiction);
            }
        }).getResult();
    }

    /**
     * Get market holidays for a specific month and jurisdiction.
     */
    public Promise<List<FinanceCalendarEvent>> getMarketHolidays(
            YearMonth month, FinanceJurisdiction jurisdiction) {
        
        return Promise.ofBlocking(executor, () -> {
            List<LocalDate> holidays = holidayCalendar.getHolidaysInMonth(month, jurisdiction.name()).getResult();
            List<FinanceCalendarEvent> events = new ArrayList<>();
            
            for (LocalDate holiday : holidays) {
                events.add(new FinanceCalendarEvent(
                    UUID.randomUUID().toString(),
                    "MARKET_HOLIDAY",
                    holiday,
                    "Market holiday for " + jurisdiction.name(),
                    getPrimaryMarketForJurisdiction(jurisdiction),
                    jurisdiction,
                    true,
                    Instant.now()
                ));
            }
            
            return Promise.of(events);
        }).getResult();
    }

    // ── Private Helper Methods ───────────────────────────────────────────────────

    private Promise<LocalDate> addPositiveBusinessDays(
            LocalDate date, int businessDays, FinanceJurisdiction jurisdiction) {
        
        return Promise.ofBlocking(executor, () -> {
            LocalDate current = date;
            int daysAdded = 0;
            
            while (daysAdded < businessDays) {
                current = current.plusDays(1);
                boolean isBusinessDay = holidayCalendar.isBusinessDay(current, jurisdiction.name()).getResult();
                if (isBusinessDay) {
                    daysAdded++;
                }
            }
            
            return Promise.of(current);
        }).getResult();
    }

    private Promise<LocalDate> addNegativeBusinessDays(
            LocalDate date, int businessDays, FinanceJurisdiction jurisdiction) {
        
        return Promise.ofBlocking(executor, () -> {
            LocalDate current = date;
            int daysSubtracted = 0;
            
            while (daysSubtracted < businessDays) {
                current = current.minusDays(1);
                boolean isBusinessDay = holidayCalendar.isBusinessDay(current, jurisdiction.name()).getResult();
                if (isBusinessDay) {
                    daysSubtracted++;
                }
            }
            
            return Promise.of(current);
        }).getResult();
    }

    private LocalDate getQuarterEndDate(int year, int quarter, FinanceJurisdiction jurisdiction) {
        return switch (quarter) {
            case 1 -> LocalDate.of(year, 3, 31);
            case 2 -> LocalDate.of(year, 6, 30);
            case 3 -> LocalDate.of(year, 9, 30);
            case 4 -> LocalDate.of(year, 12, 31);
            default -> throw new IllegalArgumentException("Invalid quarter: " + quarter);
        };
    }

    private String getJurisdictionForMarket(FinanceMarket market) {
        return switch (market) {
            case NYSE, NASDAQ -> "US";
            case LSE -> "UK";
            case EURONEXT -> "EU";
            case HKEX -> "HONG_KONG";
            case SGX -> "SINGAPORE";
            case ASX -> "AUSTRALIA";
            case TSE -> "JAPAN";
            case BSE, NSE -> "INDIA";
        };
    }

    private static FinanceJurisdiction getJurisdictionEnum(String jurisdiction) {
        return switch (jurisdiction) {
            case "US" -> FinanceJurisdiction.US;
            case "UK" -> FinanceJurisdiction.UK;
            case "EU" -> FinanceJurisdiction.EU;
            case "SINGAPORE" -> FinanceJurisdiction.SINGAPORE;
            case "HONG_KONG" -> FinanceJurisdiction.HONG_KONG;
            case "AUSTRALIA" -> FinanceJurisdiction.AUSTRALIA;
            case "JAPAN" -> FinanceJurisdiction.JAPAN;
            case "INDIA" -> FinanceJurisdiction.INDIA;
            case "NEPAL" -> FinanceJurisdiction.NEPAL;
            default -> throw new IllegalArgumentException("Unknown jurisdiction: " + jurisdiction);
        };
    }

    private List<FinanceMarket> getMarketsForJurisdiction(FinanceJurisdiction jurisdiction) {
        return switch (jurisdiction) {
            case US -> List.of(FinanceMarket.NYSE, FinanceMarket.NASDAQ);
            case UK -> List.of(FinanceMarket.LSE);
            case EU -> List.of(FinanceMarket.EURONEXT);
            case SINGAPORE -> List.of(FinanceMarket.SGX);
            case HONG_KONG -> List.of(FinanceMarket.HKEX);
            case AUSTRALIA -> List.of(FinanceMarket.ASX);
            case JAPAN -> List.of(FinanceMarket.TSE);
            case INDIA -> List.of(FinanceMarket.BSE, FinanceMarket.NSE);
            case NEPAL -> List.of(); // Nepal doesn't have major financial markets
        };
    }

    private FinanceMarket getPrimaryMarketForJurisdiction(FinanceJurisdiction jurisdiction) {
        List<FinanceMarket> markets = getMarketsForJurisdiction(jurisdiction);
        return markets.isEmpty() ? FinanceMarket.NYSE : markets.get(0);
    }

    // ── Default Implementation (for testing) ─────────────────────────────────────

    public static FinanceCalendarService createDefault(MeterRegistry registry, Executor executor) {
        return new FinanceCalendarService(
            new DefaultFinanceHolidayCalendarPort(),
            new DefaultFinanceFiscalYearPort(),
            new DefaultFinanceSettlementPort(),
            new DefaultFinanceReportingPort(),
            registry,
            executor
        );
    }

    // Default implementations for testing/development
    private static final class DefaultFinanceHolidayCalendarPort implements FinanceHolidayCalendarPort {
        @Override
        public Promise<Boolean> isHoliday(LocalDate date, String jurisdiction) {
            return Promise.of(date.getDayOfWeek().getValue() >= 6); // Weekend check
        }

        @Override
        public Promise<Boolean> isBusinessDay(LocalDate date, String jurisdiction) {
            return Promise.of(date.getDayOfWeek().getValue() < 5); // Mon-Thu
        }

        @Override
        public Promise<LocalDate> nextBusinessDay(LocalDate date, String jurisdiction) {
            LocalDate next = date.plusDays(1);
            while (next.getDayOfWeek().getValue() >= 5) {
                next = next.plusDays(1);
            }
            return Promise.of(next);
        }

        @Override
        public Promise<LocalDate> previousBusinessDay(LocalDate date, String jurisdiction) {
            LocalDate prev = date.minusDays(1);
            while (prev.getDayOfWeek().getValue() >= 5) {
                prev = prev.minusDays(1);
            }
            return Promise.of(prev);
        }

        @Override
        public Promise<List<LocalDate>> getHolidaysInMonth(YearMonth month, String jurisdiction) {
            return Promise.of(List.of());
        }
    }

    private static final class DefaultFinanceFiscalYearPort implements FinanceFiscalYearPort {
        @Override
        public Promise<FinanceFiscalYear> getCurrentFiscalYear(String jurisdiction) {
            int year = LocalDate.now().getYear();
            return getFiscalYear(year, jurisdiction);
        }

        @Override
        public Promise<FinanceFiscalYear> getFiscalYear(int year, String jurisdiction) {
            return Promise.of(new FinanceFiscalYear(
                year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
                getJurisdictionEnum(jurisdiction), List.of()
            ));
        }

        @Override
        public Promise<List<FinanceFiscalQuarter>> getFiscalQuarters(int year, String jurisdiction) {
            return Promise.of(List.of());
        }
    }

    private static final class DefaultFinanceSettlementPort implements FinanceSettlementPort {
        @Override
        public Promise<LocalDate> calculateSettlementDate(LocalDate tradeDate, int tPlusDays, String market) {
            LocalDate settlement = tradeDate;
            int daysAdded = 0;
            
            while (daysAdded < tPlusDays) {
                settlement = settlement.plusDays(1);
                if (settlement.getDayOfWeek().getValue() < 5) { // Business day
                    daysAdded++;
                }
            }
            
            return Promise.of(settlement);
        }

        @Override
        public Promise<LocalDate> calculateCorporateActionDate(LocalDate announcementDate, int days, String market) {
            return Promise.of(announcementDate.plusDays(days));
        }

        @Override
        public Promise<LocalDate> calculateDividendPaymentDate(LocalDate exDividendDate, String market) {
            return Promise.of(exDividendDate.plusDays(2));
        }
    }

    private static final class DefaultFinanceReportingPort implements FinanceReportingPort {
        @Override
        public Promise<LocalDate> getReportingDeadline(LocalDate periodEnd, String reportType, String jurisdiction) {
            // Typical deadline is 40 days after period end
            return Promise.of(periodEnd.plusDays(40));
        }

        @Override
        public Promise<List<LocalDate>> getReportingSchedule(int year, String jurisdiction) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<LocalDate> adjustForWeekend(LocalDate date, String jurisdiction) {
            if (date.getDayOfWeek().getValue() >= 6) { // Weekend
                return Promise.of(date.plusDays(8 - date.getDayOfWeek().getValue()));
            }
            return Promise.of(date);
        }
    }
}
