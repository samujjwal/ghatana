package com.ghatana.digitalmarketing.application.sow;

import com.ghatana.digitalmarketing.domain.sow.SowClause;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Repository contract for the DMOS SOW clause library.
 *
 * <p>Implementations load clause library entries, typically from a persistent
 * store or an in-process defaults registry. The repository is read-only from
 * the service layer; clause lifecycle management (authoring, approval) is a
 * separate administrative concern.</p>
 *
 * @doc.type class
 * @doc.purpose SOW clause library repository interface for F1-016
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SowClauseRepository {

    /**
     * Returns all approved clauses available for SOW generation.
     *
     * @return a promise resolving to the list of approved clauses (never null, may be empty)
     */
    Promise<List<SowClause>> findApprovedClauses();
}
