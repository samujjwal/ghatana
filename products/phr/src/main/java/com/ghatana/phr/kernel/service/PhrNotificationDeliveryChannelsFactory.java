package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.eventloop.Eventloop;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Resolves concrete PHR notification delivery channels from kernel context configuration
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class PhrNotificationDeliveryChannelsFactory {

    private PhrNotificationDeliveryChannelsFactory() {}

    public static PhrNotificationDeliveryChannels fromContext(KernelContext context) {
        Eventloop eventloop = Objects.requireNonNull(context.getEventloop(), "eventloop must not be null");
        PhrNotificationProviderConfig providerConfig = PhrNotificationProviderConfig.fromContext(context);
        if (!providerConfig.hasAnyEndpoint()) {
            throw new IllegalStateException(
                "PHR notification provider endpoints are required: configure one of "
                    + "phr.notification.email.endpoint, phr.notification.sms.endpoint, or phr.notification.push.endpoint"
            );
        }
        PhrNotificationDeliveryChannels baseChannels = createHttpChannels(context, providerConfig);
        return new ResilientPhrNotificationDeliveryChannels(eventloop, baseChannels);
    }

    private static PhrNotificationDeliveryChannels createHttpChannels(
            KernelContext context,
            PhrNotificationProviderConfig providerConfig) {
        Executor executor = context.getExecutor("phr-notification-delivery");
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(providerConfig.requestTimeout())
            .build();
        return new HttpPhrNotificationDeliveryChannels(httpClient, executor, providerConfig);
    }
}
