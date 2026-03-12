# Siddhanta TDD Prompt Suite Index

This index organizes the specialized TDD prompts according to the current execution plan and implementation order in [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md).

Use the base prompt first:
- [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md)

Then apply the specialized prompt that matches the current implementation wave.

---

## Recommended Use Order

### Phase 0: Execution Bootstrap
1. [tdd_prompt_phase0_bootstrap_v1.md](tdd_prompt_phase0_bootstrap_v1.md)

### Phase 1: Kernel Foundation
1. [tdd_prompt_k05_event_bus_v1.md](tdd_prompt_k05_event_bus_v1.md)
2. [tdd_prompt_k15_dual_calendar_v1.md](tdd_prompt_k15_dual_calendar_v1.md)
3. [tdd_prompt_k07_audit_framework_v1.md](tdd_prompt_k07_audit_framework_v1.md)
4. [tdd_prompt_k02_configuration_engine_v1.md](tdd_prompt_k02_configuration_engine_v1.md)

### Phase 2: Kernel Completion and Control Plane
1. [tdd_prompt_phase2_kernel_completion_v1.md](tdd_prompt_phase2_kernel_completion_v1.md)

### Phase 3: Trading MVP Domain Path
1. [tdd_prompt_phase3_trading_mvp_v1.md](tdd_prompt_phase3_trading_mvp_v1.md)

### Phase 4 and Phase 5: Operational Scale-Out, Hardening, and Launch Readiness
1. [tdd_prompt_phase4_5_operational_hardening_v1.md](tdd_prompt_phase4_5_operational_hardening_v1.md)

---

## Selection Rules

- Use the most specific prompt available.
- If you are working on a single early kernel module, prefer the module-specific prompt over the phase-level prompt.
- If you are planning a coordinated delivery wave across multiple modules, use the phase-level prompt.
- Always combine the specialized prompt with the base prompt rather than replacing the base prompt.

---

## Authority Order

All prompts in this suite assume the following source precedence:
1. [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)
2. Current accepted ADRs
3. [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
4. Relevant LLDs
5. Relevant epics and stories
6. Architecture and C4 docs
7. Existing implementation and tests

---

## Output Expectations

Every specialized prompt in this suite is intended to force:
- exhaustive scenario decomposition
- explicit expected inputs and outputs
- positive and negative coverage
- branch and statement coverage mapping
- test-first sequencing
- machine-readable test-plan output for automatic test and code generation
