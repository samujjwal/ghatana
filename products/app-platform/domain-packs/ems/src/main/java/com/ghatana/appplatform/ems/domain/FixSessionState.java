package com.ghatana.appplatform.ems.domain;

/**
 * @doc.type      Enum
 * @doc.purpose   FIX protocol session states.
 * @doc.layer     Domain
 * @doc.pattern   State Machine Support
 */
public enum FixSessionState {
    DISCONNECTED,
    CONNECTING,
    LOGGED_ON,
    LOGGED_OUT
}
