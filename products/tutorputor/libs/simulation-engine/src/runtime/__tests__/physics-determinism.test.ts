import { describe, it, expect } from "vitest";
import { createPhysicsKernel, SeededRandom } from "../physics-kernel";

describe("Physics Kernel Determinism", () => {
  it("should be deterministic with the same seed", async () => {
    const seed = 12345;
    const kernel1 = createPhysicsKernel({ seed });
    const kernel2 = createPhysicsKernel({ seed });

    const manifest: any = {
      id: "sim-1",
      domain: "PHYSICS",
      steps: [],
      initialEntities: [], // Empty for now, but seeded RNG should match
    };

    kernel1.initialize(manifest);
    kernel2.initialize(manifest);

    await kernel1.step();
    const state1 = kernel1.serialize();

    await kernel2.step();
    const state2 = kernel2.serialize();

    expect(state1).toEqual(state2);
  });

  it("SeededRandom should produce consistent sequence", () => {
    const rng1 = new SeededRandom(42);
    const seq1 = [rng1.nextFloat(), rng1.nextFloat(), rng1.nextFloat()];

    const rng2 = new SeededRandom(42);
    const seq2 = [rng2.nextFloat(), rng2.nextFloat(), rng2.nextFloat()];

    expect(seq1).toEqual(seq2);
  });
});
