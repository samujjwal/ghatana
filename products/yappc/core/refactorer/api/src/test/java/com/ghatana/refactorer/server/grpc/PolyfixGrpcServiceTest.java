package com.ghatana.refactorer.server.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.refactorer.api.testutils.GrpcProtoFactory;
import com.ghatana.refactorer.api.v1.JobId;
import com.ghatana.refactorer.server.error.ExceptionHandler;
import com.ghatana.refactorer.server.jobs.JobRecord;
import com.ghatana.refactorer.server.jobs.JobService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies gRPC adapter error mapping to canonical status codes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolyfixGrpcService")
class PolyfixGrpcServiceTest {

    @Test
    @DisplayName("maps invalid run requests to INVALID_ARGUMENT")
    void mapsInvalidRunRequestsToInvalidArgument() {
        PolyfixGrpcService service = new PolyfixGrpcService(mock(JobService.class));
        CapturingObserver<com.ghatana.refactorer.api.v1.JobId> observer = new CapturingObserver<>();

        service.run(GrpcProtoFactory.sampleRunRequest().toBuilder().clearConfig().build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("maps service exceptions to matching gRPC status codes")
    void mapsServiceExceptionsToGrpcStatuses() {
        StatusRuntimeException error = PolyfixGrpcService.toGrpcException(
                new ExceptionHandler.ServiceException(ErrorCode.STORAGE_UNAVAILABLE, "downstream unavailable"));

        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(error.getStatus().getDescription()).isEqualTo("downstream unavailable");
    }

    @Test
    @DisplayName("returns NOT_FOUND when requested job status is missing")
    void returnsNotFoundWhenJobStatusMissing() {
        JobService jobService = mock(JobService.class);
        when(jobService.get("missing-job")).thenReturn(Optional.empty());
        PolyfixGrpcService service = new PolyfixGrpcService(jobService);
        CapturingObserver<com.ghatana.refactorer.api.v1.RunStatus> observer = new CapturingObserver<>();

        service.getStatus(JobId.newBuilder().setId("missing-job").build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(error.getStatus().getDescription()).contains("missing-job");
    }

    @Test
    @DisplayName("returns a status payload when the job exists")
    void returnsStatusPayloadWhenJobExists() {
        JobService jobService = mock(JobService.class);
        JobRecord record = JobRecord.newQueued("job-1", "tenant-1", java.util.Map.of("idempotencyKey", "idem-1"));
        when(jobService.get("job-1")).thenReturn(Optional.of(record));
        PolyfixGrpcService service = new PolyfixGrpcService(jobService);
        CapturingObserver<com.ghatana.refactorer.api.v1.RunStatus> observer = new CapturingObserver<>();

        service.getStatus(JobId.newBuilder().setId("job-1").build(), observer);

        assertThat(observer.value).isNotNull();
        assertThat(observer.value.getJobId()).isEqualTo("job-1");
        assertThat(observer.completed).isTrue();
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}