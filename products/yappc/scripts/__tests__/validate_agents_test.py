"""Acceptance tests for learning governance validation in validate_agents.py.

Each test invokes the real production function `validate_learning_contract`
with controlled inputs and asserts on the returned error list.
No test creates object-literal expectations that bypass production code.
"""

import sys
import os

import pytest

# Add the scripts directory to the path so we can import the production module.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from validate_agents import validate_learning_contract  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

MASTERY_REGISTRY = {
    "java-class-writing",
    "react-ui-generation",
    "requirements-drafting",
    "acceptance-criteria-formatting",
    "drift-detection",
    "memory-capability",
}


def _agent(learning: dict) -> dict:
    """Return a minimal AgentDefinition dict with the given learning block."""
    return {
        "apiVersion": "ghatana.yappc/v1",
        "kind": "AgentDefinition",
        "metadata": {"id": "test-agent"},
        "learning": learning,
    }


def _errors(learning: dict) -> list:
    return validate_learning_contract("test-agent", _agent(learning), MASTERY_REGISTRY)


# ---------------------------------------------------------------------------
# learningLevel validation
# ---------------------------------------------------------------------------

class TestLearningLevelValidation:

    def test_missing_learning_level_is_an_error(self):
        errs = _errors({})
        assert any("learningLevel is missing" in e for e in errs), errs

    def test_invalid_learning_level_is_an_error(self):
        errs = _errors({"learningLevel": "L9"})
        assert any("invalid" in e for e in errs), errs

    def test_valid_levels_produce_no_level_errors(self):
        for lvl in ["L0", "L1", "L2", "L3", "L4", "L5"]:
            learning = {
                "learningLevel": lvl,
                "provenanceRequired": True,
                "promotionRequired": True,
                "evaluationRefs": ["eval-pack-v1"],
            }
            errs = _errors(learning)
            level_errs = [e for e in errs if "learningLevel" in e and "invalid" in e]
            assert not level_errs, f"Level {lvl} raised unexpected errors: {level_errs}"


# ---------------------------------------------------------------------------
# L3+ evaluationRefs requirement
# ---------------------------------------------------------------------------

class TestEvaluationRefsRequirement:

    def test_l3_without_eval_refs_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": [],
        })
        assert any("evaluationRefs" in e for e in errs), errs

    def test_l4_without_eval_refs_fails(self):
        errs = _errors({
            "learningLevel": "L4",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": [],
        })
        assert any("evaluationRefs" in e for e in errs), errs

    def test_l5_without_eval_refs_fails(self):
        errs = _errors({
            "learningLevel": "L5",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": [],
        })
        assert any("evaluationRefs" in e for e in errs), errs

    def test_l3_with_eval_refs_passes_this_check(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
        })
        eval_errs = [e for e in errs if "evaluationRefs" in e]
        assert not eval_errs, eval_errs

    def test_l2_does_not_require_eval_refs(self):
        errs = _errors({
            "learningLevel": "L2",
            "provenanceRequired": True,
        })
        eval_errs = [e for e in errs if "evaluationRefs" in e]
        assert not eval_errs, eval_errs

    def test_l1_does_not_require_eval_refs(self):
        errs = _errors({"learningLevel": "L1"})
        eval_errs = [e for e in errs if "evaluationRefs" in e]
        assert not eval_errs, eval_errs


# ---------------------------------------------------------------------------
# L2+ provenanceRequired requirement
# ---------------------------------------------------------------------------

class TestProvenanceRequirement:

    def test_l2_without_provenance_required_fails(self):
        errs = _errors({"learningLevel": "L2", "provenanceRequired": False})
        assert any("provenanceRequired" in e for e in errs), errs

    def test_l2_missing_provenance_required_fails(self):
        errs = _errors({"learningLevel": "L2"})
        assert any("provenanceRequired" in e for e in errs), errs

    def test_l2_with_provenance_required_passes(self):
        errs = _errors({"learningLevel": "L2", "provenanceRequired": True})
        prov_errs = [e for e in errs if "provenanceRequired" in e]
        assert not prov_errs, prov_errs

    def test_l1_does_not_require_provenance(self):
        errs = _errors({"learningLevel": "L1"})
        prov_errs = [e for e in errs if "provenanceRequired" in e]
        assert not prov_errs, prov_errs


# ---------------------------------------------------------------------------
# L3+ promotionRequired requirement
# ---------------------------------------------------------------------------

class TestPromotionRequirement:

    def test_l3_without_promotion_required_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": False,
            "evaluationRefs": ["eval-pack-v1"],
        })
        assert any("promotionRequired" in e for e in errs), errs

    def test_l3_with_promotion_required_passes(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
        })
        prom_errs = [e for e in errs if "promotionRequired" in e]
        assert not prom_errs, prom_errs

    def test_l2_does_not_require_promotion(self):
        errs = _errors({"learningLevel": "L2", "provenanceRequired": True})
        prom_errs = [e for e in errs if "promotionRequired" in e]
        assert not prom_errs, prom_errs


# ---------------------------------------------------------------------------
# adaptationTargets validation
# ---------------------------------------------------------------------------

