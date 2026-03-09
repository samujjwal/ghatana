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
    private final TestJobService testJobService = new TestJobService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong jobsSubmitted = new AtomicLong();
    private final AtomicLong jobsCancelled = new AtomicLong();

    public ServerTestHarness() {
        // port assigned on start()
    }

    public ServerTestHarness start() throws IOException {
        grpcPort = findAvailablePort();
        httpPort = findAvailablePort();

        // Start gRPC server
        FakePolyfixService fakeService = new FakePolyfixService(testJobService);
        grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(fakeService)
                .build()
                .start();

        // Start HTTP server for REST / SSE endpoints
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(4));
        registerHttpEndpoints();
        httpServer.start();

        return this;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    /** Returns `host:port` string for gRPC channel targets. */
    public String getGrpcAddress() {
        return "localhost:" + grpcPort;
    }

    /** Returns the HTTP port allocated for REST / streaming endpoints. */
    public int getHttpPort() {
        return httpPort;
    }

    /** Returns the HTTP base URL (e.g. `http://localhost:PORT`). */
    public String getHttpBaseUrl() {
        return "http://localhost:" + httpPort;
    }

    @Override
    public void close() throws Exception {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer.awaitTermination();
        }
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Returns the {@link TestJobService} backed by this harness.
     */
    public TestJobService getJobService() {
        return testJobService;
    }

    // ---- HTTP endpoint registration ----

    private void registerHttpEndpoints() {
        httpServer.createContext("/api/v1/run", this::handleRun);
        httpServer.createContext("/api/v1/jobs/", this::handleJobRoutes);
        httpServer.createContext("/metrics", this::handleMetrics);
    }

    private void handleRun(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try (InputStream is = exchange.getRequestBody()) {
            RestModels.RunRequest req = objectMapper.readValue(is, RestModels.RunRequest.class);
            String idempotencyKey = req.idempotencyKey();
            String jobId = "job-" + UUID.randomUUID();

            Map<String, String> attrs = new java.util.HashMap<>();
            if (idempotencyKey != null) {
                attrs.put("idempotencyKey", idempotencyKey);
            }
            testJobService.submit(jobId, attrs);
            jobsSubmitted.incrementAndGet();

            String json = objectMapper.writeValueAsString(new RestModels.JobResponse(jobId));
            sendJson(exchange, 200, json);
        }
    }

    @SuppressWarnings("resource")
    private void handleJobRoutes(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        // path pattern: /api/v1/jobs/{jobId}[/status|/report|/events]
        String suffix = path.substring("/api/v1/jobs/".length());
        String[] parts = suffix.split("/", 2);
        String jobId = parts[0];
        String action = parts.length > 1 ? parts[1] : "";

        Optional<JobRecord> optRecord = testJobService.get(jobId);

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) && action.isEmpty()) {
            // Cancel
            if (optRecord.isPresent()) {
                JobRecord updated = optRecord.get().transition(JobState.CANCELLED, 0, null);
                testJobService.store.put(jobId, updated);
                jobsCancelled.incrementAndGet();
                sendJson(exchange, 200, objectMapper.writeValueAsString(toRunStatus(updated)));
            } else {
                sendJson(exchange, 404, "{\"error\":\"not found\"}");
            }
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        switch (action) {
            case "status" -> {
                if (optRecord.isEmpty()) {
                    sendJson(exchange, 404, "{\"error\":\"not found\"}");
                    return;
                }
                sendJson(exchange, 200, objectMapper.writeValueAsString(toRunStatus(optRecord.get())));
            }
            case "report" -> {
                if (optRecord.isEmpty()) {
                    sendJson(exchange, 404, "{\"error\":\"not found\"}");
                    return;
                }
                JobRecord rec = optRecord.get();
                RestModels.Report report = new RestModels.Report(
                        rec.jobId(),
                        "{\"state\":\"" + rec.state().name() + "\"}",
                        null,
                        rec.updatedAt());
                sendJson(exchange, 200, objectMapper.writeValueAsString(report));
            }
            case "events" -> {
                // SSE endpoint
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                String dataPrefix = "data: {\"jobId\":\"" + jobId + "\"";
                os.write(("event: connected\n" + dataPrefix + "}\n\n").getBytes(StandardCharsets.UTF_8));
                os.write(("event: status\n" + dataPrefix + ",\"state\":\"QUEUED\"}\n\n").getBytes(StandardCharsets.UTF_8));
                os.write(("event: progress\n" + dataPrefix + ",\"pass\":1}\n\n").getBytes(StandardCharsets.UTF_8));
                os.write(("event: complete\n" + dataPrefix + "}\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }
            default -> sendJson(exchange, 404, "{\"error\":\"unknown route\"}");
        }
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        String body = "# HELP polyfix_jobs_submitted_total Total submitted jobs\n"
                + "# TYPE polyfix_jobs_submitted_total counter\n"
                + "polyfix_jobs_submitted_total " + jobsSubmitted.get() + "\n"
                + "# HELP polyfix_jobs_cancelled_total Total cancelled jobs\n"
                + "# TYPE polyfix_jobs_cancelled_total counter\n"
                + "polyfix_jobs_cancelled_total " + jobsCancelled.get() + "\n";
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private RestModels.RunStatus toRunStatus(JobRecord rec) {
        return new RestModels.RunStatus(
                rec.jobId(),
                rec.state().name(),
                rec.currentPass(),
                rec.createdAt(),
                rec.updatedAt(),
                rec.attributes(),
                rec.errorMessage());
    }

    /**
     * Lightweight in-memory job service for test utilities such as {@link TestJobs}.
     */
    public class TestJobService {

        final Map<String, JobRecord> store = new ConcurrentHashMap<>();

        /**
         * Creates and stores a {@link JobRecord}
         * using the given idempotency key as the job id.
         */
        public JobRecord submit(String jobId, Map<String, ?> attributes) {
            @SuppressWarnings("unchecked")
            Map<String, String> attrs = (Map<String, String>) (Map<?, ?>) attributes;
            JobRecord record = JobRecord.newQueued(jobId, "test-tenant", attrs);
            store.put(jobId, record);
            return record;
        }

        public Optional<JobRecord> get(String jobId) {
            return Optional.ofNullable(store.get(jobId));
        }
    }

    /**
     * In-memory fake of {@link PolyfixServiceGrpc.PolyfixServiceImplBase} that satisfies
     * the adapter integration tests without any real orchestration.
     */
    private static final class FakePolyfixService
            extends PolyfixServiceGrpc.PolyfixServiceImplBase {

        private final TestJobService jobService;
        private final Map<String, RunStatus> grpcJobs = new ConcurrentHashMap<>();

        FakePolyfixService(TestJobService jobService) {
            this.jobService = jobService;
        }

        @Override
        public void run(RunRequest request, StreamObserver<JobId> responseObserver) {
            String jobId = "job-" + UUID.randomUUID();
            Map<String, String> attrs = new java.util.HashMap<>();
            String idempotencyKey = request.getIdempotencyKey();
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                attrs.put("idempotencyKey", idempotencyKey);
            }

            RunStatus status = RunStatus.newBuilder()
                    .setJobId(jobId)
                    .setState("QUEUED")
                    .setPass(0)
                    .setStartedAt(String.valueOf(System.currentTimeMillis()))
                    .setUpdatedAt(String.valueOf(System.currentTimeMillis()))
                    .putAllToolVersions(attrs)
                    .build();
            grpcJobs.put(jobId, status);

            // Also register in the shared test job service
            jobService.submit(jobId, attrs);

            responseObserver.onNext(JobId.newBuilder().setId(jobId).build());
            responseObserver.onCompleted();
        }

        @Override
        public void getStatus(JobId request, StreamObserver<RunStatus> responseObserver) {
            RunStatus status = grpcJobs.get(request.getId());
            if (status != null) {
                responseObserver.onNext(status);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId())
                                .asRuntimeException());
            }
        }

        @Override
        public void getReport(JobId request, StreamObserver<Report> responseObserver) {
            RunStatus status = grpcJobs.get(request.getId());
            if (status != null) {
                responseObserver.onNext(
                        Report.newBuilder()
                                .setJobId(status.getJobId())
                                .setSummaryJson("{\"state\":\"" + status.getState() + "\"}")
                                .setGeneratedAt(String.valueOf(System.currentTimeMillis()))
                                .build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId())
                                .asRuntimeException());
            }
        }

        @Override
        public void streamProgress(JobId request, StreamObserver<ProgressEvent> responseObserver) {
            RunStatus status = grpcJobs.get(request.getId());
            if (status == null) {
                responseObserver.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId())
                                .asRuntimeException());
                return;
            }
            // Emit 3 progress events
            for (int i = 1; i <= 3; i++) {
                responseObserver.onNext(
                        ProgressEvent.newBuilder()
                                .setJobId(request.getId())
                                .setEventType("progress")
                                .setMessage("Processing pass " + i)
                                .setCurrentPass(i)
                                .setTotalPasses(3)
                                .setTimestamp(System.currentTimeMillis())
                                .build());
            }
            responseObserver.onCompleted();
        }

        @Override
        public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
            responseObserver.onNext(
                    HealthResponse.newBuilder()
                            .setStatus("UP")
                            .setTimestamp(System.currentTimeMillis())
                            .build());
            responseObserver.onCompleted();
        }

        @Override
        public void diagnose(
                DiagnoseRequest request, StreamObserver<DiagnoseResponse> responseObserver) {
            responseObserver.onNext(
                    DiagnoseResponse.newBuilder()
                            .setExecutionId("exec-" + UUID.randomUUID())
                            .setTimestamp(System.currentTimeMillis())
                            .addDiagnostics(
                                    UnifiedDiagnostic.newBuilder()
                                            .setTool("fake-tool")
                                            .setRule("fake-rule")
                                            .setMessage("Fake diagnostic")
                                            .setFile(request.getRepoRoot() + "/Test.java")
                                            .setLine(1)
                                            .setColumn(1)
                                            .setSeverity("WARNING")
                                            .build())
                            .build());
            responseObserver.onCompleted();
        }
    }
}
