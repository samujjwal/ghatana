/**
 * @fileoverview Builder route page — wraps the BuilderStudio section.
 *
 * Provides the Studio shell route for the visual UI builder workflow.
 *
 * @doc.type component
 * @doc.purpose Visual UI builder route
 * @doc.layer studio
 */

import type { ReactElement } from "react";
import BuilderStudio from "../sections/BuilderStudio";

/**
 * Route page for the visual UI Builder.
 * Mounts the full BuilderStudio section as a Studio route.
 */
export default function BuilderPage(): ReactElement {
  return <BuilderStudio />;
}
