# Changelog

All notable changes to `@ghatana/design-system` will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [0.1.0] — 2026-03-25

### Added
- `README.md` — package documentation with peer dependency install instructions,
  component layer table, theming setup, and accessibility notes.
  Closes audit finding MED-005.

### Notes
- Peer dependencies: `@ghatana/theme`, `@ghatana/tokens`, `react ^19.2.4`,
  `react-dom ^19.2.4`, `react-router ^7.0.0`. All are required at runtime.
- WCAG 2.1 AA compliance is in scope; violations should be filed as bugs.
