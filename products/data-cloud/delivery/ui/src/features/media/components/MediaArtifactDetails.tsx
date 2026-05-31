/**
 * Media artifact details component.
 *
 * Displays detailed information about a media artifact including metadata,
 * processing jobs, and results with redaction for sensitive fields.
 *
 * G21: Media artifact details with redaction and lineage links.
 *
 * @doc.type component
 * @doc.purpose Media artifact details display
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import clsx from "clsx";
import { useAtom } from "jotai";
import {
  AlertTriangle,
  ArrowLeft,
  Clock,
  EyeOff,
  FileText,
} from "lucide-react";
import React from "react";
import {
  selectedArtifactAnalysisJobsAtom,
  selectedArtifactTranscriptionJobsAtom,
  selectedMediaArtifactAtom,
} from "../stores/media.store";

interface MediaArtifactDetailsProps {
  onBack: () => void;
}

/**
 * G21: i18n keys for user-visible text
 */
const I18N_KEYS = {
  back: "mediaArtifactDetails.back",
  metadata: "mediaArtifactDetails.metadata",
  artifactId: "mediaArtifactDetails.artifactId",
  tenantId: "mediaArtifactDetails.tenantId",
  agentId: "mediaArtifactDetails.agentId",
  mediaType: "mediaArtifactDetails.mediaType",
  storageUri: "mediaArtifactDetails.storageUri",
  sizeBytes: "mediaArtifactDetails.sizeBytes",
  durationMs: "mediaArtifactDetails.durationMs",
  checksum: "mediaArtifactDetails.checksum",
  originToolId: "mediaArtifactDetails.originToolId",
  correlationId: "mediaArtifactDetails.correlationId",
  consentStatus: "mediaArtifactDetails.consentStatus",
  retentionPolicy: "mediaArtifactDetails.retentionPolicy",
  retentionUntil: "mediaArtifactDetails.retentionUntil",
  createdAt: "mediaArtifactDetails.createdAt",
  processingJobs: "mediaArtifactDetails.processingJobs",
  transcriptionJobs: "mediaArtifactDetails.transcriptionJobs",
  analysisJobs: "mediaArtifactDetails.analysisJobs",
  noJobs: "mediaArtifactDetails.noJobs",
  jobId: "mediaArtifactDetails.jobId",
  status: "mediaArtifactDetails.status",
  pending: "mediaArtifactDetails.pending",
  processing: "mediaArtifactDetails.processing",
  completed: "mediaArtifactDetails.completed",
  failed: "mediaArtifactDetails.failed",
  redacted: "mediaArtifactDetails.redacted",
  consentWarning: "mediaArtifactDetails.consentWarning",
  retentionWarning: "mediaArtifactDetails.retentionWarning",
} as const;

/**
 * Media artifact details component.
 *
 * Shows artifact metadata, processing jobs, and results with appropriate redaction.
 */
