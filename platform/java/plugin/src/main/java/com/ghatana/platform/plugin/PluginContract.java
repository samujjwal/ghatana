package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Defines a typed contract for plugin interactions.
 *
 * @param <Req> Request type
 * @param <Res> Response type
 *
 * @doc.type interface
 * @doc.purpose Typed interaction contract
 * @doc.layer core
 */
public interface PluginContract<Req, Res> {
    /**
     * Unique identifier for this contract.
     */
    @NotNull
    String contractId();

    /**
     * Class of the request object.
     */
    @NotNull
    Class<Req> requestType();

    /**
     * Class of the response object.
     */
    @NotNull
    Class<Res> responseType();
}
