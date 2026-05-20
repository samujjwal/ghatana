/**
 * @fileoverview Design System Generation Page
 *
 * Provides a workflow for generating design systems from tokens,
 * with options for different output formats and customization.
 * Uses the canonical DesignSystemDocument model + emitCss/emitJson/emitTailwind/emitReactTheme.
 *
 * @doc.type component
 * @doc.purpose DS generation workflow UI
 * @doc.layer studio
 */

import { useState, useMemo } from 'react';
import {
  createDesignSystemDocument,
  emitCss,
  emitJson,
  emitTailwind,
  emitReactTheme,
  materializePreset,
  type DesignSystemPreset,
} from '@ghatana/ds-generator';
import { PRESET_GHATANA_DEFAULT, PRESET_ENTERPRISE_NEUTRAL, PRESET_CREATIVE_BOLD } from '@ghatana/ds-generator';

type OutputFormat = 'css' | 'json' | 'tailwind' | 'react-theme';

const PRESETS = [
  { name: 'Ghatana Default', preset: PRESET_GHATANA_DEFAULT },
  { name: 'Enterprise Neutral', preset: PRESET_ENTERPRISE_NEUTRAL },
  { name: 'Creative Bold', preset: PRESET_CREATIVE_BOLD },
] as const;

const FORMAT_EXTENSIONS: Record<OutputFormat, string> = {
  css: 'css',
  json: 'json',
  tailwind: 'js',
  'react-theme': 'tsx',
};

export function DesignSystemPage() {
  const [selectedPresetIndex, setSelectedPresetIndex] = useState(0);
  const [outputFormat, setOutputFormat] = useState<OutputFormat>('css');
  const [generatedOutput, setGeneratedOutput] = useState<string>('');
  const [isGenerating, setIsGenerating] = useState(false);

  const selectedPreset: DesignSystemPreset = PRESETS[selectedPresetIndex]?.preset ?? PRESET_GHATANA_DEFAULT;
  const selectedPresetName: string = PRESETS[selectedPresetIndex]?.name ?? 'Ghatana Default';

  /** Materialized token map for the selected preset. */
  const materializedTokens = useMemo(() => materializePreset(selectedPreset), [selectedPreset]);

  /** Build a canonical DesignSystemDocument from the selected preset. */
  const doc = useMemo(
    () =>
      createDesignSystemDocument(
        selectedPreset.id,
        selectedPresetName,
        selectedPreset.id,
        materializedTokens as Record<string, unknown>,
      ),
    [selectedPreset, selectedPresetName, materializedTokens],
  );

  const handleGenerate = async (): Promise<void> => {
    setIsGenerating(true);

    // Simulate async generation (emit functions are sync but UI benefits from async boundary)
    await new Promise<void>((resolve) => setTimeout(resolve, 300));

    let output = '';
    switch (outputFormat) {
      case 'css':
        output = emitCss(doc);
        break;
      case 'json':
        output = emitJson(doc).json;
        break;
      case 'tailwind':
        output = emitTailwind(doc);
        break;
      case 'react-theme':
        output = emitReactTheme(doc);
        break;
    }

    setGeneratedOutput(output);
    setIsGenerating(false);
  };

  const handleDownload = (): void => {
    const ext = FORMAT_EXTENSIONS[outputFormat];
    const blob = new Blob([generatedOutput], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `design-system.${ext}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Design System Generator</h1>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Configuration Panel */}
        <div className="space-y-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">Configuration</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">Preset</label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={selectedPresetIndex}
                  onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                    setSelectedPresetIndex(Number(e.target.value))
                  }
                >
                  {PRESETS.map((preset, index) => (
                    <option key={preset.name} value={index}>
                      {preset.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">Output Format</label>
                <div className="flex gap-2 flex-wrap">
                  {(['css', 'json', 'tailwind', 'react-theme'] as const).map((format) => (
                    <button
                      key={format}
                      type="button"
                      onClick={() => setOutputFormat(format)}
                      className={`px-4 py-2 rounded ${
                        outputFormat === format
                          ? 'bg-blue-500 text-white'
                          : 'bg-gray-100 hover:bg-gray-200'
                      }`}
                    >
                      {format.toUpperCase()}
                    </button>
                  ))}
                </div>
              </div>

              <button
                type="button"
                onClick={() => void handleGenerate()}
                disabled={isGenerating}
                className="w-full bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600 disabled:opacity-50"
              >
                {isGenerating ? 'Generating...' : 'Generate Design System'}
              </button>
            </div>
          </div>

          {/* Generated Output */}
          {generatedOutput && (
            <div className="bg-white rounded-lg shadow p-6">
              <div className="flex justify-between items-center mb-4">
                <h2 className="text-lg font-semibold">Generated Output</h2>
                <button
                  type="button"
                  onClick={handleDownload}
                  className="bg-green-500 text-white px-4 py-2 rounded hover:bg-green-600 text-sm"
                >
                  Download
                </button>
              </div>
              <pre className="bg-gray-50 p-4 rounded overflow-auto max-h-96 text-sm">
                {generatedOutput}
              </pre>
            </div>
          )}
        </div>

        {/* Token Preview Panel */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">Token Preview</h2>
          <div className="space-y-4">
            <div>
              <h3 className="font-medium mb-2">Colors</h3>
              <div className="grid grid-cols-4 gap-2">
                {Object.entries(selectedPreset.colors).slice(0, 8).map(([key, value]) => (
                  <div key={key} className="text-center">
                    <div
                      className="w-full h-12 rounded border"
                      style={{ backgroundColor: value as string }}
                    />
                    <span className="text-xs mt-1 block truncate">{key}</span>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h3 className="font-medium mb-2">Spacing</h3>
              <div className="flex gap-2 flex-wrap">
                {Object.entries(materializedTokens.spacing).slice(0, 6).map(([key, value]) => (
                  <div key={key} className="text-sm bg-gray-100 px-2 py-1 rounded">
                    {key}: {value}px
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h3 className="font-medium mb-2">Typography</h3>
              <div className="text-sm space-y-1">
                <p>Font: {selectedPreset.typography.fontFamily}</p>
                <p>Base Size: {selectedPreset.typography.baseFontSize}px</p>
              </div>
            </div>

            <div>
              <h3 className="font-medium mb-2">Document Info</h3>
              <div className="text-sm space-y-1 text-gray-600">
                <p>Schema: {doc.schemaVersion}</p>
                <p>Document ID: <code className="text-xs">{doc.documentId.slice(0, 12)}…</code></p>
                <p>Tokens: {Object.keys(doc.resolvedTokens).length} categories</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

