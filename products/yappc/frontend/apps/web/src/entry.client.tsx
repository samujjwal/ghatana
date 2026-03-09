import { HydratedRouter } from "react-router/dom";
import { startTransition, StrictMode } from "react";
import { hydrateRoot } from "react-dom/client";

async function boot() {
  // In production and development, we use real API calls.
  // MSW is only enabled for E2E tests when VITE_ENABLE_MSW=true

  startTransition(() => {
    hydrateRoot(
      document,
      <StrictMode>
        <HydratedRouter />
      </StrictMode>
    );
  });
}

boot();
