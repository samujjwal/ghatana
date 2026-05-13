#!/usr/bin/env python3
"""Validate agent definitions for consistency, completeness, and coherence.

Learning governance rules enforced:
  - L3+ agents must declare at least one evaluationRefs entry.
  - L2+ agents must set provenanceRequired: true.
  - L3+ agents must set promotionRequired: true.
  - Agents with masteryBindings must have all skillRef values resolve to a
    MasteryEntry in definitions/mastery/.
  - adaptationTargets values must be valid LearningTarget enum names.
  - skillRefs must resolve to mastery registry entries or be absent.
"""

import sys
import yaml
import os
from collections import defaultdict

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

VALID_LEARNING_LEVELS = {"L0", "L1", "L2", "L3", "L4", "L5"}

VALID_LEARNING_TARGETS = {
    "EPISODIC_MEMORY",
    "SEMANTIC_FACT",
    "RETRIEVAL_POLICY",
    "CONFIDENCE_THRESHOLD",
    "ROUTING_POLICY",
    "PROCEDURAL_SKILL",
    "NEGATIVE_KNOWLEDGE",
    "PROMPT_TEMPLATE",
    "PLANNER_POLICY",
    "MODEL_ADAPTER",
    "MASTERY_STATE",
}

# Minimum learning level ordinal that requires a given constraint.
# ordinal: L0=0, L1=1, L2=2, L3=3, L4=4, L5=5
_LEVEL_ORDINAL = {lvl: i for i, lvl in enumerate(["L0", "L1", "L2", "L3", "L4", "L5"])}
PROVENANCE_REQUIRED_FROM = _LEVEL_ORDINAL["L2"]
PROMOTION_REQUIRED_FROM = _LEVEL_ORDINAL["L3"]
EVAL_REFS_REQUIRED_FROM = _LEVEL_ORDINAL["L3"]


# ---------------------------------------------------------------------------
# Schema helpers
# ---------------------------------------------------------------------------

def get_id(data):
    """Extract agent/mastery ID from different schema formats."""
    if "metadata" in data and "id" in data["metadata"]:
        return data["metadata"]["id"]
    if "id" in data:
        return data["id"]
    return None


def get_level_number(data):
    """Extract agent hierarchy level (1/2/3)."""
    if "metadata" in data and "level" in data["metadata"]:
        return data["metadata"]["level"]
    if "spec" in data and "level" in data["spec"]:
        return data["spec"]["level"]
    return None


def get_schema_type(data):
    """Determine schema type."""
    kind = data.get("kind", "")
    if kind == "AgentDefinition":
        return "AgentDefinition"
    if kind == "MasteryEntry":
        return "MasteryEntry"
    if "id" in data:
        return "v1-legacy"
    return "unknown"


# ---------------------------------------------------------------------------
# Learning governance validation
# ---------------------------------------------------------------------------

def validate_learning_contract(agent_id, data, mastery_registry):
    """Return a list of error strings for learning governance violations.

    Args:
        agent_id: the agent ID string, for error messages.
        data: the parsed YAML dict.
        mastery_registry: set of known MasteryEntry IDs (from definitions/mastery/).
    """
    errors = []

    learning = data.get("learning")
    if learning is None:
        # Only AgentDefinition files require a learning block.
        return errors

    # --- learningLevel ---
    ll = learning.get("learningLevel")
    if ll is None:
        errors.append(f"{agent_id}: learning.learningLevel is missing")
        return errors
    if ll not in VALID_LEARNING_LEVELS:
        errors.append(
            f"{agent_id}: learning.learningLevel '{ll}' is invalid; "
            f"must be one of {sorted(VALID_LEARNING_LEVELS)}"
        )
        return errors

    ordinal = _LEVEL_ORDINAL[ll]

    # --- adaptationTargets ---
    targets = learning.get("adaptationTargets", [])
    if not isinstance(targets, list):
        errors.append(f"{agent_id}: learning.adaptationTargets must be a list")
    else:
        for t in targets:
            if t not in VALID_LEARNING_TARGETS:
                errors.append(
                    f"{agent_id}: learning.adaptationTargets contains invalid value '{t}'; "
                    f"must be one of {sorted(VALID_LEARNING_TARGETS)}"
                )

    # --- provenanceRequired (L2+) ---
    if ordinal >= PROVENANCE_REQUIRED_FROM:
        prov = learning.get("provenanceRequired")
        if prov is not True:
            errors.append(
                f"{agent_id}: learningLevel {ll} requires learning.provenanceRequired: true"
            )

    # --- promotionRequired (L3+) ---
    if ordinal >= PROMOTION_REQUIRED_FROM:
        prom = learning.get("promotionRequired")
        if prom is not True:
            errors.append(
                f"{agent_id}: learningLevel {ll} requires learning.promotionRequired: true"
            )

    # --- evaluationRefs (L3+) ---
    if ordinal >= EVAL_REFS_REQUIRED_FROM:
        eval_refs = learning.get("evaluationRefs")
        if not eval_refs:
            errors.append(
                f"{agent_id}: learningLevel {ll} requires at least one "
                "learning.evaluationRefs entry"
            )

    # --- skillRefs must resolve to mastery registry ---
    skill_refs = learning.get("skillRefs", [])
    if not isinstance(skill_refs, list):
        errors.append(f"{agent_id}: learning.skillRefs must be a list")
    else:
        for ref in skill_refs:
            if ref not in mastery_registry:
                errors.append(
                    f"{agent_id}: learning.skillRefs references unknown mastery entry "
                    f"'{ref}'; available: {sorted(mastery_registry)}"
                )

    # --- masteryBindings: each skillRef must resolve ---
    mastery_bindings = learning.get("masteryBindings", [])
    if not isinstance(mastery_bindings, list):
        errors.append(f"{agent_id}: learning.masteryBindings must be a list")
    else:
        for binding in mastery_bindings:
            if not isinstance(binding, dict):
                errors.append(f"{agent_id}: each entry in learning.masteryBindings must be a mapping")
                continue
            skill_ref = binding.get("skillRef")
            if not skill_ref:
                errors.append(f"{agent_id}: learning.masteryBindings entry is missing skillRef")
            elif skill_ref not in mastery_registry:
                errors.append(
                    f"{agent_id}: learning.masteryBindings.skillRef '{skill_ref}' "
                    f"not found in mastery registry; available: {sorted(mastery_registry)}"
                )
            if not binding.get("masteryPolicyRef"):
                errors.append(
                    f"{agent_id}: learning.masteryBindings entry for skillRef "
                    f"'{skill_ref}' is missing masteryPolicyRef"
                )

    return errors


