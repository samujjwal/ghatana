# YAPPC Terminology Glossary

This glossary is the canonical wording source for YAPPC routes, UI copy, API summaries, docs, and tests.

| Term | Definition | Use when | Avoid |
| --- | --- | --- | --- |
| Project | The workspace-scoped product effort that moves through the mounted YAPPC lifecycle. | Naming the user-visible work item, route context, dashboard card, or API resource. | App, application, product effort |
| Product | The real-world software product or business outcome that a YAPPC project is shaping. | Describing the market/customer outcome, not the stored YAPPC resource. | |
| App | A runnable software application produced, generated, deployed, or observed from a YAPPC project. | Naming deployed or generated runtime software, not the YAPPC project container. | Project, product |
| Artifact | A governed lifecycle output such as requirements, evidence, generated code, imports, or run results. | Referring to auditable lifecycle outputs that can be reviewed, persisted, or traced. | |
| Page document | The persisted page artifact envelope that stores builder content, governance metadata, sync state, and operation history. | Discussing page-artifact persistence, save/reload/conflict handling, or artifact operation logs. | |
| Builder document | The ui-builder document model containing registry-backed component instances and document metadata. | Discussing component contracts, registry validation, serialization, and renderer behavior. | |
| Canvas node | A node in the product canvas graph. Page-builder nodes may embed page documents. | Discussing spatial canvas interactions, graph import/export, or canvas provenance. | |
| Lifecycle packet | The phase-specific bundle of persisted data, derived readiness, evidence, blockers, suggestions, review state, and governance trace. | Describing what a mounted phase cockpit presents to help an operator decide the next action. | |

## Labeling Rules

- Use **project** for the persisted YAPPC work item and `/api/projects/*` resources.
- Use **product** only for the product being designed or delivered.
- Use **app** only for runnable software produced, generated, deployed, or observed from a project.
- Use **artifact** for auditable lifecycle outputs, including generated files and imports.
- Use **page document** for the persisted artifact wrapper and **builder document** for the ui-builder model inside it.
- Use **canvas node** for graph/spatial canvas entities.
- Use **lifecycle packet** when describing the full mounted cockpit contract.
