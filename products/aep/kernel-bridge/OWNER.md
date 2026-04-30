# Owner: AEP Kernel Bridge

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Product kernel integration boundary. Provides the bridge between AEP and the product kernel runtime.

- Kernel module lifecycle integration
- AEP-as-kernel-module deployment
- Kernel event bridge
- Configuration synchronization

## Key Components

| Component | Purpose |
|-----------|---------|
| Kernel module adapter | AEP integration as kernel module |
| Lifecycle bridge | Kernel lifecycle to AEP lifecycle mapping |
| Event bridge | Kernel event propagation to AEP |

## Dependencies

- `platform-kernel:kernel-core`
- `platform-kernel:kernel-plugin`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Module size: Small (4 items)
