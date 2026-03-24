package com.ghatana.yappc.domain;

/**
 * @doc.type enum
 * @doc.purpose Represents the eight phases of the YAPPC lifecycle
 * @doc.layer domain
 
 * @doc.pattern Enum
* @doc.gaa.lifecycle perceive
*/
public enum PhaseType {
    INTENT,
    SHAPE,
    VALIDATE,
    GENERATE,
    RUN,
    OBSERVE,
    LEARN,
    EVOLVE
}
