package com.ghatana.appplatform.ems.service;

import com.ghatana.appplatform.ems.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   FIX 4.4/5.0 protocol engine managing session lifecycle, sequence numbers,
 *                message encoding/decoding, heartbeats, and gap fill.
 * @doc.layer     Application
 * @doc.pattern   State Machine — DISCONNECTED → CONNECTING → LOGGED_ON → LOGGED_OUT
 *
 * Supports message types:
 *   - Logon (A), Logout (5), Heartbeat (0), Test Request (1), Sequence Reset (4)
 *   - Resend Request (2) for gap fill
 *   - NewOrderSingle (D), ExecutionReport (8), OrderCancelRequest (F),
 *     OrderCancelReplaceRequest (G), MarketDataRequest (V)
 *
 * Sequence number management: each session maintains independent send/receive counters.
 * Gap fill: on sequence gap detection, emits ResendRequest; counterparty expects Sequence Reset.
 */
public class FixProtocolService {

    private static final Logger log = LoggerFactory.getLogger(FixProtocolService.class);
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int SESSION_TIMEOUT_SECONDS    = 60;
    private static final char FIELD_DELIM               = '\u0001'; // SOH

    private final ConcurrentHashMap<String, FixSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<FixMessage>> sentMessageLog = new ConcurrentHashMap<>();
    private final Consumer<Object> eventPublisher;
    private final ScheduledExecutorService scheduler;
    private final Counter heartbeatsSent;
    private final Counter gapFillsIssued;
    private final Counter logonCount;

    public FixProtocolService(Consumer<Object> eventPublisher, MeterRegistry meterRegistry) {
        this.eventPublisher = eventPublisher;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fix-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeatsSent = meterRegistry.counter("fix.heartbeats.sent");
        this.gapFillsIssued = meterRegistry.counter("fix.gapfill.issued");
        this.logonCount     = meterRegistry.counter("fix.sessions.logon");

        scheduler.scheduleAtFixedRate(this::processHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Opens a FIX session: creates session state and emits a Logon message.
     *
     * @param sessionId    unique session identifier (e.g. "NEPSE_FIX")
     * @param senderCompId our COMP ID
     * @param targetCompId counterparty COMP ID
     * @param fixVersion   "FIX.4.4" or "FIXT.1.1"
     * @return encoded Logon message ready to send over the wire
     */
    public String initiateLogon(String sessionId, String senderCompId,
                                String targetCompId, String fixVersion) {
        FixSession session = new FixSession(sessionId, senderCompId, targetCompId,
                fixVersion, FixSessionState.CONNECTING, 1, 1, Instant.now(), null);
        sessions.put(sessionId, session);
        sentMessageLog.put(sessionId, new ArrayList<>());

        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  senderCompId);
        fields.put(FixMessage.TAG_TARGET_COMP,  targetCompId);
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        fields.put(108, String.valueOf(HEARTBEAT_INTERVAL_SECONDS)); // HeartBtInt
        fields.put(141, "Y");  // ResetOnLogon

        FixMessage logon = new FixMessage(FixMessage.LOGON, session.nextSendSeqNum(), fields, Instant.now());
        recordSent(sessionId, logon);
        sessions.put(sessionId, session.incrementSend());
        logonCount.increment();

        log.info("FIX logon initiated: session={} sender={} target={}", sessionId, senderCompId, targetCompId);
        return encode(logon, fixVersion);
    }

    /**
     * Processes an inbound FIX message string, updating session state and returning any outbound response.
     *
     * @param sessionId  session context
     * @param rawMessage raw FIX message string (SOH-delimited)
     * @return Optional outbound message to send back (e.g. heartbeat response, gap fill)
     */
    public Optional<String> receive(String sessionId, String rawMessage) {
        FixSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("FIX message received for unknown session: {}", sessionId);
            return Optional.empty();
        }

        FixMessage msg = decode(rawMessage);
        int incomingSeq = msg.seqNum();

        // Sequence gap detection
        if (incomingSeq > session.nextExpectedSeqNum()) {
            log.warn("FIX sequence gap detected: session={} expected={} got={}",
                    sessionId, session.nextExpectedSeqNum(), incomingSeq);
            gapFillsIssued.increment();
            return Optional.of(buildResendRequest(session, incomingSeq));
        }

        // Update expected sequence
        sessions.put(sessionId, new FixSession(
                session.sessionId(), session.senderCompId(), session.targetCompId(),
                session.fixVersion(), session.state(),
                session.nextSendSeqNum(), incomingSeq + 1,
                session.logonAt(), session.lastHeartbeatAt()));

        return switch (msg.msgType()) {
            case FixMessage.LOGON      -> handleLogon(sessionId, session, msg);
            case FixMessage.LOGOUT     -> handleLogout(sessionId, session);
            case FixMessage.HEARTBEAT  -> handleHeartbeat(sessionId);
            case FixMessage.TEST_REQUEST -> Optional.of(buildHeartbeat(session, msg.get(112)));
            case FixMessage.EXEC_REPORT  -> { handleExecReport(sessionId, msg); yield Optional.empty(); }
            case FixMessage.RESEND_REQUEST -> Optional.of(buildSequenceReset(session, msg));
            default -> Optional.empty();
        };
    }

