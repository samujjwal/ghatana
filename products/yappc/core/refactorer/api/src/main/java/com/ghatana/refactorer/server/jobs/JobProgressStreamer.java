package com.ghatana.refactorer.server.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.server.dto.RestModels;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufStrings;
import io.activej.csp.supplier.ChannelSupplier;
import io.activej.csp.supplier.ChannelSuppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Streams job lifecycle updates to connected SSE/WebSocket clients.
 *
 * @doc.type class
 * @doc.purpose Multiplex job progress notifications to SSE/WebSocket transports safely.
 * @doc.layer product
 * @doc.pattern Streamer
 */
public final class JobProgressStreamer {
    private static final Logger logger = LogManager.getLogger(JobProgressStreamer.class);

    /**
 * Logical progress event container. */
    public record Event(String type, String data) {}

    private final JobService jobService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public JobProgressStreamer(JobService jobService) {
        this.jobService = jobService;
    }

    /**
 * Returns logical progress events for the given job if it exists. */
    public Optional<List<Event>> progressEvents(String jobId) {
        Optional<JobRecord> record = jobService.get(jobId);
        if (record.isEmpty()) {
            return Optional.empty();
        }

        List<Event> events = new ArrayList<>();
        try {
            events.add(new Event("connected", "{\"message\":\"Connected to job stream\"}"));

            RestModels.RunStatus status = JobMappers.toRestStatus(record.get());
            String statusJson = objectMapper.writeValueAsString(status);
            events.add(new Event("status", statusJson));

            String progressPayload =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "jobId",
                                    jobId,
                                    "eventType",
                                    "progress",
                                    "message",
                                    "Pass "
                                            + Math.max(status.pass(), 1)
                                            + " of "
                                            + Math.max(status.pass(), 1),
                                    "currentPass",
                                    status.pass(),
                                    "totalPasses",
                                    Math.max(status.pass(), 1)));
            events.add(new Event("progress", progressPayload));

            events.add(new Event("complete", "{\"jobId\":\"" + jobId + "\"}"));
        } catch (Exception e) {
            logger.warn("Failed to serialize job status", e);
            events.add(new Event("error", "{\"message\":\"Failed to serialize job status\"}"));
        }

        return Optional.of(events);
    }

    /**
 * Creates a channel supplier emitting SSE-formatted messages for the given job. */
    public Optional<ChannelSupplier<ByteBuf>> openStream(String jobId) {
        return progressEvents(jobId)
                .map(
                        list -> {
                            List<ByteBuf> buffers = new ArrayList<>(list.size());
                            for (Event event : list) {
                                buffers.add(toSseEvent(event));
                            }
                            return ChannelSuppliers.ofList(buffers);
                        });
    }

    private static ByteBuf toSseEvent(Event event) {
        String payload = "event: " + event.type() + "\n" + "data: " + event.data() + "\n\n";
        return ByteBufStrings.wrapUtf8(payload);
    }
}
