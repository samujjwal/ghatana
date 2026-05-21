package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

/**
 * Product bridge SPI for Kernel-brokered product interactions.
 *
 * <p>Product bridges implement this interface for externally declared contracts.
 * Consumers call through the Kernel broker and never import another product's
 * domain classes or storage adapters directly.</p>
 *
 * @param <Req> request payload type
 * @param <Res> response payload type
 *
 * @doc.type interface
 * @doc.purpose Shared bridge handler SPI for product-to-product interactions
 * @doc.layer kernel
 * @doc.pattern Port
 */
public interface ProductInteractionHandler<Req, Res> {

    String contractId();

    default String schemaVersion() {
        return "1.0.0";
    }

    Class<Req> requestType();

    Class<Res> responseType();

    Promise<ProductInteractionOutcome<Res>> handle(ProductInteractionRequest<Req> request);
}