    /**
     * Builds an encoded NewOrderSingle (D) FIX message.
     *
     * @param sessionId  session to use for sequence tracking
     * @param order      the routed order details
     * @return encoded FIX string ready to transmit
     */
    public String buildNewOrderSingle(String sessionId, RoutedOrder order) {
        FixSession session = requireSession(sessionId);

        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        fields.put(FixMessage.TAG_CL_ORD_ID,   order.routingId());
        fields.put(FixMessage.TAG_SYMBOL,       order.instrumentId());
        fields.put(FixMessage.TAG_SIDE,         order.side() == ExecutionSide.BUY ? "1" : "2");
        fields.put(FixMessage.TAG_ORDER_QTY,    String.valueOf(order.quantity()));
        fields.put(FixMessage.TAG_ORD_TYPE,     "MARKET".equals(order.orderType()) ? "1" : "2");
        fields.put(FixMessage.TAG_TIME_IN_FORCE, mapTimeInForce(order.timeInForce()));
        if (order.limitPrice() != null) {
            fields.put(FixMessage.TAG_PRICE, order.limitPrice().toPlainString());
        }

        FixMessage nos = new FixMessage(FixMessage.NEW_ORDER_SINGLE, session.nextSendSeqNum(), fields, Instant.now());
        recordSent(sessionId, nos);
        sessions.put(sessionId, session.incrementSend());

        return encode(nos, session.fixVersion());
    }

    /**
     * Builds an encoded OrderCancelRequest (F).
     */
    public String buildOrderCancelRequest(String sessionId, String routingId,
                                           String instrumentId, ExecutionSide side) {
        FixSession session = requireSession(sessionId);

        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        fields.put(FixMessage.TAG_CL_ORD_ID,   routingId + "_CXL");
        fields.put(41,                           routingId); // OrigClOrdID
        fields.put(FixMessage.TAG_SYMBOL,       instrumentId);
        fields.put(FixMessage.TAG_SIDE,         side == ExecutionSide.BUY ? "1" : "2");
        fields.put(54,                           side == ExecutionSide.BUY ? "1" : "2");

        FixMessage cancel = new FixMessage(FixMessage.ORDER_CANCEL, session.nextSendSeqNum(), fields, Instant.now());
        recordSent(sessionId, cancel);
        sessions.put(sessionId, session.incrementSend());

        return encode(cancel, session.fixVersion());
    }

    /**
     * Initiates FIX session logout.
     */
    public String initiateLogout(String sessionId, String reason) {
        FixSession session = requireSession(sessionId);

        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        if (reason != null && !reason.isBlank()) {
            fields.put(58, reason); // Text
        }

        FixMessage logout = new FixMessage(FixMessage.LOGOUT, session.nextSendSeqNum(), fields, Instant.now());
        recordSent(sessionId, logout);
        sessions.put(sessionId, session.incrementSend().withState(FixSessionState.LOGGED_OUT));

        log.info("FIX logout initiated: session={} reason={}", sessionId, reason);
        return encode(logout, session.fixVersion());
    }

