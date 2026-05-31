package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

/**
 * Contract for producing a single backend phase panel.
 *
 * @doc.type interface
 * @doc.purpose Defines SRP boundary for phase-specific panel generation
 * @doc.layer services
 * @doc.pattern Strategy
 */
public interface PhasePanelProvider {

    String phase();

    PhasePacket.PhasePanelView build(PhasePanelInput input);
}
