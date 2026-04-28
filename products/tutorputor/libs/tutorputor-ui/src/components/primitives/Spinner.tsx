/**
 * F-034: Spinner re-exported from @ghatana/design-system.
 *
 * The hand-rolled Spinner implementation has been replaced by the canonical
 * design-system Spinner. All callsites retain the same import path
 * (`@tutorputor/ui` or `@tutorputor/ui/components`) — no caller changes needed.
 *
 * @doc.type component
 * @doc.purpose Re-export of canonical Spinner from @ghatana/design-system
 * @doc.layer ui
 * @doc.pattern Primitive
 */

export { Spinner } from "@ghatana/design-system";
export type { SpinnerProps } from "@ghatana/design-system";
