import React, { useCallback, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type DocumentStatus =
  | "PENDING_UPLOAD"
  | "UPLOADED"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED";

interface RequiredDocument {
  type: string;         // e.g. "PASSPORT_OR_CITIZENSHIP"
  label: string;        // e.g. "Passport or Citizenship Certificate"
  status: DocumentStatus;
  uploadedAt?: string;
  rejectionReason?: string;
}

interface UploadProgress {
  documentType: string;
  percent: number;      // 0-100
  uploading: boolean;
  error?: string;
}

interface UploadResult {
  documentType: string;
  objectKey: string;
  uploadedAt: string;
}

// ---------------------------------------------------------------------------
// API helpers  (adapt base URL via env)
// ---------------------------------------------------------------------------

const API_BASE = "/api/onboarding";

async function fetchRequiredDocuments(instanceId: string): Promise<RequiredDocument[]> {
  const res = await fetch(`${API_BASE}/kyc/${instanceId}/documents`);
  if (!res.ok) throw new Error("Failed to load required documents");
  return res.json();
}

async function getUploadUrl(instanceId: string, documentType: string, mimeType: string) {
  const res = await fetch(`${API_BASE}/kyc/${instanceId}/upload-url`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ documentType, mimeType }),
  });
  if (!res.ok) throw new Error("Failed to get upload URL");
  return res.json() as Promise<{ presignedUrl: string; objectKey: string }>;
}

async function confirmUpload(instanceId: string, documentType: string, objectKey: string): Promise<UploadResult> {
  const res = await fetch(`${API_BASE}/kyc/${instanceId}/documents/${documentType}/confirm`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ objectKey }),
  });
  if (!res.ok) throw new Error("Failed to confirm upload");
  return res.json();
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const ALLOWED_MIME_TYPES = ["application/pdf", "image/jpeg", "image/png"];
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

function humanizeDocType(type: string) {
  return type
    .split("_")
    .map((w) => w[0] + w.slice(1).toLowerCase())
    .join(" ");
}

function statusColor(status: DocumentStatus) {
  const map: Record<DocumentStatus, string> = {
    PENDING_UPLOAD: "bg-gray-100 text-gray-600",
    UPLOADED:       "bg-blue-100  text-blue-700",
    UNDER_REVIEW:   "bg-yellow-100 text-yellow-700",
    APPROVED:       "bg-green-100 text-green-700",
    REJECTED:       "bg-red-100   text-red-700",
  };
  return map[status];
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

interface DropZoneProps {
  documentType: string;
  status: DocumentStatus;
  progress?: UploadProgress;
  onFileDrop: (documentType: string, file: File) => void;
}

function DropZone({ documentType, status, progress, onFileDrop }: DropZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const disabled = status === "APPROVED" || status === "UNDER_REVIEW";

  function validate(file: File): string | null {
    if (!ALLOWED_MIME_TYPES.includes(file.type)) return "Only PDF, JPG, or PNG files are accepted.";
    if (file.size > MAX_FILE_SIZE_BYTES) return "File must be under 5 MB.";
    return null;
  }

  const handle = useCallback((file: File) => {
    const err = validate(file);
    if (err) { setValidationError(err); return; }
    setValidationError(null);
    onFileDrop(documentType, file);
  }, [documentType, onFileDrop]);

  return (
    <div
      className={`relative border-2 rounded-lg p-4 transition-colors
        ${dragging ? "border-blue-500 bg-blue-50" : "border-dashed border-gray-300 bg-white"}
        ${disabled ? "opacity-50 pointer-events-none" : "cursor-pointer hover:border-blue-400"}`}
      onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragging(false);
        const file = e.dataTransfer?.files[0];
        if (file) handle(file);
      }}
      onClick={() => inputRef.current?.click()}
    >
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept=".pdf,.jpg,.jpeg,.png"
        onChange={(e) => { const f = e.target.files?.[0]; if (f) handle(f); e.target.value = ""; }}
      />

      {progress?.uploading ? (
        <div className="space-y-2">
          <p className="text-sm text-blue-600 font-medium">Uploading…</p>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-500 h-2 rounded-full transition-all"
              style={{ width: `${progress.percent}%` }}
            />
          </div>
          <p className="text-xs text-gray-500">{progress.percent}%</p>
        </div>
      ) : (
        <div className="text-center">
          <svg className="mx-auto h-8 w-8 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M7 16V12m0 0V8m0 4H3m4 0h4M17 8l-4-4-4 4m8 0v4a4 4 0 01-8 0V8" />
          </svg>
          <p className="mt-1 text-xs text-gray-500">Drag and drop, or click to browse</p>
          <p className="text-xs text-gray-400">PDF · JPG · PNG — max 5 MB</p>
        </div>
      )}

      {(validationError || progress?.error) && (
        <p className="mt-2 text-xs text-red-600">{validationError ?? progress?.error}</p>
      )}
    </div>
  );
}

interface DocumentCardProps {
  doc: RequiredDocument;
  progress?: UploadProgress;
  onFileDrop: (documentType: string, file: File) => void;
}

