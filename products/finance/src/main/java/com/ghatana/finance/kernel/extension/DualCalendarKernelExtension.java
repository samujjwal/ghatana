package com.ghatana.finance.kernel.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Extends kernel with Nepali Bikram Sambat + Gregorian dual-calendar support.
 *
 * <p>Provides date conversion and calculation services for financial operations
 * in Nepal, supporting both the official Nepali calendar (Bikram Sambat) and
 * the international Gregorian calendar for cross-border transactions.</p>
 *
 * @doc.type class
 * @doc.purpose Dual-calendar (Gregorian + Nepali BS) extension for financial date calculations
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class DualCalendarKernelExtension implements KernelExtension {

    private static final String EXTENSION_ID = "dual-calendar";
    private static final String VERSION = "1.0.0";
    private static final String NAME = "Dual Calendar Extension";

    private volatile KernelContext context;
    private volatile CalendarRegistry calendarRegistry;

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId(EXTENSION_ID)
            .withName(NAME)
            .withVersion(VERSION)
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .withCapability(KernelCapability.Core.CONFIG_MANAGEMENT)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            new KernelCapability(
                "calendar.dual",
                "Dual Calendar Support",
                "Gregorian and Nepali Bikram Sambat calendar support",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of(
                    "calendars", "gregorian,bikram-sambat",
                    "primary", "bikram-sambat",
                    "conversion", "bidirectional",
                    "fiscal_year_start", "shrawan-1"
                )
            )
        );
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        this.context = context;
        this.calendarRegistry = new CalendarRegistry();

        // Register Gregorian calendar
        calendarRegistry.register(new GregorianCalendar());

        // Register Nepali Bikram Sambat calendar
        calendarRegistry.register(new NepaliCalendar());
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        // Initialize calendar conversion cache
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        // Cleanup calendar services
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        // Compatible with any module
        return true;
    }

    @Override
    public int getPriority() {
        return 50; // Medium priority
    }

    /**
     * Gets the calendar registry.
     *
     * @return the calendar registry
     */
    public CalendarRegistry getCalendarRegistry() {
        return calendarRegistry;
    }

    // ==================== Inner Classes ====================

    /**
     * Calendar registry for managing calendar systems.
     */
    public static class CalendarRegistry {
        private final Map<String, CalendarSystem> calendars = new java.util.concurrent.ConcurrentHashMap<>();

        public void register(CalendarSystem calendar) {
            calendars.put(calendar.getCalendarId(), calendar);
        }

        public CalendarSystem getCalendar(String calendarId) {
            return calendars.get(calendarId);
        }

        public Set<String> getAvailableCalendars() {
            return Set.copyOf(calendars.keySet());
        }
    }

    /**
     * Interface for calendar systems.
     */
    public interface CalendarSystem {
        String getCalendarId();
        String getName();
        LocalDate today();
        boolean isValidDate(int year, int month, int day);
        int daysInMonth(int year, int month);
        int daysInYear(int year);
    }

    /**
     * Gregorian calendar implementation.
     */
    public static class GregorianCalendar implements CalendarSystem {
        @Override
        public String getCalendarId() { return "gregorian"; }

        @Override
        public String getName() { return "Gregorian Calendar"; }

        @Override
        public LocalDate today() { return LocalDate.now(); }

        @Override
        public boolean isValidDate(int year, int month, int day) {
            try {
                LocalDate.of(year, month, day);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public int daysInMonth(int year, int month) {
            return java.time.YearMonth.of(year, month).lengthOfMonth();
        }

        @Override
        public int daysInYear(int year) {
            return java.time.Year.of(year).isLeap() ? 366 : 365;
        }
    }

    /**
     * Nepali Bikram Sambat calendar implementation.
     */
    public static class NepaliCalendar implements CalendarSystem {
        // Nepali months and their typical days
        private static final int[] BS_MONTH_DAYS = {
            31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 // Shrawan to Ashad
        };

        @Override
        public String getCalendarId() { return "bikram-sambat"; }

        @Override
        public String getName() { return "Bikram Sambat (Nepali Calendar)"; }

        @Override
        public LocalDate today() {
            // In production, this would convert current Nepali date to Gregorian
            return LocalDate.now();
        }

        @Override
        public boolean isValidDate(int year, int month, int day) {
            if (month < 1 || month > 12) return false;
            if (day < 1 || day > daysInMonth(year, month)) return false;
            return true;
        }

        @Override
        public int daysInMonth(int year, int month) {
            // Simplified - actual days vary by year
            return BS_MONTH_DAYS[month - 1];
        }

        @Override
        public int daysInYear(int year) {
            // Bikram Sambat years typically have 365 days
            // Leap years have 366 but calculation is complex
            return 365;
        }

        /**
         * Converts Bikram Sambat date to Gregorian.
         */
        public LocalDate toGregorian(int bsYear, int bsMonth, int bsDay) {
            // Simplified conversion - production would use accurate algorithm
            int gregorianYear = bsYear - 57; // Approximate offset
            int gregorianMonth = bsMonth;
            int gregorianDay = bsDay;
            return LocalDate.of(gregorianYear, gregorianMonth, gregorianDay);
        }

        /**
         * Converts Gregorian date to Bikram Sambat.
         */
        public int[] fromGregorian(LocalDate gregorianDate) {
            // Simplified conversion - production would use accurate algorithm
            int bsYear = gregorianDate.getYear() + 57; // Approximate offset
            int bsMonth = gregorianDate.getMonthValue();
            int bsDay = gregorianDate.getDayOfMonth();
            return new int[]{bsYear, bsMonth, bsDay};
        }
    }

    /**
     * Dual calendar calculator for conversions.
     */
    public static class DualCalendarCalculator {
        private final NepaliCalendar nepaliCalendar = new NepaliCalendar();
        private final GregorianCalendar gregorianCalendar = new GregorianCalendar();

        /**
         * Converts between calendar systems.
         */
        public LocalDate convert(LocalDate date, String fromCalendar, String toCalendar) {
            if (fromCalendar.equals(toCalendar)) {
                return date;
            }

            if (fromCalendar.equals("bikram-sambat") && toCalendar.equals("gregorian")) {
                int[] bs = {date.getYear(), date.getMonthValue(), date.getDayOfMonth()};
                return nepaliCalendar.toGregorian(bs[0], bs[1], bs[2]);
            }

            if (fromCalendar.equals("gregorian") && toCalendar.equals("bikram-sambat")) {
                int[] bs = nepaliCalendar.fromGregorian(date);
                // Return as LocalDate (in practice would use a specific BS date type)
                return LocalDate.of(bs[0], bs[1], bs[2]);
            }

            throw new IllegalArgumentException("Unsupported calendar conversion: " + fromCalendar + " to " + toCalendar);
        }

        /**
         * Gets current date in specified calendar.
         */
        public LocalDate now(String calendarId) {
            if (calendarId.equals("gregorian")) {
                return gregorianCalendar.today();
            }
            if (calendarId.equals("bikram-sambat")) {
                return nepaliCalendar.today();
            }
            throw new IllegalArgumentException("Unknown calendar: " + calendarId);
        }

        /**
         * Gets fiscal year for given date in Nepal.
         * Nepal fiscal year: Shrawan (July-Aug) to Ashad (June-July)
         */
        public String getNepalFiscalYear(LocalDate date) {
            int[] bs = nepaliCalendar.fromGregorian(date);
            int bsYear = bs[0];
            int bsMonth = bs[1]; // 1 = Shrawan, 12 = Ashad

            // Fiscal year is named by the ending year
            if (bsMonth >= 1 && bsMonth <= 9) { // Shrawan to Chaitra
                return bsYear + "/" + (bsYear + 1);
            } else { // Baishakh to Ashad
                return (bsYear - 1) + "/" + bsYear;
            }
        }
    }
}
