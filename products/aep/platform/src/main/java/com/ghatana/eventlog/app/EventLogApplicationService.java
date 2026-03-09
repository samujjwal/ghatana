package com.ghatana.eventlog.app;

import com.ghatana.contracts.event.v1.GetEventRequestProto;
import com.ghatana.contracts.event.v1.GetEventResponseProto;
import com.ghatana.contracts.event.v1.IngestBatchRequestProto;
import com.ghatana.contracts.event.v1.IngestBatchResponseProto;
import com.ghatana.contracts.event.v1.IngestRequestProto;
import com.ghatana.contracts.event.v1.IngestResponseProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.eventlog.EventLogStore;
import com.ghatana.platform.observability.Meters;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


import java.util.Objects;

/**
 * Facade over {@link EventLogStore} with basic metrics.
 */
public class EventLogApplicationService {


    private final EventLogStore store;
    private final MeterRegistry meters;

    public EventLogApplicationService(EventLogStore store, MeterRegistry meters) {
        this.store = Objects.requireNonNull(store, "store");
        this.meters = Objects.requireNonNull(meters, "meters");
    }

    public IngestResponseProto append(IngestRequestProto req) {
        Timer appendTimer = Meters.timer(meters, "eventlog.append.latency");
        return appendTimer.record(() -> {
            IngestResponseProto resp = store.append(req);
            if (resp != null) {
                Meters.counter(meters, "eventlog.append.requests").increment();
            }
            return resp;
        });
    }

    public IngestBatchResponseProto appendBatch(IngestBatchRequestProto req) {
        Timer appendBatchTimer = Meters.timer(meters, "eventlog.append_batch.latency");
        return appendBatchTimer.record(() -> {
            IngestBatchResponseProto resp = store.appendBatch(req);
            if (resp != null) {
                Meters.counter(meters, "eventlog.append_batch.requests").increment();
                Meters.counter(meters, "eventlog.append_batch.events", "status", "success").increment(resp.getSuccessCount());
            }
            return resp;
        });
    }

    public GetEventResponseProto get(GetEventRequestProto req) {
        Timer readTimer = Meters.timer(meters, "eventlog.read.latency");
        return readTimer.record(() -> store.get(req));
    }

    public QueryEventsResponseProto query(QueryEventsRequestProto req) {
        Timer queryTimer = Meters.timer(meters, "eventlog.query.latency");
        return queryTimer.record(() -> {
            QueryEventsResponseProto resp = store.query(req);
            if (resp != null) {
                Meters.counter(meters, "eventlog.query.requests").increment();
                Meters.counter(meters, "eventlog.query.results").increment(resp.getEventsCount());
            }
            return resp;
        });
    }
}
