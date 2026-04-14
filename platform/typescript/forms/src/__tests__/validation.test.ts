/**
 * @group unit
 * @tier U
 *
 * Tests for @ghatana/forms — createFormSchema and Zod integration.
 */
import { describe, it, expect } from 'vitest';
import { z } from 'zod';

import { createFormSchema } from '../validation';

describe('createFormSchema', () => {
  it('returns the exact schema object that was passed in', () => {
    const schema = z.object({ name: z.string() });
    const result = createFormSchema(schema);
    expect(result).toBe(schema);
  });

  it('the returned schema parses valid input correctly', () => {
    const schema = createFormSchema(
      z.object({
        email: z.string().email(),
        age: z.number().min(0),
      }),
    );
    const parsed = schema.parse({ email: 'user@example.com', age: 30 });
    expect(parsed.email).toBe('user@example.com');
    expect(parsed.age).toBe(30);
  });

  it('the returned schema rejects invalid input', () => {
    const schema = createFormSchema(z.object({ email: z.string().email() }));
    expect(() => schema.parse({ email: 'not-an-email' })).toThrow();
  });

  it('supports complex nested schemas', () => {
    const schema = createFormSchema(
      z.object({
        billing: z.object({
          street: z.string().min(1),
          city: z.string().min(1),
        }),
        items: z.array(z.object({ id: z.string(), qty: z.number() })).min(1),
      }),
    );
    const payload = {
      billing: { street: '123 Main St', city: 'Springfield' },
      items: [{ id: 'abc', qty: 2 }],
    };
    expect(() => schema.parse(payload)).not.toThrow();
    expect(() => schema.parse({ billing: { street: '', city: 'x' }, items: [] })).toThrow();
  });

  it('preserves refinements applied to the schema', () => {
    const schema = createFormSchema(
      z.object({ password: z.string().min(8), confirm: z.string() }).refine(
        (v) => v.password === v.confirm,
        { message: 'Passwords must match', path: ['confirm'] },
      ),
    );
    expect(() =>
      schema.parse({ password: 'password1', confirm: 'password2' }),
    ).toThrow();
    expect(() =>
      schema.parse({ password: 'password1', confirm: 'password1' }),
    ).not.toThrow();
  });
});
