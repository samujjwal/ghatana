package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import io.activej.promise.Promise;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.util.List;

/**
 * Controller for Global Workspace operations.
 *
 * @doc.type class
 * @doc.purpose Exposes Global Workspace data via API
 * @doc.layer product
 * @doc.pattern Controller
 */
@RequiredArgsConstructor
@Tag(name = "Global Workspace", description = "Workspace spotlight and discovery endpoints")
public class GlobalWorkspaceController {

    private final GlobalWorkspace globalWorkspace;

    /**
     * Gets the current spotlight items.
     *
     * @return promise of spotlight items list
     */
    @Operation(summary = "Get spotlight items", description = "Returns the current global workspace spotlight feed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spotlight items returned")
    })
    public Promise<List<SpotlightItem>> getSpotlight() {
        return Promise.of(globalWorkspace.getSpotlightItems());
    }
}
