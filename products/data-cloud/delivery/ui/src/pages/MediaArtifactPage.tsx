/**
 * Media artifact route container.
 *
 * @doc.type page
 * @doc.purpose Route-level container for media artifact lifecycle management
 * @doc.layer product
 * @doc.pattern Container Component
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import React from "react";
import { toast } from "sonner";
import { MediaArtifactDetails } from "../features/media/components/MediaArtifactDetails";
import { MediaArtifactsPage as MediaArtifactsTable } from "../features/media/components/MediaArtifactsPage";
import { mediaApi } from "../features/media/services/api";
import {
  addMediaArtifactAtom,
  selectMediaArtifactAtom,
  selectedMediaArtifactAtom,
} from "../features/media/stores/media.store";
import type { MediaArtifactCreateRequest } from "../contracts/schemas";

type RegisterFormState = {
  agentId: string;
  mediaType: string;
  storageUri: string;
  durationMs: string;
  checksum: string;
  consentStatus: NonNullable<MediaArtifactCreateRequest["consentStatus"]>;
  retentionPolicy: string;
  retentionUntil: string;
};

const initialFormState: RegisterFormState = {
  agentId: "",
  mediaType: "audio/wav",
  storageUri: "",
  durationMs: "",
  checksum: "",
  consentStatus: "PENDING",
  retentionPolicy: "",
  retentionUntil: "",
};

function toPositiveInteger(value: string): number | undefined {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function toCreateRequest(form: RegisterFormState): MediaArtifactCreateRequest {
  const request: MediaArtifactCreateRequest = {
    agentId: form.agentId.trim(),
    mediaType: form.mediaType.trim(),
    storageUri: form.storageUri.trim(),
    consentStatus: form.consentStatus,
  };

  const durationMs = toPositiveInteger(form.durationMs);
  const checksum = form.checksum.trim();
  const retentionPolicy = form.retentionPolicy.trim();

  if (durationMs !== undefined) {
    request.durationMs = durationMs;
  }
  if (checksum) {
    request.checksum = checksum;
  }
  if (retentionPolicy) {
    request.retentionPolicy = retentionPolicy;
  }
  if (form.retentionUntil) {
    request.retentionUntil = form.retentionUntil;
  }

  return request;
}

export function MediaArtifactPage(): React.ReactElement {
  const queryClient = useQueryClient();
  const [isRegistering, setIsRegistering] = React.useState(false);
  const [form, setForm] = React.useState<RegisterFormState>(initialFormState);
  const [selectedArtifact] = useAtom(selectedMediaArtifactAtom);
  const [, selectArtifact] = useAtom(selectMediaArtifactAtom);
  const [, addArtifact] = useAtom(addMediaArtifactAtom);

  const createMutation = useMutation({
    mutationFn: async () => mediaApi.create(toCreateRequest(form)),
    onSuccess: async (artifact) => {
      await addArtifact(artifact);
      await queryClient.invalidateQueries({ queryKey: ["media-artifacts"] });
      setForm(initialFormState);
      setIsRegistering(false);
      toast.success("mediaArtifacts.registerSuccess");
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      toast.error(`mediaArtifacts.registerFailed: ${message}`);
    },
  });

  const updateForm =
    (field: keyof RegisterFormState) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
      setForm((current) => ({ ...current, [field]: event.target.value }));
    };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    createMutation.mutate();
  };

  if (selectedArtifact) {
    return <MediaArtifactDetails onBack={() => selectArtifact(null)} />;
  }

  return (
    <div className="space-y-6">
      <MediaArtifactsTable
        onRegisterClick={() => setIsRegistering(true)}
        onDetailsClick={(artifact) => selectArtifact(artifact.artifactId)}
      />

      {isRegistering && (
        <div
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
        >
          <form
            className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl"
            onSubmit={handleSubmit}
          >
            <div className="mb-5">
              <h2 className="text-xl font-semibold text-gray-900">
                mediaArtifacts.registerArtifact
              </h2>
              <p className="mt-1 text-sm text-gray-600">
                mediaArtifacts.registerDescription
              </p>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.agentId
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("agentId")}
                  required
                  value={form.agentId}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.mediaType
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("mediaType")}
                  required
                  value={form.mediaType}
                />
              </label>
              <label className="md:col-span-2 text-sm font-medium text-gray-700">
                mediaArtifactDetails.storageUri
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("storageUri")}
                  required
                  value={form.storageUri}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.durationMs
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  min="0"
                  onChange={updateForm("durationMs")}
                  type="number"
                  value={form.durationMs}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.consentStatus
                <select
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("consentStatus")}
                  value={form.consentStatus}
                >
                  <option value="PENDING">PENDING</option>
                  <option value="GRANTED">GRANTED</option>
                  <option value="DENIED">DENIED</option>
                </select>
              </label>
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.checksum
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("checksum")}
                  value={form.checksum}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.retentionPolicy
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("retentionPolicy")}
                  value={form.retentionPolicy}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                mediaArtifactDetails.retentionUntil
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("retentionUntil")}
                  type="datetime-local"
                  value={form.retentionUntil}
                />
              </label>
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <button
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700"
                onClick={() => setIsRegistering(false)}
                type="button"
              >
                common.cancel
              </button>
              <button
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                disabled={createMutation.isPending}
                type="submit"
              >
                {createMutation.isPending
                  ? "mediaArtifacts.registering"
                  : "mediaArtifacts.registerArtifact"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
