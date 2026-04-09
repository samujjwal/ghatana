package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.eventloop.Eventloop;
import java.util.Objects;

final class PhrNotificationSenders {

    private PhrNotificationSenders() {}

    static PhrNotificationSender fromContext(KernelContext context) {
        Eventloop eventloop = Objects.requireNonNull(context.getEventloop(), "eventloop must not be null");
        PhrNotificationSender sender = context
            .getOptionalDependency(PhrNotificationSender.class)
            .orElse(NoOpPhrNotificationSender.INSTANCE);
        return new ResilientPhrNotificationSender(eventloop, sender);
    }
}
