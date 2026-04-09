package com.ghatana.phr.hie;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Boundary for Nepal HIE submissions so connectivity can be tested independently
 * @doc.layer product
 * @doc.pattern Port
 */
public interface NepalHieClient {

    Promise<NepalHieAck> submitMessage(String patientId, String correlationId, String hl7Message);
}
