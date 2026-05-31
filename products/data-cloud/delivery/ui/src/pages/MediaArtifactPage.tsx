/**
 * Media artifact route container.
 *
 * Pass 6 - Audio-video first-class modality:
 * - i18n support using useTranslation
 * - Status timeline visualization
 * - Consent-aware UI controls
 * - Retention warnings
 * - Job/transcript/frame index panels
 *
 * @doc.type page
 * @doc.purpose Route-level container for media artifact lifecycle management
 * @doc.layer product
 * @doc.pattern Container Component
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { MediaArtifactDetails } from "../features/media/components/MediaArtifactDetails";
import { MediaArtifactsPage as MediaArtifactsTable } from "../features/media/components/MediaArtifactsPage";
import { mediaApi } from "../features/media/services/api";
import {
  addMediaArtifactAtom,
  selectMediaArtifactAtom,
  selectedMediaArtifactAtom,
} from "../features/media/stores/media.store";
import type { MediaArtifactCreateRequest, MediaArtifact } from "../contracts/schemas";

// Pass 6: Lifecycle status component
function StatusTimeline({ artifact }: { artifact: MediaArtifact }): React.ReactElement {
  const { t } = useTranslation();
  const lifecycleStates = [
    { key: "REGISTERED", label: t("media.lifecycle.registered"), active: true },
    { key: "CONSENT_PENDING", label: t("media.lifecycle.consentPending"), active: artifact.processingState === "CONSENT_PENDING" || artifact.processingState === "CONSENT_DENIED" },
    { key: "QUEUED", label: t("media.lifecycle.queued"), active: artifact.processingState === "QUEUED" },
    { key: "PROCESSING", label: t("media.lifecycle.processing"), active: artifact.processingState === "PROCESSING" },
    { key: "COMPLETED", label: t("media.lifecycle.completed"), active: artifact.processingState === "TRANSCRIBED" || artifact.processingState === "ANALYZED" || artifact.processingState === "INDEXED" },
  ];

  const currentIndex = lifecycleStates.findIndex(s => s.active);

  return (
    <div className="mb-6 rounded-lg border border-gray-200 bg-gray-50 p-4">
      <h3 className="mb-3 text-sm font-semibold text-gray-900">{t("media.statusTimeline")}</h3>
      <div className="flex items-center gap-2">
        {lifecycleStates.map((state, index) => (
          <React.Fragment key={state.key}>
            <div className={`flex items-center gap-1 rounded-full px-3 py-1 text-xs font-medium ${
              index <= currentIndex
                ? "bg-blue-100 text-blue-800"
                : "bg-gray-200 text-gray-600"
            }`}>
              <span>{state.label}</span>
            </div>
            {index < lifecycleStates.length - 1 && (
              <div className={`h-0.5 w-4 ${index < currentIndex ? "bg-blue-300" : "bg-gray-300"}`} />
            )}
          </React.Fragment>
        ))}
      </div>
      {artifact.processingState === "FAILED" && (
        <div className="mt-2 text-sm text-red-600">
          {t("media.lastError")}: {artifact.lastError || t("media.unknownError")}
        </div>
      )}
    </div>
  );
}

// Pass 6: Consent warning banner
function ConsentWarning({ artifact }: { artifact: MediaArtifact }): React.ReactElement | null {
  const { t } = useTranslation();
  if (!artifact.requiresConsent) return null;
  if (artifact.consentStatus === "GRANTED") return null;

  return (
    <div className="mb-4 rounded-md border border-yellow-300 bg-yellow-50 p-3 text-sm text-yellow-800" role="alert">
      <div className="flex items-center gap-2">
        <span className="font-semibold">{t("media.consentRequired")}</span>
        <span>{t("media.consentStatus")}: {artifact.consentStatus}</span>
      </div>
    </div>
  );
}

// Pass 6: Retention warning
function RetentionWarning({ artifact }: { artifact: MediaArtifact }): React.ReactElement | null {
  const { t } = useTranslation();
  if (!artifact.retentionPolicy) return null;

  const isExpiringSoon = artifact.retentionUntil != null &&
    new Date(artifact.retentionUntil).getTime() - Date.now() < 7 * 24 * 60 * 60 * 1000;

  if (!isExpiringSoon) return null;

  return (
    <div className="mb-4 rounded-md border border-orange-300 bg-orange-50 p-3 text-sm text-orange-800" role="alert">
      <div className="flex items-center gap-2">
        <span className="font-semibold">{t("media.retentionWarning")}</span>
        <span>{t("media.retentionUntil")}: {artifact.retentionUntil ? new Date(artifact.retentionUntil).toLocaleDateString() : "-"}</span>
      </div>
    </div>
  );
}

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
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [isRegistering, setIsRegistering] = React.useState(false);
  const [form, setForm] = React.useState<RegisterFormState>(initialFormState);
  const [selectedArtifact] = useAtom(selectedMediaArtifactAtom);
  const [, selectArtifact] = useAtom(selectMediaArtifactAtom);
  const [, addArtifact] = useAtom(addMediaArtifactAtom);

  // Pass 6: Fetch jobs, transcript, frame index for selected artifact
  const { data: jobs } = useQuery({
    queryKey: ["media-artifact-jobs", selectedArtifact?.artifactId],
    queryFn: () => selectedArtifact ? mediaApi.getJobs(selectedArtifact.artifactId) : null,
    enabled: !!selectedArtifact,
  });

  const { data: transcript } = useQuery({
    queryKey: ["media-artifact-transcript", selectedArtifact?.artifactId],
    queryFn: () => selectedArtifact ? mediaApi.getTranscript(selectedArtifact.artifactId) : null,
    enabled: !!selectedArtifact && selectedArtifact.transcriptId != null,
  });

  const { data: frameIndex } = useQuery({
    queryKey: ["media-artifact-frame-index", selectedArtifact?.artifactId],
    queryFn: () => selectedArtifact ? mediaApi.getFrameIndex(selectedArtifact.artifactId) : null,
    enabled: !!selectedArtifact && selectedArtifact.frameIndexId != null,
  });

  // Pass 6: Consent update mutation
  const consentMutation = useMutation({
    mutationFn: async ({ artifactId, consentStatus }: { artifactId: string; consentStatus: string }) =>
      mediaApi.updateConsent(artifactId, consentStatus),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["media-artifacts"] });
      toast.success(t("media.consentUpdateSuccess"));
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      toast.error(t("media.consentUpdateFailed", { message }));
    },
  });

  // Pass 6: Retry mutation
  const retryMutation = useMutation({
    mutationFn: async (artifactId: string) => mediaApi.retryJob(artifactId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["media-artifacts"] });
      toast.success(t("media.retrySuccess"));
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      toast.error(t("media.retryFailed", { message }));
    },
  });

  const createMutation = useMutation({
    mutationFn: async () => mediaApi.create(toCreateRequest(form)),
    onSuccess: async (artifact) => {
      await addArtifact(artifact);
      await queryClient.invalidateQueries({ queryKey: ["media-artifacts"] });
      setForm(initialFormState);
      setIsRegistering(false);
      toast.success(t("mediaArtifacts.registerSuccess"));
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      toast.error(t("mediaArtifacts.registerFailed", { message }));
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
    const canProcess = selectedArtifact.canBeProcessed;
    const isFailed = selectedArtifact.processingState === "FAILED";

    return (
      <div className="space-y-6">
        <button
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700"
          onClick={() => selectArtifact(null)}
        >
          {t("common.back")}
        </button>

        {/* Pass 6: Status timeline */}
        <StatusTimeline artifact={selectedArtifact} />

        {/* Pass 6: Warnings */}
        <ConsentWarning artifact={selectedArtifact} />
        <RetentionWarning artifact={selectedArtifact} />

        {/* Pass 6: Consent controls for audio/video */}
        {selectedArtifact.requiresConsent && selectedArtifact.consentStatus !== "GRANTED" && (
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <h3 className="mb-3 text-sm font-semibold text-gray-900">{t("media.consentManagement")}</h3>
            <div className="flex gap-2">
              <button
                className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white"
                onClick={() => consentMutation.mutate({ artifactId: selectedArtifact.artifactId, consentStatus: "GRANTED" })}
                disabled={consentMutation.isPending}
              >
                {t("media.grantConsent")}
              </button>
              <button
                className="rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-700"
                onClick={() => consentMutation.mutate({ artifactId: selectedArtifact.artifactId, consentStatus: "DENIED" })}
                disabled={consentMutation.isPending}
              >
                {t("media.denyConsent")}
              </button>
            </div>
          </div>
        )}

        {/* Pass 6: Processing controls */}
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h3 className="mb-3 text-sm font-semibold text-gray-900">{t("media.processingActions")}</h3>
          <div className="flex gap-2">
            {selectedArtifact.mediaType?.startsWith("audio/") && (
              <button
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                disabled={!canProcess}
                onClick={() => mediaApi.transcribe(selectedArtifact.artifactId)}
                title={!canProcess ? t("media.cannotProcessTooltip") : undefined}
              >
                {t("media.transcribe")}
              </button>
            )}
            {(selectedArtifact.mediaType?.startsWith("image/") || selectedArtifact.mediaType?.startsWith("video/")) && (
              <button
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                disabled={!canProcess}
                onClick={() => mediaApi.analyze(selectedArtifact.artifactId, { analysisType: "object_detection" })}
                title={!canProcess ? t("media.cannotProcessTooltip") : undefined}
              >
                {t("media.analyze")}
              </button>
            )}
            {isFailed && (
              <button
                className="rounded-md bg-orange-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                disabled={retryMutation.isPending}
                onClick={() => retryMutation.mutate(selectedArtifact.artifactId)}
              >
                {t("media.retry")}
              </button>
            )}
          </div>
          {!canProcess && (
            <p className="mt-2 text-sm text-gray-600">{t("media.cannotProcessReason")}</p>
          )}
        </div>

        {/* Pass 6: Jobs panel */}
        {jobs && jobs.length > 0 && (
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <h3 className="mb-3 text-sm font-semibold text-gray-900">{t("media.jobsPanel")}</h3>
            <div className="space-y-2">
              {jobs.map((job: { jobId: string; jobType: string; status: string; progress: number }) => (
                <div key={job.jobId} className="flex items-center justify-between rounded-md border border-gray-200 p-2">
                  <div>
                    <span className="text-sm font-medium">{job.jobType}</span>
                    <span className="ml-2 text-xs text-gray-600">{job.jobId}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="h-2 w-24 overflow-hidden rounded-full bg-gray-200">
                      <div className="h-full bg-blue-600" style={{ width: `${job.progress}%` }} />
                    </div>
                    <span className="text-xs font-medium">{job.status}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Pass 6: Transcript panel */}
        {transcript && (
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <h3 className="mb-3 text-sm font-semibold text-gray-900">{t("media.transcriptPanel")}</h3>
            <div className="text-sm text-gray-600">
              <p>{t("media.language")}: {transcript.languageCode}</p>
              <p>{t("media.confidence")}: {(transcript.confidence * 100).toFixed(1)}%</p>
              <p>{t("media.wordCount")}: {transcript.wordCount}</p>
            </div>
            <div className="mt-3 max-h-48 overflow-y-auto rounded-md bg-gray-50 p-3 text-sm">
              {transcript.fullText}
            </div>
          </div>
        )}

        {/* Pass 6: Frame index panel */}
        {frameIndex && (
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <h3 className="mb-3 text-sm font-semibold text-gray-900">{t("media.frameIndexPanel")}</h3>
            <div className="text-sm text-gray-600">
              <p>{t("media.analysisType")}: {frameIndex.analysisType}</p>
              <p>{t("media.frameCount")}: {frameIndex.frameCount}</p>
              <p>{t("media.confidence")}: {(frameIndex.confidence * 100).toFixed(1)}%</p>
            </div>
            {frameIndex.labels && frameIndex.labels.length > 0 && (
              <div className="mt-3">
                <h4 className="text-xs font-semibold text-gray-700">{t("media.detectedLabels")}</h4>
                <div className="mt-1 flex flex-wrap gap-1">
                  {frameIndex.labels.map((label: { label: string; occurrenceCount: number }) => (
                    <span key={label.label} className="rounded-full bg-blue-100 px-2 py-1 text-xs text-blue-800">
                      {label.label} ({label.occurrenceCount})
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        <MediaArtifactDetails onBack={() => selectArtifact(null)} />
      </div>
    );
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
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              setIsRegistering(false);
            }
          }}
        >
          <form
            aria-labelledby="register-dialog-title"
            className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl"
            onSubmit={handleSubmit}
            ref={(formRef) => {
              if (formRef) {
                const firstInput = formRef.querySelector('input, select, button') as HTMLElement;
                firstInput?.focus();
              }
            }}
          >
            <div className="mb-5">
              <h2 id="register-dialog-title" className="text-xl font-semibold text-gray-900">
                {t("mediaArtifacts.registerArtifact")}
              </h2>
              <p className="mt-1 text-sm text-gray-600">
                {t("mediaArtifacts.registerDescription")}
              </p>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.agentId")}
                <input
                  aria-required="true"
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("agentId")}
                  required
                  value={form.agentId}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.mediaType")}
                <input
                  aria-required="true"
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("mediaType")}
                  required
                  value={form.mediaType}
                />
              </label>
              <label className="md:col-span-2 text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.storageUri")}
                <input
                  aria-required="true"
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("storageUri")}
                  required
                  value={form.storageUri}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.durationMs")}
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  min="0"
                  onChange={updateForm("durationMs")}
                  type="number"
                  value={form.durationMs}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.consentStatus")}
                <select
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("consentStatus")}
                  value={form.consentStatus}
                >
                  <option value="PENDING">{t("media.consentPending")}</option>
                  <option value="GRANTED">{t("media.consentGranted")}</option>
                  <option value="DENIED">{t("media.consentDenied")}</option>
                  <option value="NOT_REQUIRED">{t("media.consentNotRequired")}</option>
                </select>
              </label>
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.checksum")}
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("checksum")}
                  value={form.checksum}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.retentionPolicy")}
                <input
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  onChange={updateForm("retentionPolicy")}
                  value={form.retentionPolicy}
                />
              </label>
              <label className="text-sm font-medium text-gray-700">
                {t("mediaArtifactDetails.retentionUntil")}
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
                {t("common.cancel")}
              </button>
              <button
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                disabled={createMutation.isPending}
                type="submit"
              >
                {createMutation.isPending
                  ? t("mediaArtifacts.registering")
                  : t("mediaArtifacts.registerArtifact")}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