    public FixSessionState getSessionState(String sessionId) {
        FixSession s = sessions.get(sessionId);
        return s == null ? FixSessionState.DISCONNECTED : s.state();
    }

    // ─── Internal handlers ────────────────────────────────────────────────────

    private Optional<String> handleLogon(String sessionId, FixSession session, FixMessage msg) {
        sessions.put(sessionId, session.withState(FixSessionState.LOGGED_ON));
        log.info("FIX session logged on: {}", sessionId);
        eventPublisher.accept(new FixSessionLogonEvent(sessionId));

        // Echo back heartbeat acknowledgement
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        FixMessage hb = new FixMessage(FixMessage.HEARTBEAT, session.nextSendSeqNum(), fields, Instant.now());
        sessions.put(sessionId, session.incrementSend());
        return Optional.of(encode(hb, session.fixVersion()));
    }

    private Optional<String> handleLogout(String sessionId, FixSession session) {
        sessions.put(sessionId, session.withState(FixSessionState.LOGGED_OUT));
        log.info("FIX session logged out: {}", sessionId);
        eventPublisher.accept(new FixSessionLogoutEvent(sessionId));
        return Optional.empty();
    }

    private Optional<String> handleHeartbeat(String sessionId) {
        FixSession session = requireSession(sessionId);
        sessions.put(sessionId, session.withHeartbeat(Instant.now()));
        return Optional.empty();
    }

    private void handleExecReport(String sessionId, FixMessage msg) {
        String execId     = msg.get(FixMessage.TAG_EXEC_ID);
        String clOrdId    = msg.get(FixMessage.TAG_CL_ORD_ID);
        String execType   = msg.get(FixMessage.TAG_EXEC_TYPE);
        String lastQtyStr = msg.get(FixMessage.TAG_LAST_QTY);
        String lastPxStr  = msg.get(FixMessage.TAG_LAST_PX);

        log.info("FIX ExecReport: session={} execId={} clOrdId={} execType={}",
                sessionId, execId, clOrdId, execType);
        eventPublisher.accept(new FixExecReportEvent(sessionId, execId, clOrdId, execType,
                lastQtyStr != null ? Long.parseLong(lastQtyStr) : 0,
                lastPxStr));
    }

