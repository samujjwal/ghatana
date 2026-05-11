package com.ghatana.yappc.governance.route;

/**
 * @doc.type enum
 * @doc.purpose Architectural boundary for route ownership
 * @doc.layer governance
 * @doc.pattern Enumeration
 */
public enum Boundary {
    /**
     * YAPPC product boundary - routes owned and operated within YAPPC
     */
    YAPPC,
    
    /**
     * Data Cloud + AEP boundary - routes migrating to shared platform services
     */
    DATA_CLOUD_AEP
}
