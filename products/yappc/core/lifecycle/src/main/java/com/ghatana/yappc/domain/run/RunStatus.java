package com.ghatana.yappc.domain.run;

/**
 * @doc.type enum
 * @doc.purpose Run execution status
 * @doc.layer domain
 
 * @doc.pattern Enum
* @doc.gaa.lifecycle perceive
*/
public enum RunStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