function DocumentCard({ doc, progress, onFileDrop }: DocumentCardProps) {
  return (
    <div className="rounded-xl border border-gray-200 p-4 shadow-sm space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-gray-800">{humanizeDocType(doc.type)}</h3>
        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${statusColor(doc.status)}`}>
          {doc.status.replace("_", " ")}
        </span>
      </div>

      {doc.status === "REJECTED" && doc.rejectionReason && (
        <p className="text-xs bg-red-50 text-red-700 rounded px-2 py-1 border border-red-200">
          Rejection reason: {doc.rejectionReason}
        </p>
      )}
      {doc.uploadedAt && (
        <p className="text-xs text-gray-400">Uploaded: {new Date(doc.uploadedAt).toLocaleString()}</p>
      )}

      <DropZone documentType={doc.type} status={doc.status} progress={progress} onFileDrop={onFileDrop} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

interface DocumentUploadPortalProps {
  instanceId: string;
}

export function DocumentUploadPortal({ instanceId }: DocumentUploadPortalProps) {
  const queryClient = useQueryClient();
  const [progressMap, setProgressMap] = useState<Record<string, UploadProgress>>({});

  const { data: documents, isLoading, error } = useQuery({
    queryKey: ["kyc-documents", instanceId],
    queryFn: () => fetchRequiredDocuments(instanceId),
    refetchInterval: 30_000,
  });

  const uploadMutation = useMutation({
    mutationFn: async ({ documentType, file }: { documentType: string; file: File }) => {
      // 1. Get pre-signed URL
      setProgressMap((prev) => ({
        ...prev,
        [documentType]: { documentType, percent: 0, uploading: true },
      }));

      const { presignedUrl, objectKey } = await getUploadUrl(instanceId, documentType, file.type);

      // 2. Upload directly to object storage with XHR for progress tracking
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open("PUT", presignedUrl);
        xhr.setRequestHeader("Content-Type", file.type);
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const percent = Math.round((e.loaded / e.total) * 100);
            setProgressMap((prev) => ({
              ...prev,
              [documentType]: { documentType, percent, uploading: true },
            }));
          }
        };
        xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error("Upload failed")));
        xhr.onerror = () => reject(new Error("Network error during upload"));
        xhr.send(file);
      });

      // 3. Confirm with back-end
      const result = await confirmUpload(instanceId, documentType, objectKey);
      return result;
    },
    onSuccess: (_, { documentType }) => {
      setProgressMap((prev) => ({
        ...prev,
        [documentType]: { documentType, percent: 100, uploading: false },
      }));
      queryClient.invalidateQueries({ queryKey: ["kyc-documents", instanceId] });
    },
    onError: (err: Error, { documentType }) => {
      setProgressMap((prev) => ({
        ...prev,
        [documentType]: { documentType, percent: 0, uploading: false, error: err.message },
      }));
    },
  });

  const handleFileDrop = useCallback((documentType: string, file: File) => {
    uploadMutation.mutate({ documentType, file });
  }, [uploadMutation]);

  // ---------- Completion percentage ----------
  const completionPercent = documents
    ? Math.round(
        (documents.filter((d) => ["UPLOADED", "UNDER_REVIEW", "APPROVED"].includes(d.status)).length /
          documents.length) *
          100
      )
    : 0;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        Loading required documents…
      </div>
    );
  }

  if (error || !documents) {
    return (
      <div className="rounded-lg bg-red-50 border border-red-200 p-4 text-red-700 text-sm">
        Failed to load document requirements. Please refresh the page.
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6 p-4">
      {/* Header */}
      <div>
        <h2 className="text-xl font-semibold text-gray-900">Document Submission</h2>
        <p className="text-sm text-gray-500 mt-1">
          Please provide all required documents to complete your onboarding.
        </p>
      </div>

      {/* Progress bar */}
      <div className="space-y-1">
        <div className="flex justify-between text-xs text-gray-500">
          <span>Overall progress</span>
          <span>{completionPercent}%</span>
        </div>
        <div className="w-full bg-gray-200 rounded-full h-2">
          <div
            className="bg-blue-600 h-2 rounded-full transition-all duration-500"
            style={{ width: `${completionPercent}%` }}
          />
        </div>
        <p className="text-xs text-gray-400">
          {documents.filter((d) => ["UPLOADED", "UNDER_REVIEW", "APPROVED"].includes(d.status)).length}{" "}
          of {documents.length} documents submitted
        </p>
      </div>

      {/* Document cards */}
      <div className="grid gap-4">
        {documents.map((doc) => (
          <DocumentCard
            key={doc.type}
            doc={doc}
            progress={progressMap[doc.type]}
            onFileDrop={handleFileDrop}
          />
        ))}
      </div>

      {/* Completion message */}
      {completionPercent === 100 && (
        <div className="rounded-lg bg-green-50 border border-green-200 p-4 text-green-700 text-sm font-medium">
          All documents submitted. Our team will review them and notify you within 2 business days.
        </div>
      )}
    </div>
  );
}

export default DocumentUploadPortal;
