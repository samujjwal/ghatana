import React, { useState, useCallback, useRef } from "react";
import { Play, Square, Plus, Trash2, ChevronLeft, ChevronRight, Download } from "lucide-react";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface Keyframe {
  id: string;
  timeMs: number;
  properties: Record<string, number | string>;
}

interface AnimationLayer {
  id: string;
  name: string;
  type: "shape" | "text" | "image" | "group";
  visible: boolean;
  locked: boolean;
  keyframes: Keyframe[];
  color: string;
}

type EasingFunction =
  | "linear"
  | "ease-in"
  | "ease-out"
  | "ease-in-out"
  | "bounce"
  | "elastic";

interface AnimationProject {
  id: string;
  name: string;
  durationMs: number;
  fps: number;
  layers: AnimationLayer[];
}

// ---------------------------------------------------------------------------
// Default state
// ---------------------------------------------------------------------------

const DEFAULT_PROJECT: AnimationProject = {
  id: "proj-1",
  name: "Untitled Animation",
  durationMs: 5000,
  fps: 60,
  layers: [
    {
      id: "layer-1",
      name: "Background",
      type: "shape",
      visible: true,
      locked: true,
      keyframes: [
        { id: "kf-1", timeMs: 0, properties: { opacity: 1, x: 0, y: 0 } },
        { id: "kf-2", timeMs: 5000, properties: { opacity: 1, x: 0, y: 0 } },
      ],
      color: "#6366f1",
    },
    {
      id: "layer-2",
      name: "Title Text",
      type: "text",
      visible: true,
      locked: false,
      keyframes: [
        { id: "kf-3", timeMs: 0, properties: { opacity: 0, x: 0, y: -40, scale: 0.8 } },
        { id: "kf-4", timeMs: 800, properties: { opacity: 1, x: 0, y: 0, scale: 1 } },
      ],
      color: "#f59e0b",
    },
    {
      id: "layer-3",
      name: "Illustration",
      type: "image",
      visible: true,
      locked: false,
      keyframes: [
        { id: "kf-5", timeMs: 400, properties: { opacity: 0, scale: 0.5, rotation: -15 } },
        { id: "kf-6", timeMs: 1200, properties: { opacity: 1, scale: 1, rotation: 0 } },
      ],
      color: "#10b981",
    },
  ],
};

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function LayerRow({
  layer,
  isSelected,
  durationMs,
  onSelect,
  onToggleVisible,
  onToggleLock,
  onDelete,
}: {
  layer: AnimationLayer;
  isSelected: boolean;
  durationMs: number;
  onSelect: () => void;
  onToggleVisible: () => void;
  onToggleLock: () => void;
  onDelete: () => void;
}) {
  return (
    <div
      role="row"
      aria-selected={isSelected}
      onClick={onSelect}
      className={`flex items-center border-b border-gray-100 dark:border-gray-700 cursor-pointer select-none group ${
        isSelected ? "bg-indigo-50 dark:bg-indigo-900/20" : "hover:bg-gray-50 dark:hover:bg-gray-800"
      }`}
    >
      {/* Layer info column */}
      <div className="flex items-center gap-2 px-3 py-2 w-52 flex-shrink-0 border-r border-gray-200 dark:border-gray-700">
        <span
          className="w-2.5 h-2.5 rounded-full flex-shrink-0"
          style={{ backgroundColor: layer.color }}
        />
        <span className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate flex-1">
          {layer.name}
        </span>
        <button
          onClick={(e) => { e.stopPropagation(); onToggleVisible(); }}
          className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 text-xs"
          aria-label={layer.visible ? "Hide layer" : "Show layer"}
          title={layer.visible ? "Hide" : "Show"}
        >
          {layer.visible ? "👁" : "🙈"}
        </button>
        <button
          onClick={(e) => { e.stopPropagation(); onDelete(); }}
          className="text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100 transition text-xs"
          aria-label="Delete layer"
        >
          <Trash2 className="h-3 w-3" />
        </button>
      </div>

      {/* Timeline track — keyframes as dots */}
      <div className="relative flex-1 h-9 overflow-hidden px-2">
        {layer.keyframes.map((kf) => {
          const pct = (kf.timeMs / durationMs) * 100;
          return (
            <div
              key={kf.id}
              className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full border-2 border-white dark:border-gray-900 shadow cursor-pointer hover:scale-125 transition-transform"
              style={{ left: `${pct}%`, backgroundColor: layer.color }}
              title={`${kf.timeMs}ms`}
            />
          );
        })}
        {/* Connecting line */}
        {layer.keyframes.length >= 2 && (
          <div
            className="absolute top-1/2 -translate-y-1/2 h-px opacity-30"
            style={{
              backgroundColor: layer.color,
              left: `${(Math.min(...layer.keyframes.map((k) => k.timeMs)) / durationMs) * 100}%`,
              width: `${((Math.max(...layer.keyframes.map((k) => k.timeMs)) - Math.min(...layer.keyframes.map((k) => k.timeMs))) / durationMs) * 100}%`,
            }}
          />
        )}
      </div>
    </div>
  );
}

