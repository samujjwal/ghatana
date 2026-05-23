import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildWave2ProductQualityScorecard,
  renderWave2ProductQualityScorecardMarkdown,
} from '../generate-wave2-product-quality-scorecard.mjs';

test('Wave 2 scorecard includes artifact-authoring readiness dimensions', () => {
  const scorecard = buildWave2ProductQualityScorecard();

  assert.equal(scorecard.generatedAt, 'generated-on-demand');
  assert.deepEqual(scorecard.artifactAuthoringGateScripts.sourceAcquisition, [
    'check:studio-source-acquisition-worker',
    'check:studio-production-profile:strict',
  ]);
  assert.deepEqual(scorecard.artifactAuthoringGateScripts.compilerDecompilerFidelity, [
    'check:artifact-roundtrip',
    'check:kernel-authoring-pipeline',
  ]);
  assert.deepEqual(scorecard.artifactAuthoringGateScripts.studioWorkflow, [
    'check:studio-artifact-workflow-e2e',
    'check:studio-deep-interactions',
  ]);
  assert.deepEqual(scorecard.artifactAuthoringGateScripts.generatedValidation, [
    'check:generated-artifact-validation-pipeline',
    'check:artifact-roundtrip',
  ]);
  assert.deepEqual(scorecard.artifactAuthoringGateScripts.evidencePersistence, [
    'check:studio-workflow-persistence-contracts',
  ]);

  assert.equal(scorecard.platformGateMap.artifactSourceAcquisition, true);
  assert.equal(scorecard.platformGateMap.artifactCompilerDecompilerFidelity, true);
  assert.equal(scorecard.platformGateMap.artifactStudioWorkflow, true);
  assert.equal(scorecard.platformGateMap.artifactGeneratedValidation, true);
  assert.equal(scorecard.platformGateMap.artifactEvidencePersistence, true);

  for (const row of scorecard.scoreRows) {
    assert.equal(row.area.artifactSourceAcquisition, true);
    assert.equal(row.area.artifactCompilerDecompilerFidelity, true);
    assert.equal(row.area.artifactStudioWorkflow, true);
    assert.equal(row.area.artifactGeneratedValidation, true);
    assert.equal(row.area.artifactEvidencePersistence, true);
    assert.equal(row.artifactAuthoring.sourceAcquisition, true);
    assert.equal(row.artifactAuthoring.compilerDecompilerFidelity, true);
    assert.equal(row.artifactAuthoring.studioWorkflow, true);
    assert.equal(row.artifactAuthoring.generatedValidation, true);
    assert.equal(row.artifactAuthoring.evidencePersistence, true);
  }
});

test('Wave 2 markdown renders artifact-authoring columns', () => {
  const scorecard = buildWave2ProductQualityScorecard();
  const markdown = renderWave2ProductQualityScorecardMarkdown(scorecard);

  assert.match(markdown, /Source Acquisition/);
  assert.match(markdown, /Compiler\/Decompiler Fidelity/);
  assert.match(markdown, /Studio Workflow/);
  assert.match(markdown, /Generated Validation/);
  assert.match(markdown, /Evidence Persistence/);
  assert.match(markdown, /generated-on-demand/);
});
