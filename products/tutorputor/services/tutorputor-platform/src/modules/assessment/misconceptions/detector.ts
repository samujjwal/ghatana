/**
 * Misconception Detector
 *
 * Diagnoses likely misconceptions from incorrect assessment responses.
 *
 * @doc.type service
 * @doc.purpose Detect learner misconceptions from assessment responses
 * @doc.layer product
 * @doc.pattern Diagnostic Service
 */

import type {
  AssessmentFeedback,
  AssessmentItem,
  AssessmentAttempt,
} from "@tutorputor/contracts/v1/types";
import { MisconceptionDatabase, type MisconceptionRecord } from "./database.js";

export interface MisconceptionSignal {
  misconceptionId: string;
  conceptId: string;
  prerequisiteConceptId?: string;
  confidence: number;
  explanation: string;
}

interface AssessmentResponseLike {
  type?: string;
  selectedChoiceIds?: string[];
}

export class MisconceptionDetector {
  constructor(
    private readonly database = new MisconceptionDatabase(),
  ) {}

  detectFromAttempt(args: {
    domain: string;
    items: AssessmentItem[];
    responses: AssessmentAttempt["responses"];
    feedback: AssessmentFeedback[];
  }): MisconceptionSignal[] {
    const { domain, items, responses, feedback } = args;
    const byItemId = new Map(items.map((item) => [item.id, item]));
    const feedbackByItemId = new Map(feedback.map((entry) => [entry.itemId, entry]));
    const signals: MisconceptionSignal[] = [];

    for (const [itemId, response] of Object.entries(responses ?? {})) {
      const item = byItemId.get(itemId as AssessmentItem["id"]);
      const itemFeedback = feedbackByItemId.get(itemId as AssessmentItem["id"]);

      if (!item || !itemFeedback || itemFeedback.scorePercent >= 100) {
        continue;
      }

      const metadata = extractMetadata(item.metadata);
      const topic = String(metadata.topic ?? item.prompt);
      const conceptId = String(metadata.conceptId ?? metadata.objectiveLabel ?? item.id);
      const records = this.database.findByDomainAndTopic(domain, topic);

      const matched = this.matchRecordFromResponse(records, item, response as AssessmentResponseLike);
      if (!matched) {
        continue;
      }

      const signal: MisconceptionSignal = {
        misconceptionId: matched.record.id,
        conceptId,
        confidence: matched.confidence,
        explanation: matched.record.explanation,
      };
      if (matched.record.prerequisiteConceptId) {
        signal.prerequisiteConceptId = matched.record.prerequisiteConceptId;
      }

      signals.push(signal);
    }

    return signals;
  }

  private matchRecordFromResponse(
    records: MisconceptionRecord[],
    item: AssessmentItem,
    response: AssessmentResponseLike,
  ): { record: MisconceptionRecord; confidence: number } | null {
    const selectedChoiceId = response.selectedChoiceIds?.[0];
    const selectedChoice = item.choices?.find((choice) => choice.id === selectedChoiceId);

    if (!selectedChoice) {
      return records[0]
        ? { record: records[0], confidence: 0.45 }
        : null;
    }

    const selectedLabel = selectedChoice.label.toLowerCase();
    for (const record of records) {
      if (
        selectedLabel.includes(record.distractor.toLowerCase()) ||
        record.distractor.toLowerCase().includes(selectedLabel)
      ) {
        return { record, confidence: 0.9 };
      }
    }

    return records[0] ? { record: records[0], confidence: 0.55 } : null;
  }
}

function extractMetadata(
  metadata: AssessmentItem["metadata"],
): Record<string, unknown> {
  if (metadata && typeof metadata === "object" && !Array.isArray(metadata)) {
    return metadata as Record<string, unknown>;
  }
  return {};
}
