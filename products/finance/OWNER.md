# Owner: Finance

**Team:** Finance Team  
**Slack:** #platform-finance  
**On-call:** Finance on-call rotation  
**Architecture lead:** Finance Tech Lead  
**Boundary audit score:** 8/10 (2026-03-21) — reference implementation  

## Responsibility

The Finance product provides **financial operation capabilities** including:

- Order Management System (OMS)
- Risk management and exposure tracking
- Client onboarding and KYC workflows
- Regulatory reporting

**Domain boundary:** Finance owns all financial domain logic. It uses the kernel platform for lifecycle management and the plugin framework for extensibility.

## Best Practice Note

Finance is one of two **reference implementations** for product boundary hygiene (alongside Audio-Video). When in doubt about where to put code, look at how Finance is structured.
