/**
 * TestGenerationPanel — AI-Y13
 *
 * Requests AI-generated test cases for a generated code artifact,
 * showing the suggested test suite and letting users copy or accept them.
 *
 * ## Data contract
 * `POST /api/runs/:runId/generate-tests`
 * Body: `{}` (context inferred from run)
 * Response:
 * ```json
 * {
 *   "runId": "run-1",
 *   "language": "TypeScript",
 *   "framework": "vitest",
 *   "tests": [
 *     { "id": "t1", "name": "should return 200 for valid input", "code": "..." }
 *   ],
 *   "source": "model",
 *   "confidence": 0.76
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Suggest AI-generated test cases for a code generation run
 * @doc.layer product
 * @doc.pattern Command / Data Display
 */

import React, { useState } from 'react';
import { Loader2, AlertCircle, FlaskConical, Copy } from 'lucide-react';
import { AIAssistLabel } from './AIAssistLabel';
import type { AIAssistSource } from './AIAssistLabel';
import { Button } from '../ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface GeneratedTestCase {
  id: string;
  name: string;
  code: string;
}

export interface TestGenerationResult {
  runId: string;
  language: string;
  framework: string;
  tests: GeneratedTestCase[];
  source: AIAssistSource;
  confidence: number;
}

export interface TestGenerationPanelProps {
  /** The code generation run ID to generate tests for. */
  runId: string;
  /** Called when the user accepts all generated tests. */
  onAccept?: (result: TestGenerationResult) => void;
  className?: string;
}

// ── API ────────────────────────────────────────────────────────────────────────

function buildUrl(runId: string): string {
  const meta = import.meta as ImportMeta & { env?: { DEV?: boolean; VITE_API_ORIGIN?: string } };
  const base =
    meta.env?.DEV === true
      ? (meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080')
      : '';
  return `${base}/api/runs/${runId}/generate-tests`;
}

async function generateTests(runId: string): Promise<TestGenerationResult> {
  const res = await fetch(buildUrl(runId), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  });
  if (!res.ok) throw new Error(`Test generation failed: ${res.status}`);
  return res.json() as Promise<TestGenerationResult>;
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function TestCaseItem({ test }: { test: GeneratedTestCase }) {
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    void navigator.clipboard.writeText(test.code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div
      data-testid={`test-case-${test.id}`}
      className="rounded-lg border border-border bg-surface p-3 space-y-1.5"
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-foreground">{test.name}</p>
        <Button
          data-testid={`test-copy-${test.id}`}
          onClick={handleCopy}
          aria-label={`Copy test: ${test.name}`}
          className="flex items-center gap-1 rounded border border-border px-1.5 py-0.5 text-xs text-muted hover:bg-accent"
          variant="outline"
          size="sm"
        >
          <Copy className="h-3 w-3" aria-hidden="true" />
          {copied ? 'Copied!' : 'Copy'}
        </Button>
      </div>
      <pre className="overflow-x-auto rounded bg-muted/20 px-2 py-1.5 text-xs text-foreground">
        <code>{test.code}</code>
      </pre>
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

type GenState = 'idle' | 'loading' | 'done' | 'error';

export function TestGenerationPanel({ runId, onAccept, className }: TestGenerationPanelProps) {
  const { t } = useI18n();
  const [state, setState] = useState<GenState>('idle');
  const [result, setResult] = useState<TestGenerationResult | null>(null);

  if (!runId) return null;

  async function handleGenerate() {
    setState('loading');
    setResult(null);
    try {
      const r = await generateTests(runId);
      setResult(r);
      setState('done');
    } catch {
      setState('error');
    }
  }

  return (
    <section
      data-testid="test-gen-panel"
      aria-label={t('ai.testGeneration.panelLabel')}
      className={['space-y-3', className].filter(Boolean).join(' ')}
    >
      {/* Idle trigger */}
      {state === 'idle' && (
        <Button
          data-testid="test-gen-btn"
          onClick={() => void handleGenerate()}
          className="flex items-center gap-1.5 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-foreground hover:bg-accent"
          variant="outline"
          size="sm"
        >
          <FlaskConical className="h-4 w-4" aria-hidden="true" />
          Generate tests
        </Button>
      )}

      {/* Loading */}
      {state === 'loading' && (
        <div data-testid="test-gen-loading" className="flex items-center gap-2 text-sm text-muted">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          Generating test suite…
        </div>
      )}

      {/* Error */}
      {state === 'error' && (
        <div className="space-y-2">
          <div data-testid="test-gen-error" className="flex items-center gap-2 text-sm text-destructive">
            <AlertCircle className="h-4 w-4" aria-hidden="true" />
            Could not generate tests.
          </div>
          <Button onClick={() => setState('idle')} className="text-xs text-muted underline" variant="link" size="sm">
            Try again
          </Button>
        </div>
      )}

      {/* Results */}
      {state === 'done' && result && (
        <div data-testid="test-gen-result" className="space-y-2">
          {/* Header row */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
              <FlaskConical className="h-4 w-4" aria-hidden="true" />
              {result.tests.length} test{result.tests.length !== 1 ? 's' : ''} · {result.framework}
            </div>
            <AIAssistLabel
              source={result.source}
              label={`${Math.round(result.confidence * 100)}% confidence`}
            />
          </div>

          {result.tests.map((t) => (
            <TestCaseItem key={t.id} test={t} />
          ))}

          <div className="flex gap-2 pt-1">
            {onAccept && (
              <Button
                data-testid="test-gen-accept-btn"
                onClick={() => onAccept(result)}
                className="rounded bg-primary px-3 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90"
                size="sm"
              >
                Accept tests
              </Button>
            )}
            <Button
              onClick={() => setState('idle')}
              data-testid="test-gen-regenerate-btn"
              className="rounded border border-border px-3 py-1 text-xs font-medium text-muted hover:bg-accent"
              variant="outline"
              size="sm"
            >
              Regenerate
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
