/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.core.docs.adr.template;

import com.ghatana.yappc.core.docs.adr.model.ADREnums.ADRTemplateType;

import java.util.Map;

/**
 * Contract for ADR templates.
 * Each template provides a specific format for rendering Architecture Decision Records.
 *
 * @doc.type interface
 * @doc.purpose Contract for ADR templates.
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface ADRTemplate {
    
    /**
 * Returns the type of this template. */
    ADRTemplateType templateType();

    /**
 * 
     * Generates ADR content from the provided data.
     * 
     * @param data Template data including request, analysis, and metadata
     * @return Formatted ADR content as a string
     */
    String generate(Map<String, Object> data);
}
