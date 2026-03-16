import React, { useState, useCallback, useRef, useEffect } from "react";
import { Play, Square, RotateCcw, Plus, Trash2, Settings2 } from "lucide-react";

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

type EntityShape = "circle" | "rectangle" | "triangle";

interface Vec2 {
  x: number;
  y: number;
}

interface SimulationEntity {
  id: string;
  name: string;
  shape: EntityShape;
  color: string;
  position: Vec2;
  velocity: Vec2;
  mass: number;
  radius: number;
  /** Whether this entity is affected by physics (gravity, collisions). */
  dynamic: boolean;
}

interface PhysicsParameters {
  gravityY: number;
  friction: number;
  restitution: number;
  /** Simulation speed multiplier. */
  timeScale: number;
}

interface SimulationGoal {
  id: string;
  description: string;
  /** The entity id this goal monitors. */
  entityId: string;
  type: "reach_position" | "avoid_zone" | "survive_duration" | "custom";
  targetPosition?: Vec2;
  durationMs?: number;
}

interface SimulationProject {
  id: string;
  name: string;
  description: string;
  entities: SimulationEntity[];
  physics: PhysicsParameters;
  goals: SimulationGoal[];
  canvasSize: { width: number; height: number };
}

type SimulationStatus = "idle" | "running" | "paused" | "completed" | "failed";

// ---------------------------------------------------------------------------
// Defaults
// ---------------------------------------------------------------------------

const INITIAL_PROJECT: SimulationProject = {
  id: "sim-1",
  name: "Gravity & Collision Demo",
  description: "Demonstrates basic Newtonian physics for educational purposes.",
  canvasSize: { width: 600, height: 400 },
  physics: { gravityY: 9.8, friction: 0.02, restitution: 0.7, timeScale: 1 },
  entities: [
    {
      id: "e-ball1",
      name: "Ball A",
      shape: "circle",
      color: "#6366f1",
      position: { x: 120, y: 80 },
      velocity: { x: 80, y: 0 },
      mass: 1,
      radius: 20,
      dynamic: true,
    },
    {
      id: "e-ball2",
      name: "Ball B",
      shape: "circle",
      color: "#f59e0b",
      position: { x: 400, y: 80 },
      velocity: { x: -60, y: 20 },
      mass: 2,
      radius: 28,
      dynamic: true,
    },
    {
      id: "e-platform",
      name: "Floor",
      shape: "rectangle",
      color: "#4b5563",
      position: { x: 300, y: 380 },
      velocity: { x: 0, y: 0 },
      mass: 1000,
      radius: 0,
      dynamic: false,
    },
  ],
  goals: [
    {
      id: "g-1",
      description: "Keep Ball A in bounds for 5 seconds",
      entityId: "e-ball1",
      type: "survive_duration",
      durationMs: 5000,
    },
  ],
};

// ---------------------------------------------------------------------------
// Mini physics engine (for canvas preview)
// ---------------------------------------------------------------------------

function stepPhysics(
  entities: SimulationEntity[],
  physics: PhysicsParameters,
  dt: number,
  canvas: { width: number; height: number },
): SimulationEntity[] {
  const scale = physics.timeScale;
  return entities.map((e): SimulationEntity => {
    if (!e.dynamic) return e;

    const vy = e.velocity.y + physics.gravityY * dt * scale * 60;
    const vx = e.velocity.x * (1 - physics.friction * dt * scale * 60);

    let nx = e.position.x + vx * dt * scale;
    let ny = e.position.y + vy * dt * scale;
    let nvx = vx;
    let nvy = vy;

    // Bounce off walls
    if (nx - e.radius < 0) { nx = e.radius; nvx = Math.abs(nvx) * physics.restitution; }
    if (nx + e.radius > canvas.width) { nx = canvas.width - e.radius; nvx = -Math.abs(nvx) * physics.restitution; }
    if (ny + e.radius > canvas.height) { ny = canvas.height - e.radius; nvy = -Math.abs(nvy) * physics.restitution; }
    if (ny - e.radius < 0) { ny = e.radius; nvy = Math.abs(nvy) * physics.restitution; }

    return { ...e, position: { x: nx, y: ny }, velocity: { x: nvx, y: nvy } };
  });
}

