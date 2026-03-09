package com.ghatana.requirements.ai.persona;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Persona data access.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides access to persona configurations used for context-aware requirement
 * generation. Personas define specialized perspectives (security analyst,
 * business analyst, etc.) that influence how requirements are generated.
 *
 * <p>
 * <b>Implementation Notes</b><br>
 * Implementations should provide efficient lookups and caching of frequently
 * used personas. All operations are Promise-based for async execution.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PersonaRepository repository = new InMemoryPersonaRepository();
 *
 * // Find persona by ID
 * Promise<Optional<Persona>> persona = repository.findById("security-analyst");
 *
 * // Get all active personas
 * Promise<List<Persona>> active = repository.findAllActive();
 * }</pre>
 *
 * @see Persona
 * @see PersonaType
 * @doc.type interface
 * @doc.purpose Persona data access abstraction
 * @doc.layer application
 * @doc.pattern Repository
 */
public interface PersonaRepository {

    /**
     * Finds persona by unique identifier.
     *
     * @param id persona identifier
     * @return promise of optional persona (empty if not found)
     */
    Promise<Optional<Persona>> findById(String id);

    /**
     * Finds all active personas available for use.
     *
     * @return promise of list of active personas
     */
    Promise<List<Persona>> findAllActive();

    /**
     * Finds personas by type category.
     *
     * @param type persona type filter
     * @return promise of list of personas of specified type
     */
    Promise<List<Persona>> findByType(PersonaType type);

    /**
     * Saves or updates a persona.
     *
     * @param persona persona to save
     * @return promise of saved persona
     */
    Promise<Persona> save(Persona persona);

    /**
     * Checks if a persona exists by ID.
     *
     * @param id persona identifier
     * @return promise of true if exists, false otherwise
     */
    default Promise<Boolean> exists(String id) {
        return findById(id).map(Optional::isPresent);
    }
}
