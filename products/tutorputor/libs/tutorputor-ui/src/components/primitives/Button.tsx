/**
 * F-034: Button re-exported from @ghatana/design-system.
 *
 * The hand-rolled Button implementation has been replaced by the canonical
 * design-system Button. All callsites retain the same import path
 * (`@tutorputor/ui` or `@tutorputor/ui/components`) — no caller changes needed.
 *
 * @doc.type component
 * @doc.purpose Re-export of canonical Button from @ghatana/design-system
 * @doc.layer ui
 * @doc.pattern Primitive
 */

export { Button } from "@ghatana/design-system";
export type { ButtonProps } from "@ghatana/design-system";
