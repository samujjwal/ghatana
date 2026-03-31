import { describe, expect, it, vi, beforeEach } from "vitest";

const mocks = vi.hoisted(() => ({
  bind: vi.fn(),
  create: vi.fn(),
  start: vi.fn(),
  tryShutdown: vi.fn(),
}));

vi.mock("./grpc-service.js", () => ({
  createLearnerProfileGrpcServer: mocks.create,
  bindLearnerProfileGrpcServer: mocks.bind,
}));

import { startLearnerProfileGrpcRuntime } from "./grpc-runtime.js";

describe("startLearnerProfileGrpcRuntime", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.bind.mockResolvedValue(50052);
    mocks.tryShutdown.mockImplementation((callback: () => void) => callback());
    mocks.create.mockReturnValue({
      start: mocks.start,
      tryShutdown: mocks.tryShutdown,
    });
  });

  it("starts the grpc server and returns a stoppable runtime", async () => {
    const logger = { info: vi.fn() };
    const runtime = await startLearnerProfileGrpcRuntime({
      learnerProfileService: {} as never,
      address: "127.0.0.1:50052",
      logger: logger as never,
    });

    expect(mocks.create).toHaveBeenCalled();
    expect(mocks.bind).toHaveBeenCalledWith(
      expect.objectContaining({ start: mocks.start }),
      "127.0.0.1:50052",
    );
    expect(mocks.start).toHaveBeenCalled();
    expect(runtime.port).toBe(50052);

    await runtime.stop();
    expect(mocks.tryShutdown).toHaveBeenCalled();
  });
});
