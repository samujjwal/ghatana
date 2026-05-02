package com.ghatana.digitalmarketing.domain.sow;

import java.util.Objects;

/**
 * A single risk flag attached to a DMOS SOW draft.
 *
 * <p>Risk flags are generated automatically during SOW draft creation and updated
 * if the draft is regenerated. They must be acknowledged by a human reviewer
 * before the SOW can be approved and exported.</p>
 *
 * @param flagType    the category of risk; see {@link SowRiskType}
 * @param description human-readable explanation of the specific risk
 * @param severity    {@code "WARNING"} (proceed with care) or {@code "BLOCKER"}
 *                    (must be resolved before approval)
 *
 * @doc.type class
 * @doc.purpose Risk flag value object for DMOS SOW drafts
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SowRiskFlag(SowRiskType flagType, String description, String severity) {

    public SowRiskFlag {
        Objects.requireNonNull(flagType, "flagType must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (severity.isBlank()) {
            throw new IllegalArgumentException("severity must not be blank");
        }
        if (!severity.equals("WARNING") && !severity.equals("BLOCKER")) {
            throw new IllegalArgumentException("severity must be 'WARNING' or 'BLOCKER'");
        }
    }
}