function PropertyEditor({
  layer,
  currentMs,
}: {
  layer: AnimationLayer | null;
  currentMs: number;
}) {
  if (!layer) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-gray-400 dark:text-gray-500 p-4">
        Select a layer to edit properties.
      </div>
    );
  }

  // Interpolate property value at currentMs
  const sorted = [...layer.keyframes].sort((a, b) => a.timeMs - b.timeMs);
  const prev = [...sorted].reverse().find((k) => k.timeMs <= currentMs);
  const next = sorted.find((k) => k.timeMs > currentMs);
  const propsAtTime = prev?.properties ?? {};

  return (
    <div className="p-3 space-y-3">
      <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
        {layer.name} — {currentMs}ms
      </div>
      {Object.entries(propsAtTime).map(([key, val]) => (
        <label key={key} className="flex flex-col gap-1">
          <span className="text-xs text-gray-600 dark:text-gray-400 capitalize">{key}</span>
          <input
            type="number"
            defaultValue={typeof val === "number" ? val : 0}
            step={key === "opacity" ? 0.05 : 1}
            min={key === "opacity" ? 0 : undefined}
            max={key === "opacity" ? 1 : undefined}
            className="w-full px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200"
            aria-label={`${key} at ${currentMs}ms`}
          />
        </label>
      ))}
      <div>
        <span className="text-xs text-gray-600 dark:text-gray-400">Easing</span>
        <select className="mt-1 w-full px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200">
          {(["linear", "ease-in", "ease-out", "ease-in-out", "bounce", "elastic"] as EasingFunction[]).map(
            (e) => <option key={e} value={e}>{e}</option>
          )}
        </select>
      </div>
    </div>
  );
}

