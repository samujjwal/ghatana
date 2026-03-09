#!/usr/bin/env python3
"""Validate agent definitions for consistency, completeness, and coherence."""

import yaml
import os
import json
from collections import defaultdict

def get_id(data):
    """Extract agent ID from different schema formats."""
    if 'id' in data:
        return data['id']
    elif 'metadata' in data and 'id' in data['metadata']:
        return data['metadata']['id'].replace('agent.yappc.', '')
    return None

def get_level(data):
    """Extract agent level."""
    if 'metadata' in data and 'level' in data['metadata']:
        return data['metadata']['level']
    elif 'spec' in data and 'level' in data['spec']:
        return data['spec']['level']
    return None

def get_schema_type(data):
    """Determine schema type (v1 simple or v2 full)."""
    if 'kind' in data and data.get('kind') == 'AgentDefinition':
        return 'v2'
    elif 'id' in data:
        return 'v1'
    return 'unknown'

def main():
    agents = {}
    delegations = {}
    escalations = {}
    levels = {}
    schemas = defaultdict(list)
    capabilities = defaultdict(list)
    tools_by_agent = {}
    memory_configs = {}
    generator_types = defaultdict(list)
    issues = []
    
    path = 'products/yappc/config/agents/definitions'
    
    for root, dirs, files in os.walk(path):
        for f in files:
            if f.endswith('.yaml'):
                fp = os.path.join(root, f)
                rel_path = fp.replace(path + '/', '')
                
                with open(fp) as file:
                    data = yaml.safe_load(file)
                    
                aid = get_id(data)
                if aid:
                    agents[aid] = rel_path
                    levels[aid] = get_level(data)
                    schema_type = get_schema_type(data)
                    schemas[schema_type].append(aid)
                    
                    # Extract capabilities
                    caps = data.get('capabilities', [])
                    if 'spec' in data:
                        caps = data['spec'].get('capabilities', [])
                    for cap in caps:
                        capabilities[cap].append(aid)
                    
                    # Extract tools
                    agent_tools = data.get('tools', [])
                    if 'spec' in data:
                        agent_tools = data['spec'].get('tools', [])
                    tools_by_agent[aid] = agent_tools
                    
                    # Extract memory config
                    memory = data.get('memory', {})
                    memory_configs[aid] = memory
                    
                    # Extract generator type
                    gen = data.get('generator', {})
                    gen_type = gen.get('type', 'unknown')
                    generator_types[gen_type].append(aid)
                    
                    # Check for delegation
                    deleg = data.get('delegation', {})
                    if not deleg and 'spec' in data:
                        deleg = data['spec'].get('delegation', {})
                    
                    can_delegate = deleg.get('can_delegate_to', [])
                    if isinstance(can_delegate, list):
                        delegations[aid] = [d.replace('agent.yappc.', '') for d in can_delegate]
                    else:
                        delegations[aid] = []
                    
                    esc = deleg.get('escalates_to', [])
                    if isinstance(esc, str):
                        esc = [esc]
                    elif not isinstance(esc, list):
                        esc = []
                    escalations[aid] = [e.replace('agent.yappc.', '') for e in esc]
                else:
                    issues.append(f"{rel_path}: No ID found in file")
    
    # Find missing delegation targets
    missing_delegation = {}
    missing_escalation = {}
    
    for aid, targets in delegations.items():
        for t in targets:
            if t and t not in agents:
                if aid not in missing_delegation:
                    missing_delegation[aid] = []
                missing_delegation[aid].append(t)
    
    for aid, targets in escalations.items():
        for t in targets:
            if t and t not in agents:
                if aid not in missing_escalation:
                    missing_escalation[aid] = []
                missing_escalation[aid].append(t)
    
    # Check for orphaned agents (Level 2/3 with no one delegating to them)
    delegated_to = set()
    for targets in delegations.values():
        delegated_to.update(targets)
    
    orphaned = []
    for aid, level in levels.items():
        if level and level > 1 and aid not in delegated_to:
            orphaned.append(f"{aid} (level {level})")
    
    # Check for capability coverage
    sdlc_capabilities = [
        'code-generation', 'test-generation', 'documentation', 'code-review',
        'security-analysis', 'deployment', 'monitoring', 'incident-response'
    ]
    
    # Check escalation chain completeness (every agent should eventually reach master OR a designated top-level agent)
    # Some agents are designed to be governance endpoints (mission-alignment, human-override)
    top_level_governance = {
        'mission-alignment-agent', 
        'human-override-arbitration-agent',
        'audit-trail-agent'  # Foundational infrastructure
    }
    
    def can_reach_top(agent_id, visited=None):
        if visited is None:
            visited = set()
        if agent_id in visited:
            return False  # Cycle
        if 'master-orchestrator-agent' in agent_id:
            return True
        if agent_id in top_level_governance:
            return True  # Designated top-level
        visited.add(agent_id)
        for esc in escalations.get(agent_id, []):
            if can_reach_top(esc, visited.copy()):
                return True
        return False
    
    unreachable = []
    for aid in agents:
        if levels.get(aid) != 1 and aid not in top_level_governance and not can_reach_top(aid):
            unreachable.append(aid)
    
    # Report
    print("=" * 70)
    print("YAPPC AGENT COMPREHENSIVE VALIDATION REPORT")
    print("=" * 70)
    
    print(f"\n📊 STATISTICS")
    print(f"  Total agents: {len(agents)}")
    print(f"  Level 1 (Strategic Orchestrators): {sum(1 for v in levels.values() if v == 1)}")
    print(f"  Level 2 (Domain Experts): {sum(1 for v in levels.values() if v == 2)}")
    print(f"  Level 3 (Atomic Workers): {sum(1 for v in levels.values() if v == 3)}")
    print(f"\n  Schema Distribution:")
    print(f"    v1 (simple): {len(schemas['v1'])}")
    print(f"    v2 (full): {len(schemas['v2'])}")
    
    print(f"\n🔧 GENERATOR TYPES")
    for gt, agt in sorted(generator_types.items(), key=lambda x: -len(x[1])):
        print(f"  {gt}: {len(agt)} agents")
    
    print(f"\n🧠 CAPABILITY COVERAGE")
    print(f"  Unique capabilities defined: {len(capabilities)}")
    top_caps = sorted(capabilities.items(), key=lambda x: -len(x[1]))[:10]
    for cap, agt in top_caps:
        print(f"    {cap}: {len(agt)} agents")
    
    print(f"\n🛠️  TOOL USAGE")
    all_tools = set()
    for tools in tools_by_agent.values():
        all_tools.update(tools)
    print(f"  Unique tools referenced: {len(all_tools)}")
    agents_without_tools = [a for a, t in tools_by_agent.items() if not t]
    if agents_without_tools:
        print(f"  ⚠️ Agents without tools: {len(agents_without_tools)}")
    
    print(f"\n💾 MEMORY CONFIGURATION")
    episodic_enabled = sum(1 for m in memory_configs.values() if m.get('episodic', {}).get('enabled'))
    semantic_enabled = sum(1 for m in memory_configs.values() if m.get('semantic', {}).get('enabled'))
    procedural_enabled = sum(1 for m in memory_configs.values() if m.get('procedural', {}).get('enabled'))
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
    
    print(f"\n📋 FIELD ISSUES")
    if issues:
        for issue in issues[:10]:
            print(f"  {issue}")
    else:
        print(f"  ✅ No structural issues found")
    
    # Overall status
    print("\n" + "=" * 70)
    all_good = (
        not missing_delegation and 
        not missing_escalation and 
        not orphaned and 
        not unreachable and
        not issues
    )
    if all_good:
        print("✅ ALL VALIDATION CHECKS PASSED")
    else:
        print("⚠️ SOME ISSUES NEED ATTENTION")
    print("=" * 70)

if __name__ == '__main__':
    os.chdir('/Users/samujjwal/Development/ghatana')
    main()
