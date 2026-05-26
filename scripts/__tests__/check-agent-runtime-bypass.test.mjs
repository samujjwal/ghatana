#!/usr/bin/env node

import { describe, it } from 'node:test';
import assert from 'node:assert';
import { scanFile, isAllowed, VIOLATION_PATTERNS } from '../check-agent-runtime-bypass.mjs';

describe('check-agent-runtime-bypass', () => {
  it('should allow files in the capability adapters allowlist', () => {
    const allowedPath = 'products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java';
    assert.strictEqual(isAllowed(allowedPath), true);
  });

  it('should allow test files', () => {
    assert.strictEqual(isAllowed('products/data-cloud/test/SomeTest.java'), true);
    assert.strictEqual(isAllowed('products/data-cloud/__tests__/SomeTest.java'), true);
  });

  it('should not allow production files outside allowlist', () => {
    assert.strictEqual(isAllowed('products/data-cloud/SomeProduction.java'), false);
  });

  it('should detect direct TypedAgent.process calls', () => {
    const content = `
      public class MyService {
        public void execute() {
          TypedAgent<String, String> agent = new MyAgent();
          agent.process(context, input); // This should be detected
        }
      }
    `;
    const violations = scanFile('products/data-cloud/MyService.java', content);
    assert.strictEqual(violations.length, 1);
    assert.strictEqual(violations[0].pattern, 'direct_typed_agent_process');
  });

  it('should detect direct model calls', () => {
    const content = `
      public class MyService {
        public void execute() {
          model.callModel(input); // This should be detected
        }
      }
    `;
    const violations = scanFile('products/data-cloud/MyService.java', content);
    assert.strictEqual(violations.length, 1);
    assert.strictEqual(violations[0].pattern, 'direct_model_call');
  });

  it('should detect direct tool calls', () => {
    const content = `
      public class MyService {
        public void execute() {
          tool.executeTool(input); // This should be detected
        }
      }
    `;
    const violations = scanFile('products/data-cloud/MyService.java', content);
    assert.strictEqual(violations.length, 1);
    assert.strictEqual(violations[0].pattern, 'direct_tool_call');
  });

  it('should detect direct action calls', () => {
    const content = `
      public class MyService {
        public void execute() {
          action.executeAction(input); // This should be detected
        }
      }
    `;
    const violations = scanFile('products/data-cloud/MyService.java', content);
    assert.strictEqual(violations.length, 1);
    assert.strictEqual(violations[0].pattern, 'direct_action_call');
  });

  it('should not scan allowed files for violations', () => {
    const content = `
      public class GovernedAgentDispatcher {
        public void execute() {
          TypedAgent<String, String> agent = new MyAgent();
          agent.process(context, input); // This is allowed in GovernedAgentDispatcher
        }
      }
    `;
    const violations = scanFile('products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java', content);
    assert.strictEqual(violations.length, 0);
  });

  it('should have all required violation patterns defined', () => {
    const patternNames = VIOLATION_PATTERNS.map(p => p.name);
    assert(patternNames.includes('direct_typed_agent_process'));
    assert(patternNames.includes('direct_agent_dispatcher_dispatch'));
    assert(patternNames.includes('direct_model_call'));
    assert(patternNames.includes('direct_tool_call'));
    assert(patternNames.includes('direct_action_call'));
  });

  it('should provide line numbers for violations', () => {
    const content = `
      line 1
      line 2
      line 3
      TypedAgent.process(context, input);
      line 5
    `;
    const violations = scanFile('products/data-cloud/MyService.java', content);
    assert.strictEqual(violations.length, 1);
    assert.strictEqual(violations[0].line, 4);
  });
});