// ---------------------------------------------------------------------------
// Canvas renderer
// ---------------------------------------------------------------------------

function SimCanvas({
  entities,
  canvasSize,
}: {
  entities: SimulationEntity[];
  canvasSize: { width: number; height: number };
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, canvasSize.width, canvasSize.height);
    ctx.fillStyle = "#0f172a";
    ctx.fillRect(0, 0, canvasSize.width, canvasSize.height);

    // Grid
    ctx.strokeStyle = "rgba(255,255,255,0.04)";
    ctx.lineWidth = 1;
    for (let x = 0; x < canvasSize.width; x += 50) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, canvasSize.height); ctx.stroke();
    }
    for (let y = 0; y < canvasSize.height; y += 50) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(canvasSize.width, y); ctx.stroke();
    }

    for (const e of entities) {
      ctx.fillStyle = e.color;
      ctx.beginPath();
      if (e.shape === "circle") {
        ctx.arc(e.position.x, e.position.y, e.radius, 0, Math.PI * 2);
      } else {
        ctx.rect(e.position.x - 100, e.position.y - 8, 200, 16);
      }
      ctx.fill();

      // Label
      ctx.fillStyle = "rgba(255,255,255,0.7)";
      ctx.font = "10px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(e.name, e.position.x, e.position.y - e.radius - 4);
    }
  }, [entities, canvasSize]);

  return (
    <canvas
      ref={canvasRef}
      width={canvasSize.width}
      height={canvasSize.height}
      className="rounded-lg w-full"
      aria-label="Simulation canvas"
      role="img"
    />
  );
}

// ---------------------------------------------------------------------------
// Entity editor panel
// ---------------------------------------------------------------------------

