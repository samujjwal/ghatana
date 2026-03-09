import eventSchema from '@/schemas/EventEnvelope.schema.json';
import ingestSchema from '@/schemas/IngestRequest.schema.json';
import { Validator } from 'jsonschema';

export class SchemaValidator {
  private validator = new Validator();

  validateEvent(envelope: unknown): { valid: boolean; errors: string[] } {
    return this.validateAgainstSchema(envelope, eventSchema as any);
  }

  validateIngestRequest(request: unknown): { valid: boolean; errors: string[] } {
    return this.validateAgainstSchema(request, ingestSchema as any);
  }

  private validateAgainstSchema(data: unknown, schema: object): {
    valid: boolean;
    errors: string[];
  } {
    const result = this.validator.validate(data, schema);
    return {
      valid: result.valid,
      errors: result.errors.map(err => err.toString())
    };
  }
}

export const validator = new SchemaValidator();