class TestAdaptationTargetsValidation:

    def test_invalid_learning_target_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "adaptationTargets": ["INVALID_TARGET"],
        })
        assert any("INVALID_TARGET" in e for e in errs), errs

    def test_all_valid_targets_pass(self):
        valid_targets = [
            "EPISODIC_MEMORY", "SEMANTIC_FACT", "RETRIEVAL_POLICY",
            "CONFIDENCE_THRESHOLD", "ROUTING_POLICY", "PROCEDURAL_SKILL",
            "NEGATIVE_KNOWLEDGE", "PROMPT_TEMPLATE", "PLANNER_POLICY",
            "MODEL_ADAPTER", "MASTERY_STATE",
        ]
        errs = _errors({
            "learningLevel": "L5",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "adaptationTargets": valid_targets,
        })
        target_errs = [e for e in errs if "adaptationTargets" in e]
        assert not target_errs, target_errs

    def test_non_list_adaptation_targets_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "adaptationTargets": "PROCEDURAL_SKILL",  # string, not list
        })
        assert any("adaptationTargets" in e for e in errs), errs


# ---------------------------------------------------------------------------
# skillRefs mastery registry resolution
# ---------------------------------------------------------------------------

class TestSkillRefsResolution:

    def test_unknown_skill_ref_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "skillRefs": ["unknown-skill"],
        })
        assert any("unknown mastery entry" in e for e in errs), errs

    def test_known_skill_ref_passes(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "skillRefs": ["java-class-writing"],
        })
        ref_errs = [e for e in errs if "skillRefs" in e]
        assert not ref_errs, ref_errs


# ---------------------------------------------------------------------------
# masteryBindings validation
# ---------------------------------------------------------------------------

class TestMasteryBindingsValidation:

    def test_mastery_binding_without_registry_ref_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "masteryBindings": [
                {"skillRef": "nonexistent-skill", "masteryPolicyRef": "policy-v1"},
            ],
        })
        assert any("not found in mastery registry" in e for e in errs), errs

    def test_mastery_binding_without_policy_ref_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "masteryBindings": [
                {"skillRef": "java-class-writing"},  # missing masteryPolicyRef
            ],
        })
        assert any("masteryPolicyRef" in e for e in errs), errs

    def test_mastery_binding_without_skill_ref_fails(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "masteryBindings": [
                {"masteryPolicyRef": "policy-v1"},  # missing skillRef
            ],
        })
        assert any("missing skillRef" in e for e in errs), errs

    def test_valid_mastery_binding_passes(self):
        errs = _errors({
            "learningLevel": "L3",
            "provenanceRequired": True,
            "promotionRequired": True,
            "evaluationRefs": ["eval-pack-v1"],
            "masteryBindings": [
                {"skillRef": "java-class-writing", "masteryPolicyRef": "promotion-policy-v1"},
            ],
        })
        binding_errs = [e for e in errs if "masteryBinding" in e]
        assert not binding_errs, binding_errs


# ---------------------------------------------------------------------------
# Full valid contract — no errors expected
# ---------------------------------------------------------------------------

class TestValidContract:

    def test_valid_l0_agent_passes(self):
        assert _errors({"learningLevel": "L0"}) == []

    def test_valid_l1_agent_passes(self):
        assert _errors({"learningLevel": "L1"}) == []

    def test_valid_l2_agent_passes(self):
        assert _errors({"learningLevel": "L2", "provenanceRequired": True}) == []

    def test_valid_agent_with_learning_contract_passes(self):
        """Full L3 agent definition with all required fields should produce zero errors."""
        errs = _errors({
            "learningLevel": "L3",
            "adaptationTargets": ["PROCEDURAL_SKILL", "EPISODIC_MEMORY", "SEMANTIC_FACT"],
            "skillRefs": ["java-class-writing"],
            "masteryBindings": [
                {"skillRef": "java-class-writing", "masteryPolicyRef": "promotion-policy-v1"},
            ],
            "masteryPolicyRefs": ["promotion-policy-v1"],
            "evaluationRefs": ["eval-pack-java-code-quality-v1"],
            "provenanceRequired": True,
            "promotionRequired": True,
        })
        assert errs == [], errs

    def test_valid_l5_governance_agent_passes(self):
        """L5 with MASTERY_STATE target and full required fields should pass."""
        errs = _errors({
            "learningLevel": "L5",
            "adaptationTargets": ["MASTERY_STATE", "MODEL_ADAPTER"],
            "skillRefs": ["memory-capability"],
            "masteryBindings": [
                {"skillRef": "memory-capability", "masteryPolicyRef": "promotion-policy-v1"},
            ],
            "evaluationRefs": ["eval-pack-memory-v1"],
            "provenanceRequired": True,
            "promotionRequired": True,
        })
        assert errs == [], errs

    def test_agent_without_learning_block_is_skipped(self):
        """An agent definition with no learning block produces no errors."""
        data = {
            "apiVersion": "ghatana.yappc/v1",
            "kind": "AgentDefinition",
            "metadata": {"id": "legacy-agent"},
        }
        errs = validate_learning_contract("legacy-agent", data, MASTERY_REGISTRY)
        assert errs == []
