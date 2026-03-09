package com.ghatana.refactorer.server.testutils;

import com.ghatana.refactorer.server.jobs.JobRecord;
import java.util.Map;

/**
 * Utility for creating and submitting test jobs through the harness job service.
 
 * @doc.type class
 * @doc.purpose Handles test jobs operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class TestJobs {

    private TestJobs() {}

    /**
     * Submits a job with the given idempotency key and returns the resulting {@link JobRecord}.
     *
     * @param jobService     the test job service from the harness
     * @param idempotencyKey the idempotency key for the job
     * @return the created {@link JobRecord}
     */
    public static JobRecord submit(
            ServerTestHarness.TestJobService jobService, String idempotencyKey) {
        return jobService.submit(idempotencyKey, Map.of());
    }
}
