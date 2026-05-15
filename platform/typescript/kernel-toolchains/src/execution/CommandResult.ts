export interface CommandResult {
  readonly commandId?: string;
  readonly exitCode: number;
  readonly stdout: string;
  readonly stderr: string;
  readonly durationMs: number;
  readonly startedAt?: string;
  readonly completedAt?: string;
  readonly timedOut?: boolean;
  readonly cancelled?: boolean;
  readonly stdoutTruncated?: boolean;
  readonly stderrTruncated?: boolean;
}
