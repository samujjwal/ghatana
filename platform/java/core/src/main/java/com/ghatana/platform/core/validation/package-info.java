/**
 * Centralized validation framework for consistent validation across the platform.
 * 
 * <p>This package provides a fluent validation API with common validators for
 * standard formats and patterns. All validation logic should use this framework
 * for consistency and maintainability.</p>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.ghatana.platform.core.validation.ValidationFramework} - Fluent validation API</li>
 *   <li>{@link com.ghatana.platform.core.validation.CommonValidators} - Pre-built validators</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ValidationResult result = ValidationFramework.validate()
 *     .field("email", user.getEmail())
 *         .notNull("Email is required")
 *         .matches(CommonValidators.EMAIL, "Invalid email format")
 *     .field("age", user.getAge())
 *         .notNull("Age is required")
 *         .min(18, "Must be 18 or older")
 *     .field("phone", user.getPhone())
 *         .matches(CommonValidators.PHONE_US, "Invalid phone number")
 *     .build();
 * 
 * if (!result.isValid()) {
 *     throw new ValidationException(result.getErrorMessages());
 * }
 * }</pre>
 * 
 * @see com.ghatana.platform.core.validation.ValidationFramework
 * @see com.ghatana.platform.core.validation.CommonValidators
 * @since 1.0.0
 */
package com.ghatana.platform.core.validation;