# ---------------------------------------------------------------------------
# Main validation
# ---------------------------------------------------------------------------

def main():
    agents = {}
    mastery_registry = set()
    delegations = {}
    escalations = {}
    levels = {}
    schemas = defaultdict(list)
    capabilities = defaultdict(list)
    tools_by_agent = {}
    memory_configs = {}
    generator_types = defaultdict(list)
    structural_issues = []
    learning_errors = []

    path = "products/yappc/config/agents/definitions"

    # --- First pass: collect mastery registry IDs ---
    mastery_path = os.path.join(path, "mastery")
    if os.path.isdir(mastery_path):
        for f in os.listdir(mastery_path):
            if f.endswith(".yaml"):
                fp = os.path.join(mastery_path, f)
                with open(fp) as fh:
                    data = yaml.safe_load(fh)
                if data and data.get("kind") == "MasteryEntry":
                    mid = get_id(data)
                    if mid:
                        mastery_registry.add(mid)

    # --- Second pass: collect and validate agent definitions ---
    for root, dirs, files in os.walk(path):
        # Skip mastery directory in agent collection pass
        dirs[:] = [d for d in dirs if d != "mastery" or root != path]
        for f in files:
            if not f.endswith(".yaml"):
                continue
            fp = os.path.join(root, f)
            rel_path = fp.replace(path + "/", "")

            with open(fp) as fh:
                data = yaml.safe_load(fh)

            if data is None:
                structural_issues.append(f"{rel_path}: empty or unparseable YAML")
                continue

            schema_type = get_schema_type(data)

            if schema_type == "MasteryEntry":
                # Already handled in first pass; skip here.
                continue

            aid = get_id(data)
            if not aid:
                structural_issues.append(f"{rel_path}: no ID found in file")
                continue

            agents[aid] = rel_path
            levels[aid] = get_level_number(data)
            schemas[schema_type].append(aid)

            # Capabilities
            caps = data.get("capabilities", [])
            if "spec" in data:
                caps = data["spec"].get("capabilities", [])
            for cap in caps:
                capabilities[cap].append(aid)

            # Tools
            agent_tools = data.get("tools", [])
            if "spec" in data:
                agent_tools = data["spec"].get("tools", [])
            tools_by_agent[aid] = agent_tools

            # Memory
            memory_configs[aid] = data.get("memoryBindings", data.get("memory", {}))

            # Generator type (legacy field)
            gen = data.get("generator", {})
            generator_types[gen.get("type", "none")].append(aid)

            # Delegation
            deleg = data.get("delegation", {})
            if not deleg and "spec" in data:
                deleg = data["spec"].get("delegation", {})
            can_delegate = deleg.get("can_delegate_to", [])
            delegations[aid] = [
                d.replace("agent.yappc.", "") for d in (can_delegate if isinstance(can_delegate, list) else [])
            ]
            esc = deleg.get("escalates_to", [])
            if isinstance(esc, str):
                esc = [esc]
            elif not isinstance(esc, list):
                esc = []
            escalations[aid] = [e.replace("agent.yappc.", "") for e in esc]

            # Learning governance
            if schema_type == "AgentDefinition":
                errs = validate_learning_contract(aid, data, mastery_registry)
                learning_errors.extend(errs)

    # --- Delegation/escalation cross-reference checks ---
    missing_delegation = {}
    missing_escalation = {}
    for aid, targets in delegations.items():
        for t in targets:
            if t and t not in agents:
                missing_delegation.setdefault(aid, []).append(t)
    for aid, targets in escalations.items():
        for t in targets:
            if t and t not in agents:
                missing_escalation.setdefault(aid, []).append(t)

    delegated_to = set(t for ts in delegations.values() for t in ts)
    orphaned = [
        f"{aid} (level {lvl})"
        for aid, lvl in levels.items()
        if lvl and lvl > 1 and aid not in delegated_to
    ]

    top_level_governance = {
        "mission-alignment-agent",
        "human-override-arbitration-agent",
        "audit-trail-agent",
    }

    def can_reach_top(agent_id, visited=None):
        if visited is None:
            visited = set()
        if agent_id in visited:
            return False
        if "master-orchestrator-agent" in agent_id or agent_id in top_level_governance:
            return True
        visited.add(agent_id)
        return any(can_reach_top(esc, visited.copy()) for esc in escalations.get(agent_id, []))

    unreachable = [
        aid
        for aid in agents
        if levels.get(aid) != 1
        and aid not in top_level_governance
        and not can_reach_top(aid)
    ]

    # --- Report ---
    print("=" * 70)
    print("YAPPC AGENT COMPREHENSIVE VALIDATION REPORT")
    print("=" * 70)

    print(f"\n📊 STATISTICS")
    print(f"  Total agent definitions: {len(agents)}")
    print(f"  Mastery registry entries: {len(mastery_registry)}")
    print(f"    {sorted(mastery_registry)}")
    print(f"\n  Schema Distribution:")
    for schema, ids in sorted(schemas.items()):
        print(f"    {schema}: {len(ids)}")

    print(f"\n🎓 LEARNING GOVERNANCE")
    if learning_errors:
        print(f"  ❌ {len(learning_errors)} learning governance violation(s):")
        for err in learning_errors:
            print(f"    {err}")
    else:
        print(f"  ✅ All learning contracts are valid")

    print(f"\n🧠 CAPABILITY COVERAGE")
    print(f"  Unique capabilities defined: {len(capabilities)}")
    for cap, agt in sorted(capabilities.items(), key=lambda x: -len(x[1]))[:10]:
        print(f"    {cap}: {len(agt)} agents")

    print(f"\n🛠️  TOOL USAGE")
    all_tools = set(t for ts in tools_by_agent.values() for t in ts)
    print(f"  Unique tools referenced: {len(all_tools)}")
    agents_without_tools = [a for a, t in tools_by_agent.items() if not t]
    if agents_without_tools:
        print(f"  ⚠️ Agents without tools: {len(agents_without_tools)}")

    print(f"\n💾 MEMORY CONFIGURATION")
    episodic_enabled = sum(1 for m in memory_configs.values() if m.get("episodic", {}).get("enabled"))
    semantic_enabled = sum(1 for m in memory_configs.values() if m.get("semantic", {}).get("enabled"))
    procedural_enabled = sum(1 for m in memory_configs.values() if m.get("procedural", {}).get("enabled"))
    print(f"  Episodic memory enabled: {episodic_enabled}")
    print(f"  Semantic memory enabled: {semantic_enabled}")
    print(f"  Procedural memory enabled: {procedural_enabled}")

    print(f"\n🔗 DELEGATION CHAIN ANALYSIS")
    print(f"  Total delegation relationships: {sum(len(d) for d in delegations.values())}")
    if missing_delegation:
        print(f"  ❌ Missing delegation targets: {len(missing_delegation)}")
        for k, v in sorted(missing_delegation.items()):
            print(f"    {k}: {v}")
    else:
        print(f"  ✅ All delegation targets exist")
    if missing_escalation:
        print(f"  ❌ Missing escalation targets: {len(missing_escalation)}")
        for k, v in sorted(missing_escalation.items()):
            print(f"    {k}: {v}")
    else:
        print(f"  ✅ All escalation targets exist")
    if orphaned:
        print(f"  ⚠️ Orphaned agents (not delegated to): {len(orphaned)}")
        for o in orphaned:
            print(f"    {o}")
    else:
        print(f"  ✅ All non-strategic agents are reachable via delegation")
    if unreachable:
        print(f"  ⚠️ Agents with broken escalation chain: {len(unreachable)}")
        for u in unreachable[:5]:
            print(f"    {u}")
    else:
        print(f"  ✅ All agents can escalate to master orchestrator")

    print(f"\n📋 STRUCTURAL ISSUES")
    if structural_issues:
        for issue in structural_issues[:10]:
            print(f"  {issue}")
    else:
        print(f"  ✅ No structural issues found")

    print("\n" + "=" * 70)
    all_good = (
        not learning_errors
        and not missing_delegation
        and not missing_escalation
        and not structural_issues
    )
    if all_good:
        print("✅ ALL VALIDATION CHECKS PASSED")
    else:
        print("❌ VALIDATION FAILED — see errors above")
    print("=" * 70)

    return 0 if all_good else 1


if __name__ == "__main__":
    os.chdir('/Users/samujjwal/Development/ghatana')
    sys.exit(main())
