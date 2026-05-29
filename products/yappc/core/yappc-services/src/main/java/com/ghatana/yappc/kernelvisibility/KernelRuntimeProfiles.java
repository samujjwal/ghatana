/*
 * Copyright (c) 2026 Ghatana Platform Contributors
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

package com.ghatana.yappc.kernelvisibility;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

final class KernelRuntimeProfiles {

    private KernelRuntimeProfiles() {
    }

    static boolean isProductionRuntime() {
        return isProductionValue(System.getProperty("ghatana.runtime.profile"))
                || isProductionValue(System.getProperty("yappc.runtime.profile"))
                || isProductionValue(System.getenv("GHATANA_RUNTIME_PROFILE"))
                || isProductionValue(System.getenv("GHATANA_ENV"))
                || isProductionValue(System.getenv("SPRING_PROFILES_ACTIVE"));
    }

    private static boolean isProductionValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String part : value.split("[,;\\s]+")) {
            String normalized = part.trim().toLowerCase(Locale.ROOT);
            if ("production".equals(normalized) || "prod".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
