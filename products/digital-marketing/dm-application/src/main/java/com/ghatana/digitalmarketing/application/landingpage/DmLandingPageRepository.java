package com.ghatana.digitalmarketing.application.landingpage;

import com.ghatana.digitalmarketing.domain.landingpage.DmLandingPage;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository port for landing page publishing runtime state.
 *
 * @doc.type class
 * @doc.purpose Persists landing page publishing lifecycle transitions (DMOS-F2-010)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmLandingPageRepository {

    Promise<DmLandingPage> save(DmLandingPage landingPage);

    Promise<Optional<DmLandingPage>> findById(String landingPageId);

    Promise<DmLandingPage> update(DmLandingPage landingPage);
}