export const MediaArtifactDetails: React.FC<MediaArtifactDetailsProps> = ({
  onBack,
}) => {
  const [artifact] = useAtom(selectedMediaArtifactAtom);
  const [transcriptionJobs] = useAtom(selectedArtifactTranscriptionJobsAtom);
  const [analysisJobs] = useAtom(selectedArtifactAnalysisJobsAtom);

  if (!artifact) {
    return (
      <div className="text-center py-12 text-gray-500">
        No artifact selected
      </div>
    );
  }

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + " " + sizes[i];
  };

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "pending":
        return "bg-yellow-100 text-yellow-800";
      case "processing":
        return "bg-blue-100 text-blue-800";
      case "completed":
        return "bg-green-100 text-green-800";
      case "failed":
        return "bg-red-100 text-red-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const shouldRedact = (_field: string) => {
    // G21: Redact sensitive fields based on consent status and retention policy
    if (artifact.consentStatus === "DENIED") {
      return true;
    }
    if (
      artifact.retentionUntil &&
      new Date(artifact.retentionUntil) < new Date()
    ) {
      return true;
    }
    return false;
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={onBack}
          className="p-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded"
          aria-label={I18N_KEYS.back}
        >
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {artifact.artifactId}
          </h1>
          <p className="mt-1 text-sm text-gray-600">
            {artifact.mediaType} artifact
          </p>
        </div>
      </div>

      {/* Consent/Retention Warnings */}
      {artifact.consentStatus === "DENIED" && (
        <div className="flex items-start gap-3 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <AlertTriangle className="w-5 h-5 text-yellow-600 mt-0.5" />
          <div>
            <h3 className="text-sm font-medium text-yellow-800">
              {I18N_KEYS.consentWarning}
            </h3>
            <p className="mt-1 text-sm text-yellow-700">
              Consent for this artifact has been denied. Some fields may be
              redacted.
            </p>
          </div>
        </div>
      )}

      {artifact.retentionUntil &&
        new Date(artifact.retentionUntil) < new Date() && (
          <div className="flex items-start gap-3 p-4 bg-orange-50 border border-orange-200 rounded-lg">
            <Clock className="w-5 h-5 text-orange-600 mt-0.5" />
            <div>
              <h3 className="text-sm font-medium text-orange-800">
                {I18N_KEYS.retentionWarning}
              </h3>
              <p className="mt-1 text-sm text-orange-700">
                This artifact has exceeded its retention period. Some fields may
                be redacted.
              </p>
            </div>
          </div>
        )}

      {/* Metadata */}
      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {I18N_KEYS.metadata}
          </h2>
        </div>
        <div className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.artifactId}
              </label>
              <p className="mt-1 text-sm text-gray-900">
                {artifact.artifactId}
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.tenantId}
              </label>
              <p className="mt-1 text-sm text-gray-900">{artifact.tenantId}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.agentId}
              </label>
              <p className="mt-1 text-sm text-gray-900">{artifact.agentId}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.mediaType}
              </label>
              <p className="mt-1 text-sm text-gray-900">{artifact.mediaType}</p>
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.storageUri}
              </label>
              <div className="mt-1 flex items-center gap-2">
                {shouldRedact("storageUri") ? (
                  <div className="flex items-center gap-2 text-gray-400">
                    <EyeOff size={16} />
                    <span className="text-sm">{I18N_KEYS.redacted}</span>
                  </div>
                ) : (
                  <p className="text-sm text-gray-900 font-mono">
                    {artifact.storageUri}
                  </p>
                )}
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.sizeBytes}
              </label>
              <p className="mt-1 text-sm text-gray-900">
                {formatBytes(artifact.sizeBytes)}
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.durationMs}
              </label>
              <p className="mt-1 text-sm text-gray-900">
                {formatDuration(artifact.durationMs)}
              </p>
            </div>
            {artifact.checksum && (
              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700">
                  {I18N_KEYS.checksum}
                </label>
                <p className="mt-1 text-sm text-gray-900 font-mono">
                  {artifact.checksum}
                </p>
              </div>
            )}
            {artifact.originToolId && (
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  {I18N_KEYS.originToolId}
                </label>
                <p className="mt-1 text-sm text-gray-900">
                  {artifact.originToolId}
                </p>
              </div>
            )}
            {artifact.correlationId && (
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  {I18N_KEYS.correlationId}
                </label>
                <p className="mt-1 text-sm text-gray-900">
                  {artifact.correlationId}
                </p>
              </div>
            )}
            {artifact.consentStatus && (
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  {I18N_KEYS.consentStatus}
                </label>
                <p className="mt-1 text-sm text-gray-900">
                  {artifact.consentStatus}
                </p>
              </div>
            )}
            {artifact.retentionPolicy && (
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  {I18N_KEYS.retentionPolicy}
                </label>
                <p className="mt-1 text-sm text-gray-900">
                  {artifact.retentionPolicy}
                </p>
              </div>
            )}
            {artifact.retentionUntil && (
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  {I18N_KEYS.retentionUntil}
                </label>
                <p className="mt-1 text-sm text-gray-900">
                  {new Date(artifact.retentionUntil).toLocaleString()}
                </p>
              </div>
            )}
            <div>
              <label className="block text-sm font-medium text-gray-700">
                {I18N_KEYS.createdAt}
              </label>
              <p className="mt-1 text-sm text-gray-900">
                {new Date(artifact.createdAt).toLocaleString()}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Processing Jobs */}
      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {I18N_KEYS.processingJobs}
          </h2>
        </div>
        <div className="p-6 space-y-6">
          {/* Transcription Jobs */}
          {transcriptionJobs.length > 0 && (
            <div>
              <h3 className="text-sm font-medium text-gray-900 mb-3">
                {I18N_KEYS.transcriptionJobs}
              </h3>
              <div className="space-y-2">
                {transcriptionJobs.map((job) => (
                  <div
                    key={job.jobId}
                    className="flex items-center justify-between p-3 bg-gray-50 rounded"
                  >
                    <div className="flex items-center gap-3">
                      <FileText size={16} className="text-gray-500" />
                      <div>
                        <p className="text-sm font-medium text-gray-900">
                          {job.jobId}
                        </p>
                        <p className="text-xs text-gray-500">
                          {job.language && `Language: ${job.language}`}
                          {job.format && ` • Format: ${job.format}`}
                        </p>
                      </div>
                    </div>
                    <span
                      className={clsx(
                        "px-2 py-1 rounded text-xs font-medium",
                        getStatusColor(job.status),
                      )}
                    >
                      {job.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Analysis Jobs */}
          {analysisJobs.length > 0 && (
            <div>
              <h3 className="text-sm font-medium text-gray-900 mb-3">
                {I18N_KEYS.analysisJobs}
              </h3>
              <div className="space-y-2">
                {analysisJobs.map((job) => (
                  <div
                    key={job.jobId}
                    className="flex items-center justify-between p-3 bg-gray-50 rounded"
                  >
                    <div className="flex items-center gap-3">
                      <Clock size={16} className="text-gray-500" />
                      <div>
                        <p className="text-sm font-medium text-gray-900">
                          {job.jobId}
                        </p>
                        <p className="text-xs text-gray-500">
                          Type: {job.analysisType}
                        </p>
                      </div>
                    </div>
                    <span
                      className={clsx(
                        "px-2 py-1 rounded text-xs font-medium",
                        getStatusColor(job.status),
                      )}
                    >
                      {job.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* No Jobs */}
          {transcriptionJobs.length === 0 && analysisJobs.length === 0 && (
            <p className="text-sm text-gray-500">{I18N_KEYS.noJobs}</p>
          )}
        </div>
      </div>
    </div>
  );
};
