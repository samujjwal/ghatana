package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import io.activej.promise.Promise;
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
public class GlobalWorkspaceController {

    private final GlobalWorkspace globalWorkspace;

    /**
     * Gets the current spotlight items.
     *
     * @return promise of spotlight items list
     */
    public Promise<List<SpotlightItem>> getSpotlight() {
        return Promise.of(globalWorkspace.getSpotlightItems());
    }
}
