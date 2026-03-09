package com.ghatana.refactorer.server.grpc;

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
import com.ghatana.refactorer.server.auth.TenantContext;
import com.ghatana.refactorer.server.jobs.JobMappers;
import com.ghatana.refactorer.server.jobs.JobRecord;
import com.ghatana.refactorer.server.jobs.JobService;
import com.ghatana.refactorer.server.jobs.JobSubmission;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * gRPC service implementation for Polyfix operations. Delegates to orchestrator services for actual

 * processing.

 *

 * @doc.type class

 * @doc.purpose Expose refactorer job orchestration over gRPC while delegating to shared services.

 * @doc.layer product

 * @doc.pattern gRPC Adapter

 */

public final class PolyfixGrpcService extends PolyfixServiceGrpc.PolyfixServiceImplBase {
    private static final Logger logger = LogManager.getLogger(PolyfixGrpcService.class);
    private final JobService jobService;

    public PolyfixGrpcService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public void diagnose(
            DiagnoseRequest request, StreamObserver<DiagnoseResponse> responseObserver) {
        logger.info("Received diagnose request for repo: {}", request.getRepoRoot());

        try {
            // NOTE: Integrate with actual orchestrator service
            DiagnoseResponse response =
                    DiagnoseResponse.newBuilder()
                            .setExecutionId("exec-" + System.currentTimeMillis())
                            .setTimestamp(System.currentTimeMillis())
                            .addDiagnostics(
                                    UnifiedDiagnostic.newBuilder()
                                            .setTool("mock-tool")
                                            .setRule("mock-rule")
                                            .setMessage("Mock diagnostic for testing")
                                            .setFile(request.getRepoRoot() + "/test.java")
                                            .setLine(1)
                                            .setColumn(1)
                                            .setSeverity("INFO")
                                            .setTimestamp(System.currentTimeMillis())
                                            .build())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error processing diagnose request", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void run(RunRequest request, StreamObserver<JobId> responseObserver) {
        logger.info("Received run request for repo: {}", request.getConfig().getRepoRoot());

        try {
            TenantContext tenantContext = JobMappers.tenantContextFromProto(request);
            JobSubmission submission = JobMappers.fromProtoRunRequest(request, tenantContext);
            JobRecord record = jobService.submit(submission);

            JobId response = JobId.newBuilder().setId(record.jobId()).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Created job: {}", record.jobId());

        } catch (Exception e) {
            logger.error("Error processing run request", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getStatus(JobId request, StreamObserver<RunStatus> responseObserver) {
        logger.info("Received status request for job: {}", request.getId());

        try {
            Optional<JobRecord> record = jobService.get(request.getId());
            if (record.isPresent()) {
                responseObserver.onNext(JobMappers.toProtoStatus(record.get()));
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId())
                                .asRuntimeException());
            }

        } catch (Exception e) {
            logger.error("Error getting job status", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getReport(JobId request, StreamObserver<Report> responseObserver) {
        logger.info("Received report request for job: {}", request.getId());

        try {
            Optional<JobRecord> record = jobService.get(request.getId());
            if (record.isPresent()) {
                responseObserver.onNext(JobMappers.toProtoReport(record.get()));
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Job not found: " + request.getId())
                                .asRuntimeException());
            }

        } catch (Exception e) {
            logger.error("Error generating report", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void streamProgress(JobId request, StreamObserver<ProgressEvent> responseObserver) {
        logger.info("Received stream progress request for job: {}", request.getId());

        try {
            // NOTE: Integrate with actual progress streaming
            // Mock streaming a few events
            for (int i = 1; i <= 3; i++) {
                ProgressEvent event =
                        ProgressEvent.newBuilder()
                                .setJobId(request.getId())
                                .setEventType("progress")
                                .setMessage("Processing pass " + i)
                                .setCurrentPass(i)
                                .setTotalPasses(3)
                                .setTimestamp(System.currentTimeMillis())
                                .build();

                responseObserver.onNext(event);
            }

            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error streaming progress", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
        logger.debug("Received health check request");

        try {
            HealthResponse response =
                    HealthResponse.newBuilder()
                            .setStatus("UP")
                            .putDetails("version", "1.0.0")
                            .putDetails("uptime", String.valueOf(System.currentTimeMillis()))
                            .setTimestamp(System.currentTimeMillis())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error processing health check", e);
            responseObserver.onError(e);
        }
    }
}
