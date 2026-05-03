package com.ghatana.digitalmarketing.application.killswitch;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for kill switch management.
 *
 * @doc.type interface
 * @doc.purpose Emergency pause mechanism for running campaigns and connectors (DMOS-F2-015)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmKillSwitchService {

    Promise<DmKillSwitch> activate(DmOperationContext ctx, ActivateKillSwitchCommand command);

    Promise<DmKillSwitch> deactivate(DmOperationContext ctx, String killSwitchId);

    Promise<Optional<DmKillSwitch>> findById(DmOperationContext ctx, String killSwitchId);

    Promise<List<DmKillSwitch>> listActive(DmOperationContext ctx);

    Promise<Optional<DmKillSwitch>> findActiveByScope(DmOperationContext ctx, String scope, String scopeId);

    /**
     * Command to activate a kill switch for a given scope.
     */
    record ActivateKillSwitchCommand(
        String scope,
        String scopeId,
        String reason
    ) {
        public ActivateKillSwitchCommand {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(scopeId, "scopeId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (scope.isBlank()) throw new IllegalArgumentException("scope must not be blank");
            if (scopeId.isBlank()) throw new IllegalArgumentException("scopeId must not be blank");
            if (reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
