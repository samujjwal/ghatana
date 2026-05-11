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

package com.ghatana.yappc.facades.common;

/**
 * Base interface for tenant-scoped requests in YAPPC facades.
 *
 * All facade requests that require tenant context should implement this interface
 * to ensure consistent tenant isolation across all facade operations.
 *
 * @doc.type interface
 * @doc.purpose Base interface for tenant-scoped facade requests
 * @doc.layer product
 * @doc.pattern Facade
 */
public interface TenantScopedRequest {
    
    /**
     * Get the tenant ID for this request.
     *
     * @return The tenant ID
     */
    String getTenantId();
}
