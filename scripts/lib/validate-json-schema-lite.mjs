function isObject(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function jsonTypeOf(value) {
  if (value === null) {
    return 'null';
  }
  if (Array.isArray(value)) {
    return 'array';
  }
  return typeof value;
}

function escapeJsonPointerToken(token) {
  return String(token).replace(/~/g, '~0').replace(/\//g, '~1');
}

function resolveJsonPointer(rootSchema, ref) {
  if (typeof ref !== 'string' || !ref.startsWith('#/')) {
    return null;
  }

  const parts = ref
    .slice(2)
    .split('/')
    .map((part) => part.replace(/~1/g, '/').replace(/~0/g, '~'));

  let current = rootSchema;
  for (const part of parts) {
    if (!isObject(current) || !(part in current)) {
      return null;
    }
    current = current[part];
  }

  return current;
}

function matchesType(value, typeName) {
  switch (typeName) {
    case 'object':
      return isObject(value);
    case 'array':
      return Array.isArray(value);
    case 'string':
      return typeof value === 'string';
    case 'number':
      return typeof value === 'number' && Number.isFinite(value);
    case 'integer':
      return typeof value === 'number' && Number.isInteger(value);
    case 'boolean':
      return typeof value === 'boolean';
    case 'null':
      return value === null;
    default:
      return true;
  }
}

function isValidDate(value) {
  if (typeof value !== 'string') {
    return false;
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const timestamp = Date.parse(value);
  return !Number.isNaN(timestamp);
}

function normalizeSchema(schema, rootSchema) {
  if (!isObject(schema)) {
    return null;
  }

  if (schema.$ref) {
    const resolved = resolveJsonPointer(rootSchema, schema.$ref);
    return resolved ? normalizeSchema(resolved, rootSchema) : null;
  }

  return schema;
}

function validateInternal(value, schema, context, path, errors) {
  const normalizedSchema = normalizeSchema(schema, context.rootSchema);
  if (!normalizedSchema) {
    errors.push({
      path,
      message: 'invalid schema reference',
      expected: schema?.$ref ?? 'valid schema object',
      actual: 'unresolvable-schema',
    });
    return;
  }

  if (Array.isArray(normalizedSchema.allOf)) {
    for (const childSchema of normalizedSchema.allOf) {
      validateInternal(value, childSchema, context, path, errors);
    }
  }

  if (isObject(normalizedSchema.if)) {
    const conditionalErrors = [];
    validateInternal(value, normalizedSchema.if, context, path, conditionalErrors);
    if (conditionalErrors.length === 0) {
      if (isObject(normalizedSchema.then)) {
        validateInternal(value, normalizedSchema.then, context, path, errors);
      }
    } else if (isObject(normalizedSchema.else)) {
      validateInternal(value, normalizedSchema.else, context, path, errors);
    }
  }

  if (isObject(normalizedSchema.not)) {
    const notErrors = [];
    validateInternal(value, normalizedSchema.not, context, path, notErrors);
    if (notErrors.length === 0) {
      errors.push({
        path,
        message: 'value must not match forbidden schema',
        expected: 'schema mismatch',
        actual: value,
      });
    }
  }

  if ('const' in normalizedSchema && value !== normalizedSchema.const) {
    errors.push({
      path,
      message: 'value must equal const',
      expected: normalizedSchema.const,
      actual: value,
    });
  }

  if (Array.isArray(normalizedSchema.enum) && !normalizedSchema.enum.some((entry) => entry === value)) {
    errors.push({
      path,
      message: 'value must match enum',
      expected: normalizedSchema.enum,
      actual: value,
    });
  }

  if (normalizedSchema.type) {
    const allowedTypes = Array.isArray(normalizedSchema.type)
      ? normalizedSchema.type
      : [normalizedSchema.type];
    const typeMatch = allowedTypes.some((typeName) => matchesType(value, typeName));
    if (!typeMatch) {
      errors.push({
        path,
        message: 'value has invalid type',
        expected: allowedTypes,
        actual: jsonTypeOf(value),
      });
      return;
    }
  }

  if (typeof value === 'string') {
    if (typeof normalizedSchema.minLength === 'number' && value.length < normalizedSchema.minLength) {
      errors.push({
        path,
        message: 'string shorter than minLength',
        expected: normalizedSchema.minLength,
        actual: value.length,
      });
    }

    if (typeof normalizedSchema.pattern === 'string') {
      const pattern = new RegExp(normalizedSchema.pattern);
      if (!pattern.test(value)) {
        errors.push({
          path,
          message: 'string does not match pattern',
          expected: normalizedSchema.pattern,
          actual: value,
        });
      }
    }

    if (normalizedSchema.format === 'date' && !isValidDate(value)) {
      errors.push({
        path,
        message: 'string is not a valid date',
        expected: 'YYYY-MM-DD',
        actual: value,
      });
    }
  }

  if (Array.isArray(value)) {
    if (typeof normalizedSchema.minItems === 'number' && value.length < normalizedSchema.minItems) {
      errors.push({
        path,
        message: 'array shorter than minItems',
        expected: normalizedSchema.minItems,
        actual: value.length,
      });
    }

    if (normalizedSchema.items) {
      value.forEach((item, index) => {
        validateInternal(item, normalizedSchema.items, context, `${path}/${index}`, errors);
      });
    }
  }

  if (isObject(value)) {
    const properties = isObject(normalizedSchema.properties) ? normalizedSchema.properties : {};
    const required = Array.isArray(normalizedSchema.required) ? normalizedSchema.required : [];

    required.forEach((requiredField) => {
      if (!(requiredField in value)) {
        errors.push({
          path: `${path}/${escapeJsonPointerToken(requiredField)}`,
          message: 'required property is missing',
          expected: 'present',
          actual: 'missing',
        });
      }
    });

    for (const [propertyName, propertySchema] of Object.entries(properties)) {
      if (propertyName in value) {
        validateInternal(
          value[propertyName],
          propertySchema,
          context,
          `${path}/${escapeJsonPointerToken(propertyName)}`,
          errors,
        );
      }
    }

    const additionalProperties = normalizedSchema.additionalProperties;
    if (additionalProperties === false) {
      for (const key of Object.keys(value)) {
        if (!(key in properties)) {
          errors.push({
            path: `${path}/${escapeJsonPointerToken(key)}`,
            message: 'additional property is not allowed',
            expected: 'property declared in schema',
            actual: key,
          });
        }
      }
    } else if (isObject(additionalProperties)) {
      for (const key of Object.keys(value)) {
        if (!(key in properties)) {
          validateInternal(
            value[key],
            additionalProperties,
            context,
            `${path}/${escapeJsonPointerToken(key)}`,
            errors,
          );
        }
      }
    }
  }
}

export function validateJsonSchemaLite(schema, document) {
  const errors = [];
  validateInternal(document, schema, { rootSchema: schema }, '#', errors);
  return errors;
}