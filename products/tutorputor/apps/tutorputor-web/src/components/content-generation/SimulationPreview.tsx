/**
 * Simulation Preview Component
 *
 * Provides interactive preview of generated simulations, visual examples,
 * and animations to see the content generation system in action.
 *
 * @doc.type module
 * @doc.purpose Interactive preview of generated content
 * @doc.layer product
 * @doc.pattern Preview
 */

import React, { useState, useEffect, useCallback, useRef } from "react";

/**
 * Simulation Preview Component
 */
export const SimulationPreview: React.FC = () => {
  const [selectedDemo, setSelectedDemo] = useState<string>("pendulum");
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [simulationState, setSimulationState] = useState<any>({});
  const animationRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(0);

  const demos = [
    {
      id: "pendulum",
      name: "Pendulum Motion",
      domain: "Physics",
      description: "Interactive pendulum simulation with adjustable parameters",
      entities: [
        { id: "pivot", type: "fixed", x: 400, y: 100, color: "#666" },
        {
          id: "bob",
          type: "mass",
          x: 400,
          y: 300,
          vx: 0,
          vy: 0,
          mass: 1,
          color: "#4CAF50",
        },
        {
          id: "string",
          type: "connection",
          from: "pivot",
          to: "bob",
          color: "#999",
        },
      ],
      steps: [
        { time: 0, description: "Initial position", actions: [] },
        {
          time: 1000,
          description: "Release pendulum",
          actions: [{ type: "apply_gravity", entity: "bob" }],
        },
        {
          time: 2000,
          description: "Swinging motion",
          actions: [{ type: "update_velocity", entity: "bob", vx: 2 }],
        },
        {
          time: 3000,
          description: "Peak height",
          actions: [{ type: "update_velocity", entity: "bob", vx: 0, vy: -3 }],
        },
      ],
    },
    {
      id: "chemistry",
      name: "Chemical Reaction",
      domain: "Chemistry",
      description: "Acid-base titration visualization",
      entities: [
        {
          id: "burette",
          type: "container",
          x: 200,
          y: 100,
          volume: 50,
          color: "#2196F3",
        },
        {
          id: "flask",
          type: "container",
          x: 400,
          y: 200,
          volume: 100,
          color: "#F44336",
        },
        {
          id: "indicator",
          type: "indicator",
          x: 400,
          y: 200,
          color: "#FFC107",
        },
      ],
      steps: [
        { time: 0, description: "Initial setup", actions: [] },
        {
          time: 1000,
          description: "Add base solution",
          actions: [{ type: "add_solution", entity: "burette", amount: 10 }],
        },
        {
          time: 2000,
          description: "Mixing",
          actions: [{ type: "mix", entities: ["burette", "flask"] }],
        },
        {
          time: 3000,
          description: "Color change",
          actions: [
            { type: "change_color", entity: "indicator", color: "#4CAF50" },
          ],
        },
      ],
    },
    {
      id: "algorithm",
      name: "Sorting Algorithm",
      domain: "CS Discrete",
      description: "Visual bubble sort algorithm demonstration",
      entities: [
        {
          id: "array",
          type: "data_structure",
          x: 100,
          y: 200,
          elements: [5, 2, 8, 1, 9, 3],
        },
        {
          id: "pointer",
          type: "pointer",
          x: 100,
          y: 180,
          index: 0,
          color: "#FF5722",
        },
      ],
      steps: [
        { time: 0, description: "Initial array", actions: [] },
        {
          time: 500,
          description: "Compare elements",
          actions: [{ type: "compare", indices: [0, 1] }],
        },
        {
          time: 1000,
          description: "Swap elements",
          actions: [{ type: "swap", indices: [0, 1] }],
        },
        {
          time: 1500,
          description: "Move pointer",
          actions: [{ type: "move_pointer", index: 1 }],
        },
      ],
    },
  ];

  const currentDemo = demos.find((d) => d.id === selectedDemo)!;

  // Physics animation for pendulum
  const animatePendulum = useCallback(() => {
    if (selectedDemo !== "pendulum") return;

    const animate = (timestamp: number) => {
      if (!startTimeRef.current) {
        startTimeRef.current = timestamp;
      }

      const elapsed = (timestamp - startTimeRef.current) / 1000; // Convert to seconds

      // Pendulum physics
      const length = 200; // Pendulum length in pixels
      const gravity = 9.8;
      const angle0 = Math.PI / 6; // Initial angle (30 degrees)
      const omega = Math.sqrt(gravity / (length / 100)); // Angular frequency

      // Calculate current angle
      const angle = angle0 * Math.cos(omega * elapsed);

      // Calculate bob position
      const pivotX = 400;
      const pivotY = 100;
      const bobX = pivotX + length * Math.sin(angle);
      const bobY = pivotY + length * Math.cos(angle);

      setSimulationState({
        bob: {
          x: bobX,
          y: bobY,
          vx: length * omega * Math.cos(angle) * Math.cos(omega * elapsed),
          vy: -length * omega * Math.sin(angle) * Math.cos(omega * elapsed),
        },
      });

      if (isPlaying) {
        animationRef.current = requestAnimationFrame(animate);
      }
    };

    if (isPlaying) {
      animationRef.current = requestAnimationFrame(animate);
    }
  }, [selectedDemo, isPlaying]);

  // Chemistry animation for titration
  const animateChemistry = useCallback(() => {
    if (selectedDemo !== "chemistry") return;

    const animate = (timestamp: number) => {
      if (!startTimeRef.current) {
        startTimeRef.current = timestamp;
      }

      const elapsed = (timestamp - startTimeRef.current) / 1000; // Convert to seconds

      // Titration animation phases
      let phase = Math.floor(elapsed / 2) % 4; // Change phase every 2 seconds
      let progress = (elapsed % 2) / 2; // Progress within current phase

      let newState: any = {};

      switch (phase) {
        case 0: // Adding base solution
          newState = {
            burette: {
              volume: 50 + progress * 10, // Add 10ml over 2 seconds
              color: "#2196F3",
            },
            flask: {
              volume: 100,
              color: "#F44336",
            },
            indicator: {
              color: "#FFC107",
            },
          };
          break;

        case 1: // Mixing
          const mixProgress = progress;
          newState = {
            burette: {
              volume: 60,
              color: "#2196F3",
            },
            flask: {
              volume: 100 + mixProgress * 10, // Volume increases as base is added
              color: `rgb(244, 67, 54, ${1 - mixProgress * 0.3})`, // Fade red slightly
            },
            indicator: {
              color: "#FFC107",
            },
          };
          break;

        case 2: // Color change to neutral
          const colorProgress = progress;
          const r = Math.floor(255 * (1 - colorProgress) + 76 * colorProgress); // Red to Green
          const g = Math.floor(193 * (1 - colorProgress) + 175 * colorProgress); // Yellow to Green
          const b = Math.floor(7 * (1 - colorProgress) + 80 * colorProgress); // Yellow to Green

          newState = {
            burette: {
              volume: 60,
              color: "#2196F3",
            },
            flask: {
              volume: 110,
              color: `rgb(${r}, ${g}, ${b})`,
            },
            indicator: {
              color: `rgb(${r}, ${g}, ${b})`,
            },
          };
          break;

        case 3: // Final state
          newState = {
            burette: {
              volume: 60,
              color: "#2196F3",
            },
            flask: {
              volume: 110,
              color: "#4CAF50",
            },
            indicator: {
              color: "#4CAF50",
            },
          };
          break;
      }

      setSimulationState(newState);

      if (isPlaying) {
        animationRef.current = requestAnimationFrame(animate);
      }
    };

    if (isPlaying) {
      animationRef.current = requestAnimationFrame(animate);
    }
  }, [selectedDemo, isPlaying]);

  // Algorithm animation for sorting
  const animateAlgorithm = useCallback(() => {
    if (selectedDemo !== "algorithm") return;

    const animate = (timestamp: number) => {
      if (!startTimeRef.current) {
        startTimeRef.current = timestamp;
      }

      const elapsed = (timestamp - startTimeRef.current) / 1000; // Convert to seconds
      const step = Math.floor(elapsed / 1.5) % 4; // Change step every 1.5 seconds

      let newState: any = {
        array: {
          elements: [5, 2, 8, 1, 9, 3],
        },
      };

      switch (step) {
        case 0: // Initial state
          break;
        case 1: // Compare first two
          newState.pointer = { index: 0, x: 100 };
          break;
        case 2: // Swap first two
          newState.array.elements = [2, 5, 8, 1, 9, 3];
          newState.pointer = { index: 1, x: 160 };
          break;
        case 3: // Compare next pair
          newState.array.elements = [2, 5, 8, 1, 9, 3];
          newState.pointer = { index: 1, x: 160 };
          break;
      }

      setSimulationState(newState);

      if (isPlaying) {
        animationRef.current = requestAnimationFrame(animate);
      }
    };

    if (isPlaying) {
      animationRef.current = requestAnimationFrame(animate);
    }
  }, [selectedDemo, isPlaying]);
  // Start/stop animation
  useEffect(() => {
    if (isPlaying) {
      switch (selectedDemo) {
        case "pendulum":
          animatePendulum();
          break;
        case "chemistry":
          animateChemistry();
          break;
        case "algorithm":
          animateAlgorithm();
          break;
      }
    } else {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
        animationRef.current = null;
      }
    }

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [
    isPlaying,
    selectedDemo,
    animatePendulum,
    animateChemistry,
    animateAlgorithm,
  ]);

  const applyStep = useCallback(
    (step: any) => {
      const newState = { ...simulationState };

      step.actions.forEach((action: any) => {
        // Ensure the entity exists in state
        if (!newState[action.entity]) {
          const entity = currentDemo.entities.find(
            (e: any) => e.id === action.entity,
          );
          if (entity) {
            newState[action.entity] = { ...entity };
          } else {
            console.warn(`Entity ${action.entity} not found in demo`);
            return;
          }
        }

        switch (action.type) {
          case "apply_gravity":
            newState[action.entity] = { ...newState[action.entity], vy: 9.8 };
            break;
          case "update_velocity":
            newState[action.entity] = { ...newState[action.entity], ...action };
            break;
          case "add_solution":
            newState[action.entity] = {
              ...newState[action.entity],
              volume: (newState[action.entity].volume || 0) + action.amount,
            };
            break;
          case "change_color":
            newState[action.entity] = {
              ...newState[action.entity],
              color: action.color,
            };
            break;
          case "compare":
            newState.comparison = {
              indices: action.indices,
              result:
                (newState.array?.elements?.[action.indices[0]] || 0) >
                (newState.array?.elements?.[action.indices[1]] || 0),
            };
            break;
          case "swap":
            if (newState.array?.elements) {
              const elements = [...newState.array.elements];
              [elements[action.indices[0]], elements[action.indices[1]]] = [
                elements[action.indices[1]],
                elements[action.indices[0]],
              ];
              newState.array = { ...newState.array, elements };
            }
            break;
          case "move_pointer":
            newState.pointer = {
              ...newState.pointer,
              index: action.index,
              x: 100 + action.index * 60,
            };
            break;
          default:
            console.warn(`Unknown action type: ${action.type}`);
        }
      });

      setSimulationState(newState);
    },
    [currentDemo.entities, simulationState],
  );

  useEffect(() => {
    if (isPlaying && currentStep < currentDemo.steps.length - 1) {
      const timer = setTimeout(() => {
        setCurrentStep(currentStep + 1);
        applyStep(currentDemo.steps[currentStep + 1]);
      }, 1000);
      return () => clearTimeout(timer);
    } else if (isPlaying && currentStep === currentDemo.steps.length - 1) {
      setIsPlaying(false);
    }
  }, [isPlaying, currentStep, currentDemo, applyStep]);

  const reset = () => {
    setIsPlaying(false);
    setCurrentStep(0);
    setSimulationState({});
    startTimeRef.current = 0;
    if (animationRef.current) {
      cancelAnimationFrame(animationRef.current);
      animationRef.current = null;
    }
  };

  const play = () => {
    if (currentStep === currentDemo.steps.length - 1) {
      reset();
    }
    setIsPlaying(true);
  };

  const pause = () => {
    setIsPlaying(false);
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            Simulation Preview
          </h1>
          <p className="text-gray-600">
            Interactive preview of generated simulations and content
          </p>
        </div>

        {/* Demo Selector */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Select Demo</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {demos.map((demo) => (
              <button
                key={demo.id}
                onClick={() => {
                  setSelectedDemo(demo.id);
                  reset();
                }}
                className={`p-4 rounded-lg border-2 transition-colors ${
                  selectedDemo === demo.id
                    ? "border-blue-500 bg-blue-50"
                    : "border-gray-200 hover:border-gray-300"
                }`}
              >
                <h3 className="font-semibold text-gray-900">{demo.name}</h3>
                <p className="text-sm text-gray-600 mt-1">{demo.domain}</p>
                <p className="text-xs text-gray-500 mt-2">{demo.description}</p>
              </button>
            ))}
          </div>
        </div>

        {/* Simulation Canvas */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-900">
              {currentDemo.name} - {currentDemo.domain}
            </h2>
            <div className="flex items-center space-x-2">
              <button
                onClick={isPlaying ? pause : play}
                className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors"
              >
                {isPlaying ? "⏸️ Pause" : "▶️ Play"}
              </button>
              <button
                onClick={reset}
                className="px-4 py-2 bg-gray-500 text-white rounded-lg hover:bg-gray-600 transition-colors"
              >
                🔄 Reset
              </button>
            </div>
          </div>

          {/* Canvas */}
          <div className="relative bg-gray-100 rounded-lg h-96 mb-4 overflow-hidden border-2 border-gray-300">
            <svg
              width="100%"
              height="100%"
              viewBox="0 0 800 400"
              className="drop-shadow-md"
            >
              {/* Render entities */}
              {currentDemo.entities.map((entity) => {
                const state = simulationState[entity.id] || entity;

                if (entity.type === "fixed") {
                  return (
                    <circle
                      key={entity.id}
                      cx={entity.x}
                      cy={entity.y}
                      r="8"
                      fill={entity.color}
                    />
                  );
                } else if (entity.type === "mass") {
                  return (
                    <circle
                      key={entity.id}
                      cx={state.x || entity.x}
                      cy={state.y || entity.y}
                      r="15"
                      fill={entity.color}
                    />
                  );
                } else if (entity.type === "connection") {
                  const from =
                    simulationState[entity.from] ||
                    currentDemo.entities.find((e) => e.id === entity.from)!;
                  const to =
                    simulationState[entity.to] ||
                    currentDemo.entities.find((e) => e.id === entity.to)!;
                  return (
                    <line
                      key={entity.id}
                      x1={from.x}
                      y1={from.y}
                      x2={to.x}
                      y2={to.y}
                      stroke={entity.color}
                      strokeWidth="2"
                    />
                  );
                } else if (entity.type === "container") {
                  return (
                    <rect
                      key={entity.id}
                      x={entity.x - 30}
                      y={entity.y}
                      width="60"
                      height={state.volume || entity.volume}
                      fill={entity.color}
                      opacity="0.7"
                    />
                  );
                } else if (entity.type === "indicator") {
                  return (
                    <circle
                      key={entity.id}
                      cx={entity.x}
                      cy={entity.y}
                      r="5"
                      fill={state.color || entity.color}
                    />
                  );
                } else if (entity.type === "data_structure") {
                  return (
                    <g key={entity.id}>
                      <rect
                        x={entity.x}
                        y={entity.y}
                        width="360"
                        height="40"
                        fill="#E3F2FD"
                        stroke="#1976D2"
                        strokeWidth="2"
                      />
                      {(state.elements || entity.elements).map(
                        (value: number, index: number) => (
                          <text
                            key={index}
                            x={entity.x + 10 + index * 60}
                            y={entity.y + 25}
                            fontSize="16"
                            fill="#1976D2"
                            textAnchor="middle"
                          >
                            {value}
                          </text>
                        ),
                      )}
                    </g>
                  );
                } else if (entity.type === "pointer") {
                  return (
                    <polygon
                      key={entity.id}
                      points={`${state.x || entity.x},${state.y || entity.y + 10} ${state.x || entity.x + 5},${state.y || entity.y + 20} ${state.x || entity.x},${state.y || entity.y + 30}`}
                      fill={entity.color}
                    />
                  );
                }
                return null;
              })}

              {/* Step description */}
              <text
                x="400"
                y="30"
                textAnchor="middle"
                fontSize="16"
                fill="#333"
              >
                Step {currentStep + 1}:{" "}
                {currentDemo.steps[currentStep]?.description}
              </text>
            </svg>
          </div>

          {/* Step Progress */}
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              {currentDemo.steps.map((_, index) => (
                <div
                  key={index}
                  className={`w-3 h-3 rounded-full ${
                    index <= currentStep ? "bg-blue-500" : "bg-gray-300"
                  }`}
                />
              ))}
            </div>
            <div className="text-sm text-gray-600">
              {currentStep + 1} / {currentDemo.steps.length}
            </div>
          </div>
        </div>

        {/* Entity State */}
        <div className="bg-white rounded-lg shadow-md p-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Entity State</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {currentDemo.entities.map((entity) => {
              const state = simulationState[entity.id] || entity;
              return (
                <div key={entity.id} className="border rounded-lg p-3">
                  <h3 className="font-semibold text-gray-900">{entity.id}</h3>
                  <div className="text-sm text-gray-600 space-y-1">
                    <div>Type: {entity.type}</div>
                    {state.x !== undefined && <div>X: {state.x}</div>}
                    {state.y !== undefined && <div>Y: {state.y}</div>}
                    {state.vx !== undefined && <div>VX: {state.vx}</div>}
                    {state.vy !== undefined && <div>VY: {state.vy}</div>}
                    {state.color !== undefined && (
                      <div>Color: {state.color}</div>
                    )}
                    {state.volume !== undefined && (
                      <div>Volume: {state.volume}</div>
                    )}
                    {state.elements && (
                      <div>Elements: [{state.elements.join(", ")}]</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SimulationPreview;
