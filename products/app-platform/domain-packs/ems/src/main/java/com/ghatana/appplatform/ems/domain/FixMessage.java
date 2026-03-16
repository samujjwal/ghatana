package com.ghatana.appplatform.ems.domain;

import java.time.Instant;
import java.util.Map;

/**
 * @doc.type      Record
 * @doc.purpose   Represents a FIX protocol message with tag-value pairs.
 * @doc.layer     Domain
 * @doc.pattern   Immutable Value Object
 */
public record FixMessage(
        String msgType,
        int seqNum,
        Map<Integer, String> fields,
        Instant sentAt
) {

    /** FIX message type constants */
    public static final String LOGON         = "A";
    public static final String LOGOUT        = "5";
    public static final String HEARTBEAT     = "0";
    public static final String TEST_REQUEST  = "1";
    public static final String RESEND_REQUEST = "2";
    public static final String SEQUENCE_RESET = "4";
    public static final String NEW_ORDER_SINGLE = "D";
    public static final String EXEC_REPORT   = "8";
    public static final String ORDER_CANCEL  = "F";
    public static final String ORDER_CANCEL_REPLACE = "G";
    public static final String MARKET_DATA_REQUEST = "V";

    /** Common FIX tags */
    public static final int TAG_MSG_TYPE     = 35;
    public static final int TAG_SEQ_NUM      = 34;
    public static final int TAG_SENDER_COMP  = 49;
    public static final int TAG_TARGET_COMP  = 56;
    public static final int TAG_SENDING_TIME = 52;
    public static final int TAG_CL_ORD_ID   = 11;
    public static final int TAG_SYMBOL       = 55;
    public static final int TAG_SIDE         = 54;
    public static final int TAG_ORDER_QTY   = 38;
    public static final int TAG_PRICE        = 44;
    public static final int TAG_ORD_TYPE     = 40;
    public static final int TAG_TIME_IN_FORCE = 59;
    public static final int TAG_EXEC_TYPE    = 150;
    public static final int TAG_ORD_STATUS   = 39;
    public static final int TAG_LAST_QTY     = 32;
    public static final int TAG_LAST_PX      = 31;
    public static final int TAG_EXEC_ID      = 17;

    public String get(int tag) {
        return fields.getOrDefault(tag, null);
    }

    public boolean hasField(int tag) {
        return fields.containsKey(tag);
    }
}
