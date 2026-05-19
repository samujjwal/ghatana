# Platform Foundation Baseline

Snapshot: `04f03168e597cca638110f0025bd6231ac636fe5`

Captured from current workspace without checking out a new branch because `platform/kernel-todo.md` had pre-existing local edits.

## Environment

- Node: `v25.2.1`
- pnpm: `10.33.0`
- Java: `openjdk 21.0.9`
- Gradle: `9.2.1`

## Baseline Results

| Check                                                          | Result | Classification   |
| -------------------------------------------------------------- | -----: | ---------------- |
| `pnpm check:product-registry`                                  | passed | baseline pass    |
| `pnpm check:domain-registry`                                   | passed | baseline pass    |
| `pnpm check:architecture-boundaries`                           | passed | baseline pass    |
| `pnpm build:kernel-lifecycle-platform`                         | passed | baseline pass    |
| `pnpm --dir platform/typescript/ghatana-studio type-check`     | passed | baseline pass    |
| `pnpm --dir platform/typescript/kernel-product-contracts test` | passed | baseline pass    |
| `pnpm --dir platform/typescript/kernel-providers test`         | passed | post-change pass |
| `pnpm validate:phr`                                            | passed | post-change pass |

## Notes

- `pnpm install --frozen-lockfile` was not re-run because dependencies were already present and checks executed successfully from the existing lockfile state.
- No baseline failures were hidden. The only implementation blocker found during execution was PHR lifecycle validation, which failed closed on healthcare gates before product gate-pack providers were wired.
