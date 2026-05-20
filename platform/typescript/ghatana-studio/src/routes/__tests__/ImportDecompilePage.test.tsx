/**
 * @fileoverview Tests for ImportDecompilePage.
 *
 * Verifies that:
 * - The page renders a file upload input and heading
 * - Invalid file types are rejected with validation errors
 * - Files exceeding 1 MB are rejected
 * - A successful decompile run sets the workflow store and shows action buttons
 * - A failed decompile run shows an error alert
 * - The workflow action buttons navigate to the correct routes
 * - File input accepts .ts and .tsx only
 *
 * The compiler is dynamically imported inside ImportDecompilePage, so we
 * mock the entire `@ghatana/artifact-compiler-ts` module to keep the test
 * synchronous and deterministic.
 *
 * @doc.type test
 * @doc.purpose Import/decompile workflow page tests
 * @doc.layer studio
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'jotai';
import { createStore } from 'jotai';
import {
  artifactWorkflowAtom,
  hasArtifactWorkflowResultAtom,
} from '../../state/artifactWorkflowStore.js';
import { createLogicalArtifactModel, createPerfectFidelityReport, createResidualIslandReport } from '@ghatana/artifact-contracts';

// ============================================================================
// Mocks
// ============================================================================

const navigateMock = vi.fn();
vi.mock('react-router', () => ({
  useNavigate: () => navigateMock,
}));

// Create a minimal LogicalArtifactModel for the mock compiler result
const MOCK_MODEL = createLogicalArtifactModel('model-test', 'test-import');
const MOCK_FIDELITY = createPerfectFidelityReport('model-test');
const MOCK_RESIDUALS = createResidualIslandReport([]);

// Mock the compiler module that is dynamically imported
vi.mock('@ghatana/artifact-compiler-ts', () => ({
  decompileTsx: vi.fn(() => ({
    model: MOCK_MODEL,
    fidelityReport: MOCK_FIDELITY,
  })),
  detectResidualIslands: vi.fn(() => MOCK_RESIDUALS),
  compileReact: vi.fn(() => ({
    emittedFiles: [{ relativePath: 'out.tsx', content: '<p>generated</p>' }],
    overallFidelity: MOCK_FIDELITY,
  })),
  buildRoundTripDiffReport: vi.fn(() => ({
    reportId: 'round-trip:test',
    modelId: 'model-test',
    diffs: [],
    fidelity: MOCK_FIDELITY,
    residuals: MOCK_RESIDUALS,
    isLossless: true,
    generatedAt: new Date().toISOString(),
  })),
}));

// Mock the ModelToBuilderAdapter dynamic import
vi.mock('../../adapters/ModelToBuilderAdapter.js', () => ({
  projectModelToBuilderDocument: vi.fn((_model: unknown) => ({
    documentId: 'mock-doc-id',
    owner: 'mock-projection',
    schemaVersion: '1.0.0',
    root: 'root-id',
    nodes: {
      'root-id': {
        id: 'root-id',
        contractName: 'RootContainer',
        props: {},
        slots: {},
        bindings: [],
        metadata: { name: 'Root', locked: false, hidden: false },
      },
    },
    bindings: [],
    layout: {
      type: 'flex',
      rootId: 'root-id',
      nodes: { 'root-id': { id: 'root-id', type: 'root', children: [], layout: 'flex' } },
    },
    metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
  })),
}));

import ImportDecompilePage from '../ImportDecompilePage';

// ============================================================================
// Helpers
// ============================================================================

function makeFile(name: string, content: string, type = 'text/plain'): File {
  return new File([content], name, { type });
}

/**
 * Reliably simulate file input change in jsdom.
 * `userEvent.upload` does not trigger React's synthetic onChange in jsdom;
 * setting files via Object.defineProperty + fireEvent.change does.
 */
function uploadFiles(input: HTMLElement, files: File[]): void {
  Object.defineProperty(input, 'files', { value: files, configurable: true });
  fireEvent.change(input);
}

function renderWithStore(store = createStore()) {
  return { store, ...render(
    <Provider store={store}>
      <ImportDecompilePage />
    </Provider>,
  )};
}

// ============================================================================
// Tests
// ============================================================================

