import { mergeConfig } from "vitest/config";
import baseConfig from "./vitest.config";

export default mergeConfig(baseConfig, {
  test: {
    include: [
      "src/pages/__tests__/*.test.{ts,tsx}",
      "src/__tests__/route-contracts.test.tsx",
      "src/lib/__tests__/http-client.test.ts",
    ],
  },
});
