package com.ghatana.appplatform.calendar.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion;
import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion.BsDateComponents;
import com.ghatana.appplatform.calendar.conversion.CalendarRangeException;
import com.ghatana.appplatform.calendar.domain.BsDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Middleware that enriches database records with dual-calendar date fields before storage.
 *
 * <p>When a record has a primary Gregorian date field, this middleware automatically
 * computes and injects the corresponding BS date into a parallel {@code calendar_date} JSONB
 * column. Both representations are stored in a vendor-neutral JSON envelope.
 *
 * <h2>JSON envelope format</h2>
 * <pre>{@code
 * {
 *   "gregorian": "2024-04-13",
 *   "bs": {
 *     "year": 2081, "month": 1, "day": 1,
 *     "text": "2081-01-01"
 *   }
 * }
 * }</pre>
 *
 * <p>When K-15 is unavailable or the date is out of the supported BS range, the middleware
 * writes a degraded envelope with {@code "bs": null} and {@code "degraded": true} rather than
 * blocking the primary write. This matches the Sprint-1 degradation contract in STORY-K05-004.
 *
 * @doc.type class
 * @doc.purpose Enriches records with dual-calendar (BS + Gregorian) date fields (STORY-K15-005)
 * @doc.layer product
 * @doc.pattern Decorator
 */
public final class CalendarDateStorageMiddleware {

    private static final Logger log = LoggerFactory.getLogger(CalendarDateStorageMiddleware.class);

    private final ObjectMapper mapper;

    public CalendarDateStorageMiddleware() {
        this.mapper = new ObjectMapper();
    }

    /**
     * Enriches a record map by computing and injecting the {@code calendar_date} JSON field.
     *
     * <p>The input map is not modified — a new map is returned with the enriched field added.
     * The original map may be either mutable or immutable.
     *
     * @param record       the record being persisted; must contain the {@code gregorianDateField} key
     * @param gregorianField name of the field holding the canonical Gregorian {@link LocalDate}
     * @param outputField  name of the output JSONB column (e.g. {@code "calendar_date"})
     * @return a new map with the {@code outputField} JSONB string injected
     */
    public Map<String, Object> enrich(Map<String, Object> record,
                                      String gregorianField,
                                      String outputField) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(gregorianField, "gregorianField");
        Objects.requireNonNull(outputField, "outputField");

        Object raw = record.get(gregorianField);
        if (raw == null) {
            log.debug("[CalendarDateStorageMiddleware] gregorianField='{}' not found; skipping enrichment",
                gregorianField);
            return record;
        }

        LocalDate gregorian = toLocalDate(raw);
        if (gregorian == null) {
            log.warn("[CalendarDateStorageMiddleware] Cannot convert '{}' to LocalDate; skipping enrichment", raw);
            return record;
        }

        String calendarDateJson = buildCalendarDateJson(gregorian);

        java.util.Map<String, Object> enriched = new java.util.LinkedHashMap<>(record);
        enriched.put(outputField, calendarDateJson);
        return enriched;
    }

    /**
     * Computes the calendar date JSON string for a given Gregorian date.
     *
     * <p>Returns a degraded envelope when BS conversion fails (out of range, etc.)
     * so that primary writes are never blocked.
     *
     * @param gregorian Gregorian date to convert
     * @return JSON string suitable for storage in a JSONB column
     */
    public String buildCalendarDateJson(LocalDate gregorian) {
        Objects.requireNonNull(gregorian, "gregorian");
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("gregorian", gregorian.toString());

        try {
            BsDateComponents bs = BsCalendarConversion.gregorianToBs(gregorian);
            BsDate bsDate = BsDate.of(bs.year(), bs.month(), bs.day());
            ObjectNode bsNode = mapper.createObjectNode();
            bsNode.put("year",  bsDate.year());
            bsNode.put("month", bsDate.month());
            bsNode.put("day",   bsDate.day());
            bsNode.put("text",  bsDate.toString());
            envelope.set("bs", bsNode);
        } catch (CalendarRangeException e) {
            log.debug("[CalendarDateStorageMiddleware] Date {} out of BS range; using degraded envelope", gregorian);
            envelope.putNull("bs");
            envelope.put("degraded", true);
        }

        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("[CalendarDateStorageMiddleware] Serialization failed for {}", gregorian, e);
            return "{\"gregorian\":\"" + gregorian + "\",\"bs\":null,\"degraded\":true}";
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date sd) return sd.toLocalDate();
        if (value instanceof String s) {
            try {
                return LocalDate.parse(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
