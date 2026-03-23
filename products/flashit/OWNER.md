# Owner: FlashIt — Personal Context Capture Platform

**Team:** FlashIt Team  
**Slack:** #product-flashit  
**On-call:** FlashIt on-call rotation  
**Architecture lead:** FlashIt Tech Lead  
**Boundary audit score:** 5/10 (2026-03-22) — active, composite build complexity

## Responsibility

FlashIt is a **full-stack personal context capture platform** enabling users to record, organise, and derive meaning from everyday thoughts, experiences, and media. It provides:
- React Native mobile app for on-the-go capture
- React Web dashboard for organisation and review
- AI-powered meaning extraction and summarisation
- Multi-modal capture (text, audio, images)

**Domain boundary:** FlashIt owns the personal context capture domain. It consumes `platform:java:ai-integration` for embeddings/summarisation and `products:data-cloud` for event storage. No other products should depend on FlashIt's internal modules.

## Architecture

```
┌─────────────────────────┐
│ Clients                 │
│ - React Native Mobile   │
│ - React Web Dashboard   │
│ - Admin CLI (planned)   │
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│ FlashIt API Gateway     │
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│ FlashIt Core Services   │
└─────────────────────────┘
```

See [docs/](docs/) for the full architecture breakdown.

## Known Issues

- `OWNER.md` was missing as of the 2026-03-22 boundary audit (accountability gap)
- Composite build complexity noted — evaluate if the build graph can be simplified
- Score of 5/10 indicates active work but architectural cleanup needed