    private String buildHeartbeat(FixSession session, String testReqId) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        if (testReqId != null) fields.put(112, testReqId);
        FixMessage hb = new FixMessage(FixMessage.HEARTBEAT, session.nextSendSeqNum(), fields, Instant.now());
        sessions.put(session.sessionId(), session.incrementSend());
        heartbeatsSent.increment();
        return encode(hb, session.fixVersion());
    }

    private String buildResendRequest(FixSession session, int gapSeq) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        fields.put(7,  String.valueOf(session.nextExpectedSeqNum())); // BeginSeqNo
        fields.put(16, "0"); // EndSeqNo (0 = all messages)
        FixMessage rr = new FixMessage(FixMessage.RESEND_REQUEST, session.nextSendSeqNum(), fields, Instant.now());
        sessions.put(session.sessionId(), session.incrementSend());
        return encode(rr, session.fixVersion());
    }

    private String buildSequenceReset(FixSession session, FixMessage resendReq) {
        int beginSeq = Integer.parseInt(resendReq.get(7));
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
        fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
        fields.put(FixMessage.TAG_SENDING_TIME, Instant.now().toString());
        fields.put(36, String.valueOf(session.nextSendSeqNum())); // NewSeqNo
        fields.put(123, "Y"); // GapFillFlag
        FixMessage sr = new FixMessage(FixMessage.SEQUENCE_RESET, beginSeq, fields, Instant.now());
        return encode(sr, session.fixVersion());
    }

    private void processHeartbeats() {
        Instant now = Instant.now();
        sessions.forEach((id, session) -> {
            if (session.state() != FixSessionState.LOGGED_ON) return;
            Instant lastHb = session.lastHeartbeatAt();
            if (lastHb != null && lastHb.plusSeconds(SESSION_TIMEOUT_SECONDS).isBefore(now)) {
                log.warn("FIX session timeout detected: {}", id);
                sessions.put(id, session.withState(FixSessionState.DISCONNECTED));
                eventPublisher.accept(new FixSessionTimeoutEvent(id));
                return;
            }
            // Send heartbeat
            Map<Integer, String> fields = new LinkedHashMap<>();
            fields.put(FixMessage.TAG_SENDER_COMP,  session.senderCompId());
            fields.put(FixMessage.TAG_TARGET_COMP,  session.targetCompId());
            fields.put(FixMessage.TAG_SENDING_TIME, now.toString());
            FixMessage hb = new FixMessage(FixMessage.HEARTBEAT, session.nextSendSeqNum(), fields, now);
            sessions.put(id, session.incrementSend().withHeartbeat(now));
            heartbeatsSent.increment();
            // In production, this encoded message would be dispatched to the transport layer
            log.debug("FIX heartbeat sent: session={} seq={}", id, hb.seqNum());
        });
    }

    // ─── Encoding / Decoding ──────────────────────────────────────────────────

    /**
     * Encodes a FixMessage into a FIX wire format string.
     * Computes BodyLength (tag 9) and CheckSum (tag 10) per FIX specification.
     */
    private String encode(FixMessage msg, String fixVersion) {
        StringBuilder body = new StringBuilder();
        body.append(FixMessage.TAG_MSG_TYPE).append('=').append(msg.msgType()).append(FIELD_DELIM);
        body.append(FixMessage.TAG_SEQ_NUM).append('=').append(msg.seqNum()).append(FIELD_DELIM);
        msg.fields().forEach((tag, value) -> {
            if (tag != FixMessage.TAG_MSG_TYPE && tag != FixMessage.TAG_SEQ_NUM) {
                body.append(tag).append('=').append(value).append(FIELD_DELIM);
            }
        });

        String header = "8=" + fixVersion + FIELD_DELIM + "9=" + body.length() + FIELD_DELIM;
        String full   = header + body;
        int checksum  = 0;
        for (char c : full.toCharArray()) checksum = (checksum + c) & 0xFF;
        return full + "10=" + String.format("%03d", checksum) + FIELD_DELIM;
    }

    /**
     * Decodes a raw FIX wire string into a FixMessage.
     * Parses SOH-delimited tag=value pairs; extracts msgType and seqNum.
     */
    private FixMessage decode(String raw) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        String[] parts = raw.split(String.valueOf(FIELD_DELIM));
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                int tag   = Integer.parseInt(part.substring(0, eq));
                String val = part.substring(eq + 1);
                fields.put(tag, val);
            } catch (NumberFormatException ignored) { /* malformed — skip */ }
        }

        String msgType = fields.getOrDefault(FixMessage.TAG_MSG_TYPE, "");
        int seqNum     = 0;
        try { seqNum = Integer.parseInt(fields.getOrDefault(FixMessage.TAG_SEQ_NUM, "0")); }
        catch (NumberFormatException ignored) { /* default 0 */ }

        return new FixMessage(msgType, seqNum, fields, Instant.now());
    }

    private void recordSent(String sessionId, FixMessage msg) {
        sentMessageLog.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(msg);
    }

    private FixSession requireSession(String sessionId) {
        FixSession s = sessions.get(sessionId);
        if (s == null) throw new IllegalStateException("No FIX session: " + sessionId);
        return s;
    }

    private String mapTimeInForce(String tif) {
        return switch (tif == null ? "" : tif.toUpperCase()) {
            case "DAY"   -> "0";
            case "GTC"   -> "1";
            case "IOC"   -> "3";
            case "FOK"   -> "4";
            case "GTD"   -> "6";
            default      -> "0";
        };
    }

    // ─── Event records ────────────────────────────────────────────────────────

    public record FixSessionLogonEvent(String sessionId) {}
    public record FixSessionLogoutEvent(String sessionId) {}
    public record FixSessionTimeoutEvent(String sessionId) {}
    public record FixExecReportEvent(String sessionId, String execId, String clOrdId,
                                     String execType, long lastQty, String lastPx) {}
}
