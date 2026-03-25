/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.error;

import java.util.Map;

/**
 * Template processing exceptions for template not found, processing failures, and variable
 * substitution errors.
 *
 * <p>Week 10 Day 50: Phase 4 architectural improvements - Error Handling Strategy
 *
 * @doc.type class
 * @doc.purpose Template processing exceptions for template not found, processing failures, and variable
 * @doc.layer platform
 * @doc.pattern Component
 */
public class TemplateException extends YappcException {

    public TemplateException(String message) {
        super(BaseErrorCode.TEMPLATE_PROCESSING_FAILED, message, ErrorSeverity.HIGH);
    }

    public TemplateException(String message, Throwable cause) {
        super(BaseErrorCode.TEMPLATE_PROCESSING_FAILED, message, ErrorSeverity.HIGH, cause);
    }

    public TemplateException(BaseErrorCode errorCode, String message) {
        super(errorCode, message, errorCode.getDefaultSeverity());
    }

    public TemplateException(BaseErrorCode errorCode, String message, Map<String, Object> context) {
        super(errorCode, message, errorCode.getDefaultSeverity(), null, context);
    }
}
