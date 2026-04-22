/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.server.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.DiagnoseResponse;
import com.ghatana.refactorer.api.v1.HealthRequest;
import com.ghatana.refactorer.api.v1.HealthResponse;
import com.ghatana.refactorer.api.v1.JobId;
import com.ghatana.refactorer.api.v1.PolyfixServiceGrpc;
import com.ghatana.refactorer.api.v1.ProgressEvent;
import com.ghatana.refactorer.api.v1.Report;
import com.ghatana.refactorer.api.v1.RunRequest;
import com.ghatana.refactorer.api.v1.RunStatus;
import com.ghatana.refactorer.api.v1.UnifiedDiagnostic;
import com.ghatana.refactorer.server.dto.RestModels;
import com.ghatana.refactorer.server.jobs.JobRecord;
import com.ghatana.refactorer.server.jobs.JobState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test harness that starts a lightweight in-process gRPC server and HTTP server
 * for integration testing of adapters.

 * @doc.type class
 * @doc.purpose Handles server test harness operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ServerTestHarness implements AutoCloseable {

    private Server grpcServer;
    private HttpServer httpServer;
    private int grpcPort;
    private int httpPort;
    private final TestJobService testJobService = new TestJobService(); // GH-90000
    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
    private final AtomicLong jobsSubmitted = new AtomicLong(); // GH-90000
    private final AtomicLong jobsCancelled = new AtomicLong(); // GH-90000

    public ServerTestHarness() { // GH-90000
        // port assigned on start() // GH-90000
    }

    public ServerTestHarness start() throws IOException { // GH-90000
        grpcPort = findAvailablePort(); // GH-90000
        httpPort = findAvailablePort(); // GH-90000

        // Start gRPC server
        FakePolyfixService fakeService = new FakePolyfixService(testJobService); // GH-90000
        grpcServer = ServerBuilder.forPort(grpcPort) // GH-90000
                .addService(fakeService) // GH-90000
                .build() // GH-90000
                .start(); // GH-90000

        // Start HTTP server for REST / SSE endpoints
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0); // GH-90000
        httpServer.setExecutor(Executors.newFixedThreadPool(4)); // GH-90000
        registerHttpEndpoints(); // GH-90000
        httpServer.start(); // GH-90000

        return this;
    }

    public int getGrpcPort() { // GH-90000
        return grpcPort;
    }

    /** Returns `host:port` string for gRPC channel targets. */
    public String getGrpcAddress() { // GH-90000
        return "localhost:" + grpcPort;
    }

    /** Returns the HTTP port allocated for REST / streaming endpoints. */
    public int getHttpPort() { // GH-90000
        return httpPort;
    }

    /** Returns the HTTP base URL (e.g. `http://localhost:PORT`). */ // GH-90000
    public String getHttpBaseUrl() { // GH-90000
        return "http://localhost:" + httpPort;
    }

    @Override
    public void close() throws Exception { // GH-90000
        if (httpServer != null) { // GH-90000
            httpServer.stop(0); // GH-90000
        }
        if (grpcServer != null) { // GH-90000
            grpcServer.shutdownNow(); // GH-90000
            grpcServer.awaitTermination(); // GH-90000
        }
    }

    private static int findAvailablePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    /**
     * Returns the {@link TestJobService} backed by this harness.
     */
    public TestJobService getJobService() { // GH-90000
        return testJobService;
    }

    // ---- HTTP endpoint registration ----

    private void registerHttpEndpoints() { // GH-90000
        httpServer.createContext("/api/v1/run", this::handleRun); // GH-90000
        httpServer.createContext("/api/v1/jobs/", this::handleJobRoutes); // GH-90000
        httpServer.createContext("/metrics", this::handleMetrics); // GH-90000
    }

    private void handleRun(HttpExchange exchange) throws IOException { // GH-90000
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { // GH-90000
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); // GH-90000
            return;
        }
        try (InputStream is = exchange.getRequestBody()) { // GH-90000
            RestModels.RunRequest req = objectMapper.readValue(is, RestModels.RunRequest.class); // GH-90000
            String idempotencyKey = req.idempotencyKey(); // GH-90000
            String jobId = "job-" + UUID.randomUUID(); // GH-90000

            Map<String, String> attrs = new java.util.HashMap<>(); // GH-90000
            if (idempotencyKey != null) { // GH-90000
                attrs.put("idempotencyKey", idempotencyKey); // GH-90000
            }
            testJobService.submit(jobId, attrs); // GH-90000
            jobsSubmitted.incrementAndGet(); // GH-90000

            String json = objectMapper.writeValueAsString(new RestModels.JobResponse(jobId)); // GH-90000
            sendJson(exchange, 200, json); // GH-90000
        }
    }

    @SuppressWarnings("resource [GH-90000]")
    private void handleJobRoutes(HttpExchange exchange) throws IOException { // GH-90000
        String path = exchange.getRequestURI().getPath(); // GH-90000
        // path pattern: /api/v1/jobs/{jobId}[/status|/report|/events]
        String suffix = path.substring("/api/v1/jobs/".length()); // GH-90000
        String[] parts = suffix.split("/", 2); // GH-90000
        String jobId = parts[0];
        String action = parts.length > 1 ? parts[1] : "";

        Optional<JobRecord> optRecord = testJobService.get(jobId); // GH-90000

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) && action.isEmpty()) { // GH-90000
            // Cancel
            if (optRecord.isPresent()) { // GH-90000
                JobRecord updated = optRecord.get().transition(JobState.CANCELLED, 0, null); // GH-90000
                testJobService.store.put(jobId, updated); // GH-90000
                jobsCancelled.incrementAndGet(); // GH-90000
                sendJson(exchange, 200, objectMapper.writeValueAsString(toRunStatus(updated))); // GH-90000
            } else {
                sendJson(exchange, 404, "{\"error\":\"not found\"}"); // GH-90000
            }
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { // GH-90000
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}"); // GH-90000
            return;
        }

        switch (action) { // GH-90000
            case "status" -> {
                if (optRecord.isEmpty()) { // GH-90000
                    sendJson(exchange, 404, "{\"error\":\"not found\"}"); // GH-90000
                    return;
                }
                sendJson(exchange, 200, objectMapper.writeValueAsString(toRunStatus(optRecord.get()))); // GH-90000
            }
            case "report" -> {
                if (optRecord.isEmpty()) { // GH-90000
                    sendJson(exchange, 404, "{\"error\":\"not found\"}"); // GH-90000
                    return;
                }
                JobRecord rec = optRecord.get(); // GH-90000
                RestModels.Report report = new RestModels.Report( // GH-90000
                        rec.jobId(), // GH-90000
                        "{\"state\":\"" + rec.state().name() + "\"}", // GH-90000
                        null,
                        rec.updatedAt()); // GH-90000
                sendJson(exchange, 200, objectMapper.writeValueAsString(report)); // GH-90000
            }
            case "events" -> {
                // SSE endpoint
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); // GH-90000
                exchange.getResponseHeaders().set("Cache-Control", "no-cache"); // GH-90000
                exchange.sendResponseHeaders(200, 0); // GH-90000
                OutputStream os = exchange.getResponseBody(); // GH-90000
                String dataPrefix = "data: {\"jobId\":\"" + jobId + "\"";
                os.write(("event: connected\n" + dataPrefix + "}\n\n").getBytes(StandardCharsets.UTF_8)); // GH-90000
                os.write(("event: status\n" + dataPrefix + ",\"state\":\"QUEUED\"}\n\n").getBytes(StandardCharsets.UTF_8)); // GH-90000
                os.write(("event: progress\n" + dataPrefix + ",\"pass\":1}\n\n").getBytes(StandardCharsets.UTF_8)); // GH-90000
                os.write(("event: complete\n" + dataPrefix + "}\n\n").getBytes(StandardCharsets.UTF_8)); // GH-90000
                os.flush(); // GH-90000
                os.close(); // GH-90000
            }
            default -> sendJson(exchange, 404, "{\"error\":\"unknown route\"}"); // GH-90000
        }
    }

    private void handleMetrics(HttpExchange exchange) throws IOException { // GH-90000
        String body = "# HELP polyfix_jobs_submitted_total Total submitted jobs\n"
                + "# TYPE polyfix_jobs_submitted_total counter\n"
                + "polyfix_jobs_submitted_total " + jobsSubmitted.get() + "\n" // GH-90000
                + "# HELP polyfix_jobs_cancelled_total Total cancelled jobs\n"
                + "# TYPE polyfix_jobs_cancelled_total counter\n"
                + "polyfix_jobs_cancelled_total " + jobsCancelled.get() + "\n"; // GH-90000
        exchange.getResponseHeaders().set("Content-Type", "text/plain"); // GH-90000
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); // GH-90000
        exchange.sendResponseHeaders(200, bytes.length); // GH-90000
        exchange.getResponseBody().write(bytes); // GH-90000
        exchange.getResponseBody().close(); // GH-90000
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException { // GH-90000
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8); // GH-90000
        exchange.getResponseHeaders().set("Content-Type", "application/json"); // GH-90000
        exchange.sendResponseHeaders(status, bytes.length); // GH-90000
        exchange.getResponseBody().write(bytes); // GH-90000
        exchange.getResponseBody().close(); // GH-90000
    }

    private RestModels.RunStatus toRunStatus(JobRecord rec) { // GH-90000
        return new RestModels.RunStatus( // GH-90000
                rec.jobId(), // GH-90000
                rec.state().name(), // GH-90000
                rec.currentPass(), // GH-90000
                rec.createdAt(), // GH-90000
                rec.updatedAt(), // GH-90000
                rec.attributes(), // GH-90000
                rec.errorMessage()); // GH-90000
    }

    /**
     * Lightweight in-memory job service for test utilities such as {@link TestJobs}.
     */
    public class TestJobService {

        final Map<String, JobRecord> store = new ConcurrentHashMap<>(); // GH-90000

        /**
         * Creates and stores a {@link JobRecord}
         * using the given idempotency key as the job id.
         */
        public JobRecord submit(String jobId, Map<String, ?> attributes) { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, String> attrs = (Map<String, String>) (Map<?, ?>) attributes; // GH-90000
            JobRecord record = JobRecord.newQueued(jobId, "test-tenant", attrs); // GH-90000
            store.put(jobId, record); // GH-90000
            return record;
        }

        public Optional<JobRecord> get(String jobId) { // GH-90000
            return Optional.ofNullable(store.get(jobId)); // GH-90000
        }
    }

    /**
     * In-memory fake of {@link PolyfixServiceGrpc.PolyfixServiceImplBase} that satisfies
     * the adapter integration tests without any real orchestration.
     */
    private static final class FakePolyfixService
            extends PolyfixServiceGrpc.PolyfixServiceImplBase {

        private final TestJobService jobService;
        private final Map<String, RunStatus> grpcJobs = new ConcurrentHashMap<>(); // GH-90000

        FakePolyfixService(TestJobService jobService) { // GH-90000
            this.jobService = jobService;
        }

        @Override
        public void run(RunRequest request, StreamObserver<JobId> responseObserver) { // GH-90000
            String jobId = "job-" + UUID.randomUUID(); // GH-90000
            Map<String, String> attrs = new java.util.HashMap<>(); // GH-90000
            String idempotencyKey = request.getIdempotencyKey(); // GH-90000
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) { // GH-90000
                attrs.put("idempotencyKey", idempotencyKey); // GH-90000
            }

            RunStatus status = RunStatus.newBuilder() // GH-90000
                    .setJobId(jobId) // GH-90000
                    .setState("QUEUED [GH-90000]")
                    .setPass(0) // GH-90000
                    .setStartedAt(String.valueOf(System.currentTimeMillis())) // GH-90000
                    .setUpdatedAt(String.valueOf(System.currentTimeMillis())) // GH-90000
                    .putAllToolVersions(attrs) // GH-90000
                    .build(); // GH-90000
            grpcJobs.put(jobId, status); // GH-90000

            // Also register in the shared test job service
            jobService.submit(jobId, attrs); // GH-90000

            responseObserver.onNext(JobId.newBuilder().setId(jobId).build()); // GH-90000
            responseObserver.onCompleted(); // GH-90000
        }

        @Override
        public void getStatus(JobId request, StreamObserver<RunStatus> responseObserver) { // GH-90000
            RunStatus status = grpcJobs.get(request.getId()); // GH-90000
            if (status != null) { // GH-90000
                responseObserver.onNext(status); // GH-90000
                responseObserver.onCompleted(); // GH-90000
            } else {
                responseObserver.onError( // GH-90000
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId()) // GH-90000
                                .asRuntimeException()); // GH-90000
            }
        }

        @Override
        public void getReport(JobId request, StreamObserver<Report> responseObserver) { // GH-90000
            RunStatus status = grpcJobs.get(request.getId()); // GH-90000
            if (status != null) { // GH-90000
                responseObserver.onNext( // GH-90000
                        Report.newBuilder() // GH-90000
                                .setJobId(status.getJobId()) // GH-90000
                                .setSummaryJson("{\"state\":\"" + status.getState() + "\"}") // GH-90000
                                .setGeneratedAt(String.valueOf(System.currentTimeMillis())) // GH-90000
                                .build()); // GH-90000
                responseObserver.onCompleted(); // GH-90000
            } else {
                responseObserver.onError( // GH-90000
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId()) // GH-90000
                                .asRuntimeException()); // GH-90000
            }
        }

        @Override
        public void streamProgress(JobId request, StreamObserver<ProgressEvent> responseObserver) { // GH-90000
            RunStatus status = grpcJobs.get(request.getId()); // GH-90000
            if (status == null) { // GH-90000
                responseObserver.onError( // GH-90000
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId()) // GH-90000
                                .asRuntimeException()); // GH-90000
                return;
            }
            // Emit 3 progress events
            for (int i = 1; i <= 3; i++) { // GH-90000
                responseObserver.onNext( // GH-90000
                        ProgressEvent.newBuilder() // GH-90000
                                .setJobId(request.getId()) // GH-90000
                                .setEventType("progress [GH-90000]")
                                .setMessage("Processing pass " + i) // GH-90000
                                .setCurrentPass(i) // GH-90000
                                .setTotalPasses(3) // GH-90000
                                .setTimestamp(System.currentTimeMillis()) // GH-90000
                                .build()); // GH-90000
            }
            responseObserver.onCompleted(); // GH-90000
        }

        @Override
        public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) { // GH-90000
            responseObserver.onNext( // GH-90000
                    HealthResponse.newBuilder() // GH-90000
                            .setStatus("UP [GH-90000]")
                            .setTimestamp(System.currentTimeMillis()) // GH-90000
                            .build()); // GH-90000
            responseObserver.onCompleted(); // GH-90000
        }

        @Override
        public void diagnose( // GH-90000
                DiagnoseRequest request, StreamObserver<DiagnoseResponse> responseObserver) {
            responseObserver.onNext( // GH-90000
                    DiagnoseResponse.newBuilder() // GH-90000
                            .setExecutionId("exec-" + UUID.randomUUID()) // GH-90000
                            .setTimestamp(System.currentTimeMillis()) // GH-90000
                            .addDiagnostics( // GH-90000
                                    UnifiedDiagnostic.newBuilder() // GH-90000
                                            .setTool("fake-tool [GH-90000]")
                                            .setRule("fake-rule [GH-90000]")
                                            .setMessage("Fake diagnostic [GH-90000]")
                                            .setFile(request.getRepoRoot() + "/Test.java") // GH-90000
                                            .setLine(1) // GH-90000
                                            .setColumn(1) // GH-90000
                                            .setSeverity("WARNING [GH-90000]")
                                            .build()) // GH-90000
                            .build()); // GH-90000
            responseObserver.onCompleted(); // GH-90000
        }
    }
}
