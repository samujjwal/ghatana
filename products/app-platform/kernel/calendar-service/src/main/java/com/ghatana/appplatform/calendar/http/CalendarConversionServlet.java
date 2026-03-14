package com.ghatana.appplatform.calendar.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion;
import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion.BsDateComponents;
import com.ghatana.appplatform.calendar.conversion.CalendarRangeException;
import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.CalendarConversionResult;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * ActiveJ HTTP servlet exposing BS ↔ Gregorian calendar conversion (K15-003).
 *
 * <h2>Endpoints</h2>
 * <pre>
 * GET /calendar/convert?from=greg&date=2024-04-13
 * GET /calendar/convert?from=bs&date=2081-01-01
 * GET /calendar/today
 * </pre>
 *
 * <p>All responses are JSON with both Gregorian and BS representations.
 *
 * @doc.type class
 * @doc.purpose REST API for BS ↔ Gregorian calendar conversion (STORY-K15-003)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class CalendarConversionServlet {

    private final ObjectMapper mapper;

    public CalendarConversionServlet() {
        this.mapper = new ObjectMapper();
    }

    /**
     * Builds the routing servlet. Attach to an existing {@code RoutingServlet} or HTTP server.
     */
    public RoutingServlet buildRoutes() {
        return RoutingServlet.create()
            .map(HttpMethod.GET, "/calendar/convert", this::handleConvert)
            .map(HttpMethod.GET, "/calendar/today",   this::handleToday);
    }

    // ── Handlers  ─────────────────────────────────────────────────────────────

    private Promise<HttpResponse> handleConvert(HttpRequest request) {
        String from = request.getQueryParameter("from");
        String date = request.getQueryParameter("date");

        if (from == null || date == null) {
            return badRequest("Query params 'from' and 'date' are required. " +
                "Example: /calendar/convert?from=greg&date=2024-04-13");
        }

        try {
            CalendarConversionResult result = switch (from.toLowerCase()) {
                case "greg", "gregorian" -> convertFromGregorian(date);
                case "bs"               -> convertFromBs(date);
                default -> throw new IllegalArgumentException(
                    "Unknown 'from' value: '" + from + "'. Use 'greg' or 'bs'.");
            };
            return Promise.of(jsonResponse(200, toJson(result)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return badRequest(e.getMessage());
        } catch (CalendarRangeException e) {
            return Promise.of(jsonResponse(422, errorJson(
                "DATE_OUT_OF_RANGE", e.getMessage())));
        }
    }

    private Promise<HttpResponse> handleToday(HttpRequest request) {
        LocalDate today = LocalDate.now();
        try {
            BsDateComponents bs = BsCalendarConversion.gregorianToBs(today);
            CalendarConversionResult result = new CalendarConversionResult(
                today, BsDate.of(bs.year(), bs.month(), bs.day()));
            return Promise.of(jsonResponse(200, toJson(result)));
        } catch (CalendarRangeException e) {
            return Promise.of(jsonResponse(422, errorJson("DATE_OUT_OF_RANGE", e.getMessage())));
        }
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    private static CalendarConversionResult convertFromGregorian(String isoDate) {
        LocalDate gregorian = LocalDate.parse(isoDate);  // throws DateTimeParseException if bad
        BsDateComponents bs = BsCalendarConversion.gregorianToBs(gregorian);
        return new CalendarConversionResult(gregorian, BsDate.of(bs.year(), bs.month(), bs.day()));
    }

    private static CalendarConversionResult convertFromBs(String bsDate) {
        BsDate bs = BsDate.parse(bsDate);  // throws IllegalArgumentException if bad format
        LocalDate gregorian = BsCalendarConversion.bsToGregorian(bs.year(), bs.month(), bs.day());
        return new CalendarConversionResult(gregorian, bs);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String toJson(CalendarConversionResult result) {
        ObjectNode node = mapper.createObjectNode();
        node.put("gregorian", result.gregorian().toString());
        ObjectNode bsNode = mapper.createObjectNode();
        bsNode.put("year",  result.bs().year());
        bsNode.put("month", result.bs().month());
        bsNode.put("day",   result.bs().day());
        bsNode.put("text",  result.bs().toString());
        node.set("bs", bsNode);
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private String errorJson(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + escape(message) + "\"}";
    }

    private static Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400)
            .withBody(("{\"error\":\"BAD_REQUEST\",\"message\":\"" + escape(message) + "\"}").getBytes())
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE,
                io.activej.http.HttpHeaderValue.of("application/json")));
    }

    private static HttpResponse jsonResponse(int code, String body) {
        return HttpResponse.ofCode(code)
            .withBody(body.getBytes())
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE,
                io.activej.http.HttpHeaderValue.of("application/json"));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