function PreviewCanvas({ project, currentMs }: { project: AnimationProject; currentMs: number }) {
  return (
    <div
      className="relative w-full aspect-video bg-gradient-to-br from-slate-900 to-slate-800 rounded-lg overflow-hidden flex items-center justify-center"
      aria-label="Animation preview"
    >
      {/* Simulated canvas preview */}
      {project.layers
        .filter((l) => l.visible)
        .map((layer) => {
          const sorted = [...layer.keyframes].sort((a, b) => a.timeMs - b.timeMs);
          const prev = [...sorted].reverse().find((k) => k.timeMs <= currentMs);
          const opacity = typeof prev?.properties.opacity === "number" ? prev.properties.opacity : 1;
          const scale = typeof prev?.properties.scale === "number" ? prev.properties.scale : 1;
          return (
            <div
              key={layer.id}
              className="absolute inset-0 flex items-center justify-center transition-all"
              style={{ opacity, transform: `scale(${scale})` }}
            >
              {layer.type === "text" && (
                <span className="text-white text-2xl font-bold tracking-tight">{layer.name}</span>
              )}
              {layer.type === "shape" && (
                <div className="w-16 h-16 rounded-xl" style={{ backgroundColor: layer.color }} />
              )}
            </div>
          );
        })}
      <div className="absolute bottom-2 right-2 text-xs text-white/50">
        {currentMs}ms / {project.durationMs}ms
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

/**
 * AnimationEditorPage — Phase 2 content creation tool for authoring
 * frame-by-frame educational animations within Tutorputor.
 *
 * Architecture:
 *  - Left panel: layer list + timeline
 *  - Center: canvas preview
 *  - Right panel: property editor + easing controls
 */
export function AnimationEditorPage(): React.ReactElement {
  const [project, setProject] = useState<AnimationProject>(DEFAULT_PROJECT);
  const [selectedLayerId, setSelectedLayerId] = useState<string | null>(null);
  const [currentMs, setCurrentMs] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const rafRef = useRef<number | undefined>(undefined);
  const lastTickRef = useRef<number | undefined>(undefined);

  const selectedLayer = project.layers.find((l) => l.id === selectedLayerId) ?? null;

  // Playback loop
  const tick = useCallback(
    (now: number) => {
      const delta = lastTickRef.current !== undefined ? now - lastTickRef.current : 0;
      lastTickRef.current = now;
      setCurrentMs((prev) => {
        const next = prev + delta;
        if (next >= project.durationMs) {
          setIsPlaying(false);
          return 0;
        }
        return next;
      });
      rafRef.current = requestAnimationFrame(tick);
    },
    [project.durationMs],
  );

  const togglePlay = useCallback(() => {
    setIsPlaying((prev) => {
      if (!prev) {
        lastTickRef.current = undefined;
        rafRef.current = requestAnimationFrame(tick);
      } else {
        if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current);
      }
      return !prev;
    });
  }, [tick]);

  const addLayer = useCallback(() => {
    const colors = ["#6366f1", "#f59e0b", "#10b981", "#ec4899", "#3b82f6"];
    const newLayer: AnimationLayer = {
      id: `layer-${Date.now()}`,
      name: `Layer ${project.layers.length + 1}`,
      type: "shape",
      visible: true,
      locked: false,
      keyframes: [
        { id: `kf-${Date.now()}-0`, timeMs: 0, properties: { opacity: 0 } },
        { id: `kf-${Date.now()}-1`, timeMs: project.durationMs, properties: { opacity: 1 } },
      ],
      color: colors[project.layers.length % colors.length] ?? "#6366f1",
    };
    setProject((p) => ({ ...p, layers: [...p.layers, newLayer] }));
  }, [project.layers.length, project.durationMs]);

  const deleteLayer = useCallback(
    (id: string) => {
      setProject((p) => ({ ...p, layers: p.layers.filter((l) => l.id !== id) }));
      if (selectedLayerId === id) setSelectedLayerId(null);
    },
    [selectedLayerId],
  );

  const toggleVisible = useCallback((id: string) => {
    setProject((p) => ({
      ...p,
      layers: p.layers.map((l) => (l.id === id ? { ...l, visible: !l.visible } : l)),
    }));
  }, []);

  React.useEffect(() => {
    return () => {
      if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50 dark:bg-gray-950">
      {/* Top toolbar */}
      <header className="flex items-center gap-3 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-4 py-2">
        <h1 className="text-sm font-semibold text-gray-800 dark:text-gray-200">
          {project.name}
        </h1>
        <div className="flex items-center gap-1 ml-auto">
          <button
            onClick={() => setCurrentMs((p) => Math.max(0, p - 100))}
            className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300"
            aria-label="Step back"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            onClick={togglePlay}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-medium"
            aria-label={isPlaying ? "Pause" : "Play"}
          >
            {isPlaying ? <Square className="h-3 w-3" /> : <Play className="h-3 w-3" />}
            {isPlaying ? "Pause" : "Play"}
          </button>
          <button
            onClick={() => setCurrentMs((p) => Math.min(project.durationMs, p + 100))}
            className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300"
            aria-label="Step forward"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
          <button
            className="flex items-center gap-1.5 ml-2 px-3 py-1.5 rounded border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800"
            aria-label="Export animation"
          >
            <Download className="h-3.5 w-3.5" />
            Export
          </button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Left: Layer list + Timeline */}
        <section
          className="flex flex-col w-[26rem] flex-shrink-0 border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900"
          aria-label="Layer timeline"
        >
          {/* Section header */}
          <div className="flex items-center justify-between px-3 py-2 border-b border-gray-100 dark:border-gray-700">
            <span className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
              Layers
            </span>
            <button
              onClick={addLayer}
              className="flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400"
              aria-label="Add layer"
            >
              <Plus className="h-3.5 w-3.5" />
              Add
            </button>
          </div>

          {/* Layer rows */}
          <div className="flex-1 overflow-y-auto" role="grid" aria-label="Layers">
            {project.layers.map((layer) => (
              <LayerRow
                key={layer.id}
                layer={layer}
                isSelected={layer.id === selectedLayerId}
                durationMs={project.durationMs}
                onSelect={() => setSelectedLayerId(layer.id)}
                onToggleVisible={() => toggleVisible(layer.id)}
                onToggleLock={() => {}}
                onDelete={() => deleteLayer(layer.id)}
              />
            ))}
            {project.layers.length === 0 && (
              <div className="flex h-24 items-center justify-center text-sm text-gray-400 dark:text-gray-500">
                No layers — add one to start.
              </div>
            )}
          </div>

          {/* Scrubber */}
          <div className="border-t border-gray-200 dark:border-gray-700 px-3 py-2">
            <input
              type="range"
              min={0}
              max={project.durationMs}
              value={currentMs}
              onChange={(e) => setCurrentMs(Number(e.target.value))}
              className="w-full accent-indigo-600"
              aria-label="Timeline scrubber"
            />
            <div className="flex justify-between text-xs text-gray-400 dark:text-gray-500 mt-0.5">
              <span>0s</span>
              <span>{currentMs}ms</span>
              <span>{project.durationMs / 1000}s</span>
            </div>
          </div>
        </section>

        {/* Center: Preview */}
        <main className="flex flex-1 flex-col gap-3 p-4 overflow-auto">
          <PreviewCanvas project={project} currentMs={currentMs} />

          {/* Duration / FPS controls */}
          <div className="flex gap-4">
            <label className="flex flex-col gap-1">
              <span className="text-xs text-gray-500 dark:text-gray-400">Duration (ms)</span>
              <input
                type="number"
                value={project.durationMs}
                step={500}
                min={500}
                onChange={(e) =>
                  setProject((p) => ({ ...p, durationMs: Number(e.target.value) }))
                }
                className="w-28 px-2 py-1 text-xs border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-gray-500 dark:text-gray-400">FPS</span>
              <select
                value={project.fps}
                onChange={(e) =>
                  setProject((p) => ({ ...p, fps: Number(e.target.value) }))
                }
                className="w-20 px-2 py-1 text-xs border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200"
              >
                {[24, 30, 60].map((fps) => (
                  <option key={fps} value={fps}>{fps} fps</option>
                ))}
              </select>
            </label>
          </div>
        </main>

        {/* Right: Property editor */}
        <aside
          className="flex w-56 flex-shrink-0 flex-col border-l border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900"
          aria-label="Property editor"
        >
          <div className="border-b border-gray-100 dark:border-gray-700 px-3 py-2">
            <span className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
              Properties
            </span>
          </div>
          <div className="flex-1 overflow-y-auto">
            <PropertyEditor layer={selectedLayer} currentMs={currentMs} />
          </div>
        </aside>
      </div>
    </div>
  );
}