describe('ImportDecompilePage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
  });

  describe('initial rendering', () => {
    it('renders the page heading', () => {
      renderWithStore();
      expect(screen.getByRole('heading', { name: /Import/i })).toBeInTheDocument();
    });

    it('renders a file upload input accepting .ts and .tsx', () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i) as HTMLInputElement;
      expect(input).toBeInTheDocument();
      expect(input.getAttribute('accept')).toBe('.ts,.tsx');
      expect(input.getAttribute('multiple')).toBeDefined();
    });

    it('renders a provider selector', () => {
      renderWithStore();
      expect(screen.getByLabelText(/Source provider/i)).toBeInTheDocument();
    });

    it('does not show any action buttons before a job has run', () => {
      renderWithStore();
      expect(screen.queryByRole('button', { name: /Open in Canvas/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Open in Builder/i })).not.toBeInTheDocument();
    });
  });

  describe('file validation', () => {
    it('shows a validation error for non-.ts/.tsx files', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      const badFile = makeFile('index.js', 'const x = 1;');

      uploadFiles(input, [badFile]);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(/only .ts and .tsx files/i)).toBeInTheDocument();
      });
    });

    it('shows a validation error for files exceeding 1 MB', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      // Create a file just over 1 MB
      const bigContent = 'x'.repeat(1_000_001);
      const bigFile = makeFile('big.ts', bigContent);

      uploadFiles(input, [bigFile]);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(/exceeds 1 MB/i)).toBeInTheDocument();
      });
    });

    it('does not show errors for valid .ts files within the size limit', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      const validFile = makeFile('Button.tsx', 'export const Button = () => null;');

      uploadFiles(input, [validFile]);

      // Wait for any async processing to settle
      await waitFor(() => {
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      });
    });
  });

  describe('successful decompile', () => {
    it('sets the workflow store after a successful decompile', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      const validFile = makeFile('Button.tsx', 'export const Button = () => null;');

      uploadFiles(input, [validFile]);

      await waitFor(() => {
        expect(store.get(hasArtifactWorkflowResultAtom)).toBe(true);
      });
    });

    it('persists the logical model into the workflow store', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        const state = store.get(artifactWorkflowAtom);
        expect(state.model).not.toBeNull();
      });
    });

    it('persists the fidelity report into the workflow store', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        const state = store.get(artifactWorkflowAtom);
        expect(state.fidelityReport).not.toBeNull();
      });
    });

    it('persists the projected BuilderDocument into the workflow store', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        const state = store.get(artifactWorkflowAtom);
        expect(state.projectedBuilderDocument).not.toBeNull();
      });
    });

    it('persists the preview source into the workflow store', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        const state = store.get(artifactWorkflowAtom);
        expect(state.previewSource).not.toBeNull();
      });
    });

    it('persists an evidence pack into the workflow store', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        const state = store.get(artifactWorkflowAtom);
        expect(state.evidencePack).not.toBeNull();
        expect(state.evidencePack?.stage).toBe('round-trip');
      });
    });

    it('persists a round-trip diff report into the workflow store', async () => {
      const { store } = renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        const state = store.get(artifactWorkflowAtom);
        expect(state.roundTripDiffReport).not.toBeNull();
        expect(state.roundTripDiffReport?.reportId).toBe('round-trip:test');
      });
    });

    it('shows the Open in Canvas and Open in Builder buttons after success', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Open in Canvas/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Open in Builder/i })).toBeInTheDocument();
      });
    });

    it('shows the View Fidelity Report button after a successful job', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);

      uploadFiles(input, [makeFile('App.tsx', 'export default function App() { return null; }')]);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /View Fidelity Report/i })).toBeInTheDocument();
      });
    });

    it('can decompile pasted source through the provider registry', async () => {
      const { store } = renderWithStore();

      await userEvent.selectOptions(screen.getByLabelText(/Source provider/i), 'paste');
      await userEvent.clear(screen.getByLabelText(/Source path/i));
      await userEvent.type(screen.getByLabelText(/Source path/i), 'src/Pasted.tsx');
      fireEvent.change(screen.getByLabelText(/Source content/i), {
        target: { value: 'export function Pasted() { return <div>Pasted</div>; }' },
      });
      await userEvent.click(screen.getByRole('button', { name: /Decompile pasted source/i }));

      await waitFor(() => {
        expect(store.get(hasArtifactWorkflowResultAtom)).toBe(true);
      });
    });

    it('surfaces repository scanner boundary errors for repository providers', async () => {
      renderWithStore();

      await userEvent.selectOptions(screen.getByLabelText(/Source provider/i), 'github-repository');
      await userEvent.type(screen.getByLabelText(/Repository URL/i), 'https://github.com/example/repo');
      await userEvent.click(screen.getByRole('button', { name: /Start repository acquisition/i }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(/requires backend acquisition job/i)).toBeInTheDocument();
      });
    });
  });

  describe('navigation from action buttons', () => {
    it('clicking Open in Canvas navigates to /canvas', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      uploadFiles(input, [makeFile('A.tsx', 'export default function A() { return null; }')]);

      await waitFor(() => screen.getByRole('button', { name: /Open in Canvas/i }));
      await userEvent.click(screen.getByRole('button', { name: /Open in Canvas/i }));
      expect(navigateMock).toHaveBeenCalledWith('/canvas');
    });

    it('clicking Open in Builder navigates to /builder', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      uploadFiles(input, [makeFile('A.tsx', 'export default function A() { return null; }')]);

      await waitFor(() => screen.getByRole('button', { name: /Open in Builder/i }));
      await userEvent.click(screen.getByRole('button', { name: /Open in Builder/i }));
      expect(navigateMock).toHaveBeenCalledWith('/builder');
    });

    it('clicking View Fidelity Report navigates to /fidelity-report', async () => {
      renderWithStore();
      const input = screen.getByLabelText(/Select source files/i);
      uploadFiles(input, [makeFile('A.tsx', 'export default function A() { return null; }')]);

      await waitFor(() => screen.getByRole('button', { name: /View Fidelity Report/i }));
      await userEvent.click(screen.getByRole('button', { name: /View Fidelity Report/i }));
      expect(navigateMock).toHaveBeenCalledWith('/fidelity-report');
    });
  });
});
