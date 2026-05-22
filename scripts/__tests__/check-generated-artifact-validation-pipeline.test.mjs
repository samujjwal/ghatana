import assert from 'node:assert/strict';
import test from 'node:test';

import {
  getGeneratedArtifactValidationStages,
} from '../check-generated-artifact-validation-pipeline.mjs';

test('generated artifact validation stages are complete and ordered', () => {
  const stages = getGeneratedArtifactValidationStages();

  assert.deepEqual(
    stages.map((stage) => stage.stageId),
    ['typecheck', 'lint', 'test', 'build', 'preview-render'],
  );

  assert.equal(stages.every((stage) => stage.command.length > 0), true);
  assert.equal(stages.every((stage) => stage.args.length > 0), true);
});
