# FlashIt Vision

FlashIt is a product-owned moments and reflection experience built on the shared kernel. It exists to help users capture personal context, organize memories, derive meaning from reflection, and control privacy-sensitive sharing without pushing personal journaling semantics into platform code.

## Domain Boundary

FlashIt owns personal memory capture, reflection, journaling, search, memory expansion, collaboration, and privacy-facing experience decisions. Kernel and shared platform code must not absorb FlashIt's product semantics such as moment scoring, reflection prompts, journaling workflows, or subscription-tier behavior.

## Kernel Dependencies

FlashIt depends on kernel boundary-policy validation, audit, approval, observability, and product bridge contracts. Kernel owns those platform capabilities; FlashIt consumes them and provides its own domain pack, policy pack, gateway, agent, and client behavior on top.

## Platform Capabilities Consumed

- Boundary policy evaluation and policy-pack validation
- Audit and observability infrastructure
- Shared route/shell metadata contracts
- Shared API client and product package conventions
- Shared runtime template guidance for local/dev compose and launcher wiring

## Product-Only Business Logic

- Moment capture flows across text, image, audio, and video
- Reflection and language-insight workflows
- Search, memory expansion, and collaboration behavior
- Personal privacy controls, journaling defaults, and subscription-tier experiences
