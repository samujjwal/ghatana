/**
 * Platform core utilities: validation, string/date/collection/JSON helpers, and result types.
 *
 * <h2>Available Utilities</h2>
 *
 * <ul>
 * <li>{@link com.ghatana.platform.core.util.ValidationUtils} — boundary-guard validators
 *     that throw {@code IllegalArgumentException} for null, blank, out-of-range, and
 *     oversized inputs. Use at service request entry points.</li>
 * <li>{@link com.ghatana.platform.core.util.CollectionUtils} — null-safe collection
 *     helpers: {@code isEmpty}, {@code emptyIfNull}, {@code mapList}, {@code partition},
 *     {@code intersection}. Prefer over inline null checks.</li>
 * <li>{@link com.ghatana.platform.core.util.JsonUtils} — shared, pre-configured
 *     {@code ObjectMapper} singleton. Use {@code JsonUtils.getDefaultMapper()} instead of
 *     {@code new ObjectMapper()} anywhere in the codebase.</li>
 * <li>{@link com.ghatana.platform.core.util.StringUtils} — {@code isBlank},
 *     {@code truncate}, {@code capitalize}, {@code toCamelCase}, and related helpers.</li>
 * <li>{@link com.ghatana.platform.core.util.DateTimeUtils} — epoch/ISO/duration
 *     conversions, {@code now()} variants, and time-zone-aware formatters.</li>
 * <li>{@link com.ghatana.platform.core.util.Preconditions} — lightweight pre-condition
 *     checks ({@code checkNotNull}, {@code checkArgument}, {@code checkState}).</li>
 * </ul>
 *
 * <h2>Input Validation Pattern (VAL-001)</h2>
 *
 * <p>Validate all untrusted inputs at the HTTP handler/service boundary, not deep inside
 * domain logic:</p>
 *
 * <pre>{@code
 * // At the request handler:
 * ValidationUtils.validateNotBlank(request.tenantId(), "tenantId");
 * ValidationUtils.validateMaxLength(request.name(), 256, "name");
 * ValidationUtils.validateRange(request.pageSize(), 1, 200, "pageSize");
 * }</pre>
 *
 * <h2>Business Logic Validation Pattern (VAL-002)</h2>
 *
 * <p>Use {@link com.ghatana.platform.core.util.Preconditions} for invariant assertions
 * inside domain logic, keeping the exception distinct from boundary-level
 * {@code IllegalArgumentException}:</p>
 *
 * <pre>{@code
 * Preconditions.checkState(account.isActive(), "Account must be active to post");
 * Preconditions.checkNotNull(tenantId, "tenantId must be resolved before use");
 * }</pre>
 *
 * <h2>JSON Serialization Pattern (JSON-001 / JSON-002)</h2>
 *
 * <p>Always use the shared mapper from {@code JsonUtils} — never create a bare
 * {@code new ObjectMapper()} in production code:</p>
 *
 * <pre>{@code
 * // Serialize:
 * String json = JsonUtils.toJson(myObject);
 *
 * // Deserialize — wraps IOException into RuntimeException:
 * MyDto dto = JsonUtils.fromJson(json, MyDto.class);
 *
 * // Safe deserialize — returns Optional.empty() on parse failure:
 * Optional<MyDto> safe = JsonUtils.fromJsonSafe(json, MyDto.class);
 *
 * // Access the mapper directly for advanced operations:
 * ObjectMapper mapper = JsonUtils.getDefaultMapper();
 * }</pre>
 *
 * <h2>Data Transformation and Validation (DATA-001 / DATA-002)</h2>
 *
 * <p>Combine {@code ValidationUtils}, {@code CollectionUtils}, and {@code JsonUtils}
 * for data ingestion pipelines:</p>
 * <pre>{@code
 * List<InputRecord> safe = CollectionUtils.toNonNullList(rawRecords);
 * List<ValidRecord> validated = CollectionUtils.mapList(safe, record -> {
 *     ValidationUtils.validateNotBlank(record.id(), "record.id");
 *     return transform(record);
 * });
 * }</pre>
 */
package com.ghatana.platform.core.util;
