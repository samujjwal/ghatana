/**
 * Interaction Evidence View - displays interaction evidence records.
 *
 * @doc.type component
 * @doc.purpose Visualize interaction evidence for audit and compliance
 * @doc.layer studio
 */

import React, { useState } from "react";

interface EvidenceRecord {
  evidenceId: string;
  manifestType: string;
  contractId: string;
  providerProductId: string;
  consumerProductId: string;
  tenantId: string;
  workspaceId: string;
  status: string;
  policyDecision: string;
  requestedAt: string;
  completedAt: string;
  capturedAt: string;
  evidenceRefs: readonly string[];
  provenanceRefs: readonly string[];
  reasonCode?: string;
}

interface InteractionEvidenceViewProps {
  evidence: readonly EvidenceRecord[];
  onEvidenceClick?: (evidenceId: string) => void;
}

export function InteractionEvidenceView({ evidence, onEvidenceClick }: InteractionEvidenceViewProps) {
  const [selectedEvidence, setSelectedEvidence] = useState<string | null>(null);
  const selectedRecord = selectedEvidence
    ? evidence.find((e) => e.evidenceId === selectedEvidence)
    : null;

  return (
    <div className="interaction-evidence-view">
      <div className="evidence-header">
        <h2>Interaction Evidence</h2>
        <div className="evidence-stats">
          <span className="stat-item">
            <span className="count">{evidence.length}</span>
            <span className="label">Records</span>
          </span>
        </div>
      </div>

      <div className="evidence-layout">
        <div className="evidence-list">
          {evidence.map((record) => (
            <div
              key={record.evidenceId}
              className={`evidence-item ${selectedEvidence === record.evidenceId ? "selected" : ""}`}
              onClick={() => {
                setSelectedEvidence(record.evidenceId);
                onEvidenceClick?.(record.evidenceId);
              }}
            >
              <div className="evidence-item-header">
                <span className="evidence-id">{record.evidenceId}</span>
                <span className={`status ${record.policyDecision === "allowed" ? "allowed" : "denied"}`}>
                  {record.policyDecision}
                </span>
              </div>
              <div className="evidence-item-details">
                <span className="contract">{record.contractId}</span>
                <span className="timestamp">{new Date(record.capturedAt).toLocaleString()}</span>
              </div>
            </div>
          ))}
        </div>

        {selectedRecord && (
          <div className="evidence-detail">
            <div className="detail-header">
              <h3>Evidence Details</h3>
              <button
                onClick={() => setSelectedEvidence(null)}
                className="close-button"
              >
                ×
              </button>
            </div>

            <div className="detail-section">
              <h4>Basic Information</h4>
              <div className="detail-row">
                <span className="label">Evidence ID:</span>
                <span className="value">{selectedRecord.evidenceId}</span>
              </div>
              <div className="detail-row">
                <span className="label">Manifest Type:</span>
                <span className="value">{selectedRecord.manifestType}</span>
              </div>
              <div className="detail-row">
                <span className="label">Status:</span>
                <span className="value">{selectedRecord.status}</span>
              </div>
              <div className="detail-row">
                <span className="label">Policy Decision:</span>
                <span className={`value ${selectedRecord.policyDecision === "allowed" ? "allowed" : "denied"}`}>
                  {selectedRecord.policyDecision}
                </span>
              </div>
              {selectedRecord.reasonCode && (
                <div className="detail-row">
                  <span className="label">Reason Code:</span>
                  <span className="value reason">{selectedRecord.reasonCode}</span>
                </div>
              )}
            </div>

            <div className="detail-section">
              <h4>Contract Information</h4>
              <div className="detail-row">
                <span className="label">Contract ID:</span>
                <span className="value">{selectedRecord.contractId}</span>
              </div>
              <div className="detail-row">
                <span className="label">Provider Product:</span>
                <span className="value">{selectedRecord.providerProductId}</span>
              </div>
              <div className="detail-row">
                <span className="label">Consumer Product:</span>
                <span className="value">{selectedRecord.consumerProductId}</span>
              </div>
            </div>

            <div className="detail-section">
              <h4>Context Information</h4>
              <div className="detail-row">
                <span className="label">Tenant ID:</span>
                <span className="value">{selectedRecord.tenantId}</span>
              </div>
              <div className="detail-row">
                <span className="label">Workspace ID:</span>
                <span className="value">{selectedRecord.workspaceId}</span>
              </div>
            </div>

            <div className="detail-section">
              <h4>Timing</h4>
              <div className="detail-row">
                <span className="label">Requested At:</span>
                <span className="value">{new Date(selectedRecord.requestedAt).toLocaleString()}</span>
              </div>
              <div className="detail-row">
                <span className="label">Completed At:</span>
                <span className="value">{new Date(selectedRecord.completedAt).toLocaleString()}</span>
              </div>
              <div className="detail-row">
                <span className="label">Captured At:</span>
                <span className="value">{new Date(selectedRecord.capturedAt).toLocaleString()}</span>
              </div>
              <div className="detail-row">
                <span className="label">Duration:</span>
                <span className="value">
                  {Math.floor(
                    (new Date(selectedRecord.completedAt).getTime() -
                      new Date(selectedRecord.requestedAt).getTime()) /
                      1000
                  )}s
                </span>
              </div>
            </div>

            <div className="detail-section">
              <h4>Evidence References</h4>
              {selectedRecord.evidenceRefs.length > 0 ? (
                <ul className="ref-list">
                  {selectedRecord.evidenceRefs.map((ref, index) => (
                    <li key={index} className="ref-item">
                      {ref}
                    </li>
                  ))}
                </ul>
              ) : (
                <span className="no-refs">No evidence references</span>
              )}
            </div>

            <div className="detail-section">
              <h4>Provenance References</h4>
              {selectedRecord.provenanceRefs.length > 0 ? (
                <ul className="ref-list">
                  {selectedRecord.provenanceRefs.map((ref, index) => (
                    <li key={index} className="ref-item">
                      {ref}
                    </li>
                  ))}
                </ul>
              ) : (
                <span className="no-refs">No provenance references</span>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
