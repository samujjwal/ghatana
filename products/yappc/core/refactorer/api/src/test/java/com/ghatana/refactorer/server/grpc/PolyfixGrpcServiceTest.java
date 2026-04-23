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
    void mapsInvalidRunRequestsToInvalidArgument() { // GH-90000
        PolyfixGrpcService service = new PolyfixGrpcService(mock(JobService.class)); // GH-90000
        CapturingObserver<com.ghatana.refactorer.api.v1.JobId> observer = new CapturingObserver<>(); // GH-90000

        service.run(GrpcProtoFactory.sampleRunRequest().toBuilder().clearConfig().build(), observer); // GH-90000

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class); // GH-90000
        StatusRuntimeException error = (StatusRuntimeException) observer.error; // GH-90000
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT); // GH-90000
    }

    @Test
    @DisplayName("maps service exceptions to matching gRPC status codes")
    void mapsServiceExceptionsToGrpcStatuses() { // GH-90000
        StatusRuntimeException error = PolyfixGrpcService.toGrpcException( // GH-90000
                new ExceptionHandler.ServiceException(ErrorCode.STORAGE_UNAVAILABLE, "downstream unavailable")); // GH-90000

        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE); // GH-90000
        assertThat(error.getStatus().getDescription()).isEqualTo("downstream unavailable");
    }

    @Test
    @DisplayName("returns NOT_FOUND when requested job status is missing")
    void returnsNotFoundWhenJobStatusMissing() { // GH-90000
        JobService jobService = mock(JobService.class); // GH-90000
        when(jobService.get("missing-job")).thenReturn(Optional.empty());
        PolyfixGrpcService service = new PolyfixGrpcService(jobService); // GH-90000
        CapturingObserver<com.ghatana.refactorer.api.v1.RunStatus> observer = new CapturingObserver<>(); // GH-90000

        service.getStatus(JobId.newBuilder().setId("missing-job").build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class); // GH-90000
        StatusRuntimeException error = (StatusRuntimeException) observer.error; // GH-90000
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND); // GH-90000
        assertThat(error.getStatus().getDescription()).contains("missing-job");
    }

    @Test
    @DisplayName("returns a status payload when the job exists")
    void returnsStatusPayloadWhenJobExists() { // GH-90000
        JobService jobService = mock(JobService.class); // GH-90000
        JobRecord record = JobRecord.newQueued("job-1", "tenant-1", java.util.Map.of("idempotencyKey", "idem-1")); // GH-90000
        when(jobService.get("job-1")).thenReturn(Optional.of(record));
        PolyfixGrpcService service = new PolyfixGrpcService(jobService); // GH-90000
        CapturingObserver<com.ghatana.refactorer.api.v1.RunStatus> observer = new CapturingObserver<>(); // GH-90000

        service.getStatus(JobId.newBuilder().setId("job-1").build(), observer);

        assertThat(observer.value).isNotNull(); // GH-90000
        assertThat(observer.value.getJobId()).isEqualTo("job-1");
        assertThat(observer.completed).isTrue(); // GH-90000
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) { // GH-90000
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) { // GH-90000
            this.error = throwable;
        }

        @Override
        public void onCompleted() { // GH-90000
            this.completed = true;
        }
    }
}