function EntityPanel({
  entities,
  selectedId,
  onSelect,
  onAdd,
  onDelete,
  onUpdate,
}: {
  entities: SimulationEntity[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onAdd: () => void;
  onDelete: (id: string) => void;
  onUpdate: (entity: SimulationEntity) => void;
}) {
  const selected = entities.find((e) => e.id === selectedId) ?? null;

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-3 py-2 border-b border-gray-100 dark:border-gray-700">
        <span className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Entities</span>
        <button onClick={onAdd} className="text-indigo-600 dark:text-indigo-400 text-xs flex items-center gap-1">
          <Plus className="h-3.5 w-3.5" />
          Add
        </button>
      </div>
      <ul className="border-b border-gray-100 dark:border-gray-700">
        {entities.map((e) => (
          <li
            key={e.id}
            onClick={() => onSelect(e.id)}
            className={`flex items-center gap-2 px-3 py-2 text-xs cursor-pointer ${
              e.id === selectedId
                ? "bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-300"
                : "hover:bg-gray-50 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300"
            }`}
          >
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: e.color }} />
            <span className="flex-1 truncate">{e.name}</span>
            <button
              onClick={(ev) => { ev.stopPropagation(); onDelete(e.id); }}
              className="text-gray-300 hover:text-red-500"
              aria-label={`Delete ${e.name}`}
            >
              <Trash2 className="h-3 w-3" />
            </button>
          </li>
        ))}
      </ul>

      {selected && (
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {(["x", "y"] as const).map((axis) => (
            <label key={axis} className="flex flex-col gap-0.5">
              <span className="text-xs text-gray-500 dark:text-gray-400">Position {axis.toUpperCase()}</span>
              <input
                type="number"
                value={selected.position[axis]}
                onChange={(e) =>
                  onUpdate({ ...selected, position: { ...selected.position, [axis]: Number(e.target.value) } })
                }
                className="w-full px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200"
              />
            </label>
          ))}
          <label className="flex flex-col gap-0.5">
            <span className="text-xs text-gray-500 dark:text-gray-400">Mass (kg)</span>
            <input
              type="number"
              value={selected.mass}
              min={0.1}
              step={0.1}
              onChange={(e) => onUpdate({ ...selected, mass: Number(e.target.value) })}
              className="w-full px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200"
            />
          </label>
          <label className="flex items-center gap-2 text-xs text-gray-600 dark:text-gray-400">
            <input
              type="checkbox"
              checked={selected.dynamic}
              onChange={(e) => onUpdate({ ...selected, dynamic: e.target.checked })}
            />
            Dynamic (affected by physics)
          </label>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Physics parameters panel
// ---------------------------------------------------------------------------

function PhysicsPanel({
  params,
  onChange,
}: {
  params: PhysicsParameters;
  onChange: (p: PhysicsParameters) => void;
}) {
  return (
    <div className="p-3 space-y-2">
      <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
        Physics
      </div>
      {(
        [
          { key: "gravityY", label: "Gravity (m/s²)", step: 0.1, min: -20, max: 20 },
          { key: "friction", label: "Friction", step: 0.01, min: 0, max: 1 },
          { key: "restitution", label: "Restitution (bounciness)", step: 0.05, min: 0, max: 1 },
          { key: "timeScale", label: "Time Scale", step: 0.1, min: 0.1, max: 5 },
        ] as const
      ).map(({ key, label, step, min, max }) => (
        <label key={key} className="flex flex-col gap-0.5">
          <span className="text-xs text-gray-500 dark:text-gray-400">{label}</span>
          <input
            type="number"
            value={params[key]}
            step={step}
            min={min}
            max={max}
            onChange={(e) => onChange({ ...params, [key]: Number(e.target.value) })}
            className="w-full px-2 py-1 text-xs border border-gray-200 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200"
          />
        </label>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

/**
 * SimulationAuthoringPage — Phase 2 interactive physics simulation authoring
 * tool for Tutorputor educational content creation.
 *
 * Allows educators to place entities, configure physics parameters, define
 * educational goals, and preview simulations in real time.
 */
export function SimulationAuthoringPage(): React.ReactElement {
  const [project, setProject] = useState<SimulationProject>(INITIAL_PROJECT);
  const [runtimeEntities, setRuntimeEntities] = useState<SimulationEntity[]>(project.entities);
  const [status, setStatus] = useState<SimulationStatus>("idle");
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"entities" | "physics" | "goals">("entities");
  const [elapsedMs, setElapsedMs] = useState(0);
  const rafRef = useRef<number | undefined>(undefined);
  const lastTimeRef = useRef<number | undefined>(undefined);

  const runStep = useCallback(
    (now: number) => {
      const dt = lastTimeRef.current !== undefined ? (now - lastTimeRef.current) / 1000 : 0;
      lastTimeRef.current = now;
      setElapsedMs((p) => p + dt * 1000);
      setRuntimeEntities((prev) =>
        stepPhysics(prev, project.physics, dt, project.canvasSize),
      );
      rafRef.current = requestAnimationFrame(runStep);
    },
    [project.physics, project.canvasSize],
  );

  const start = useCallback(() => {
    if (status === "idle" || status === "paused") {
      lastTimeRef.current = undefined;
      setStatus("running");
      rafRef.current = requestAnimationFrame(runStep);
    }
  }, [status, runStep]);

  const pause = useCallback(() => {
    if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current);
    setStatus("paused");
  }, []);

  const reset = useCallback(() => {
    if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current);
    setRuntimeEntities(project.entities);
    setElapsedMs(0);
    setStatus("idle");
  }, [project.entities]);

  useEffect(() => {
    return () => { if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current); };
  }, []);

  const addEntity = useCallback(() => {
    const colors = ["#6366f1", "#f59e0b", "#10b981", "#ec4899", "#3b82f6", "#8b5cf6"];
    const newEntity: SimulationEntity = {
      id: `e-${Date.now()}`,
      name: `Entity ${project.entities.length + 1}`,
      shape: "circle",
      color: colors[project.entities.length % colors.length] ?? "#6366f1",
      position: { x: project.canvasSize.width / 2, y: 60 },
      velocity: { x: 50, y: 0 },
      mass: 1,
      radius: 18,
      dynamic: true,
    };
    setProject((p) => ({ ...p, entities: [...p.entities, newEntity] }));
    setRuntimeEntities((p) => [...p, newEntity]);
  }, [project.entities.length, project.canvasSize]);

  const deleteEntity = useCallback((id: string) => {
    setProject((p) => ({ ...p, entities: p.entities.filter((e) => e.id !== id) }));
    setRuntimeEntities((p) => p.filter((e) => e.id !== id));
    if (selectedEntityId === id) setSelectedEntityId(null);
  }, [selectedEntityId]);

  const updateEntity = useCallback((entity: SimulationEntity) => {
    setProject((p) => ({
      ...p,
      entities: p.entities.map((e) => (e.id === entity.id ? entity : e)),
    }));
    if (status === "idle") {
      setRuntimeEntities((p) => p.map((e) => (e.id === entity.id ? entity : e)));
    }
  }, [status]);

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50 dark:bg-gray-950">
      {/* Toolbar */}
      <header className="flex items-center gap-3 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-4 py-2">
        <h1 className="text-sm font-semibold text-gray-800 dark:text-gray-200">
          {project.name}
        </h1>
        <span className="text-xs text-gray-400 dark:text-gray-500 ml-1">
          {(elapsedMs / 1000).toFixed(2)}s
        </span>

        <div className="flex items-center gap-1 ml-auto">
          {status === "running" ? (
            <button
              onClick={pause}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-amber-500 hover:bg-amber-600 text-white text-xs font-medium"
            >
              <Square className="h-3 w-3" />
              Pause
            </button>
          ) : (
            <button
              onClick={start}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-medium"
            >
              <Play className="h-3 w-3" />
              {status === "paused" ? "Resume" : "Run"}
            </button>
          )}
          <button
            onClick={reset}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800"
            aria-label="Reset simulation"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            Reset
          </button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Left panel: entity/physics/goal controls */}
        <aside className="flex w-60 flex-shrink-0 flex-col border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 overflow-hidden">
          {/* Tab bar */}
          <div className="flex border-b border-gray-100 dark:border-gray-700">
            {(["entities", "physics", "goals"] as const).map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`flex-1 py-2 text-xs font-medium capitalize transition-colors ${
                  activeTab === tab
                    ? "border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400"
                    : "text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
                }`}
              >
                {tab}
              </button>
            ))}
          </div>

          <div className="flex-1 overflow-y-auto">
            {activeTab === "entities" && (
              <EntityPanel
                entities={project.entities}
                selectedId={selectedEntityId}
                onSelect={setSelectedEntityId}
                onAdd={addEntity}
                onDelete={deleteEntity}
                onUpdate={updateEntity}
              />
            )}
            {activeTab === "physics" && (
              <PhysicsPanel
                params={project.physics}
                onChange={(p) => setProject((proj) => ({ ...proj, physics: p }))}
              />
            )}
            {activeTab === "goals" && (
              <div className="p-3 space-y-2">
                <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">
                  Goals
                </div>
                {project.goals.map((g) => (
                  <div key={g.id} className="text-xs text-gray-700 dark:text-gray-300 border border-gray-100 dark:border-gray-700 rounded p-2">
                    <div className="font-medium">{g.type.replace(/_/g, " ")}</div>
                    <div className="text-gray-500 dark:text-gray-400 mt-0.5">{g.description}</div>
                  </div>
                ))}
                <button className="flex items-center gap-1 text-xs text-indigo-600 dark:text-indigo-400 mt-1">
                  <Plus className="h-3.5 w-3.5" />
                  Add goal
                </button>
              </div>
            )}
          </div>
        </aside>

        {/* Main canvas */}
        <main className="flex flex-1 items-start justify-center p-6 overflow-auto">
          <div className="flex flex-col gap-3 w-full max-w-2xl">
            <SimCanvas entities={runtimeEntities} canvasSize={project.canvasSize} />

            <div className="flex items-center gap-2">
              <span
                className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  status === "running"
                    ? "bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400"
                    : status === "paused"
                      ? "bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400"
                      : "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400"
                }`}
              >
                {status.toUpperCase()}
              </span>
              <span className="text-xs text-gray-400 dark:text-gray-500">
                {runtimeEntities.filter((e) => e.dynamic).length} dynamic{" "}
                / {runtimeEntities.length} total entities
              </span>
              <Settings2 className="h-3.5 w-3.5 text-gray-400 ml-auto" aria-hidden />
            </div>

            <div className="text-xs text-gray-500 dark:text-gray-400 bg-white dark:bg-gray-900 rounded-lg p-3 border border-gray-200 dark:border-gray-700">
              <div className="font-medium text-gray-700 dark:text-gray-300 mb-1">Description</div>
              {project.description}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
