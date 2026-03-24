/**
 * Simulation Studio Page
 *
 * Entry point for the AI-powered simulation authoring experience.
 * Loads an existing manifest when `id` is provided, then delegates
 * all editing to the SimulationStudio component backed by useNLAuthoring.
 *
 * @doc.type page
 * @doc.purpose Host the full simulation authoring UI with NL-driven manifest generation
 * @doc.layer product
 * @doc.pattern Page
 */
import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { SimulationStudio as SimulationStudioComponent } from "../components/simulation/SimulationStudio";
import { useNLAuthoring } from "../features/simulation-authoring/hooks/useNLAuthoring";
import type {
  SimulationManifest,
  SimulationDomain,
} from "@tutorputor/contracts/v1/simulation";

interface NLRefinementResult {
  success: boolean;
  manifest?: SimulationManifest;
  response: string;
  suggestions: string[];
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export default function SimulationStudio() {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();

  const [initialManifest, setInitialManifest] = useState<
    SimulationManifest | undefined
  >(undefined);
  const [isLoading, setIsLoading] = useState(!!id);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setIsLoading(false);
      return;
    }

    fetch(`/api/sim-author/manifests/${encodeURIComponent(id)}`, {
      headers: getAuthHeaders(),
    })
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status}`);
        return r.json() as Promise<{ manifest: SimulationManifest }>;
      })
      .then((data) => {
        setInitialManifest(data.manifest);
        setIsLoading(false);
      })
      .catch((err) => {
        console.error("Failed to load simulation manifest", err);
        setLoadError("Failed to load the simulation. It may not exist or you may not have permission.");
        setIsLoading(false);
      });
  }, [id]);

  const authoring = useNLAuthoring({
    initialDomain: "CS_DISCRETE",
    initialManifest,
  });

  const handleAIGenerate = async (
    prompt: string,
    domain: SimulationDomain,
  ): Promise<SimulationManifest> => {
    authoring.setDomain(domain);
    const manifest = await authoring.generate(prompt);
    if (!manifest) throw new Error("AI generation failed. Please try again.");
    return manifest;
  };

  const handleNLRefine = async (input: string): Promise<NLRefinementResult> => {
    const manifest = await authoring.refine(input);
    return {
      success: !!manifest,
      manifest: manifest ?? undefined,
      response: manifest
        ? "Simulation updated successfully."
        : "Could not apply that change — please rephrase and try again.",
      suggestions: [],
    };
  };

  const handleSave = async (manifest: SimulationManifest): Promise<void> => {
    if (!manifest.id) return;
    try {
      const res = await fetch(
        `/api/sim-author/manifests/${encodeURIComponent(manifest.id)}`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            ...getAuthHeaders(),
          },
          body: JSON.stringify(manifest),
        },
      );
      if (!res.ok) throw new Error(`${res.status}`);
    } catch (err) {
      console.error("Failed to save simulation manifest", err);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full min-h-screen">
        <p className="text-gray-500 text-sm">Loading simulation…</p>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="p-8">
        <p className="text-red-600 text-sm">{loadError}</p>
        <button
          className="mt-4 text-sm text-blue-600 underline"
          onClick={() => navigate(-1)}
        >
          Go back
        </button>
      </div>
    );
  }

  return (
    <SimulationStudioComponent
      initialManifest={authoring.manifest ?? initialManifest}
      onSave={handleSave}
      onCancel={() => navigate(-1)}
      onNLRefine={handleNLRefine}
      onAIGenerate={handleAIGenerate}
    />
  );
}

