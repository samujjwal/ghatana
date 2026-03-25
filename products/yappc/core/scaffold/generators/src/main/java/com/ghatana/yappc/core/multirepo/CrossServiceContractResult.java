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

package com.ghatana.yappc.core.multirepo;

import java.util.List;
import java.util.Map;

/**
 * Result of generating cross-service API contracts and client libraries.
 *
 * <p>Week 7 Day 35: Cross-service contract generation for multi-language integration.
 *
 * @doc.type record
 * @doc.purpose Result of generating cross-service API contracts and client libraries.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record CrossServiceContractResult(
        List<String> generatedContracts,
        Map<String, String> apiSchemas,
        Map<String, String> clientLibraries,
        Map<String, String> typeDefinitions,
        List<String> communicationProtocols,
        Map<String, Object> contractMetadata) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> generatedContracts = List.of();
        private Map<String, String> apiSchemas = Map.of();
        private Map<String, String> clientLibraries = Map.of();
        private Map<String, String> typeDefinitions = Map.of();
        private List<String> communicationProtocols = List.of();
        private Map<String, Object> contractMetadata = Map.of();

        public Builder generatedContracts(List<String> generatedContracts) {
            this.generatedContracts = generatedContracts;
            return this;
        }

        public Builder apiSchemas(Map<String, String> apiSchemas) {
            this.apiSchemas = apiSchemas;
            return this;
        }

        public Builder clientLibraries(Map<String, String> clientLibraries) {
            this.clientLibraries = clientLibraries;
            return this;
        }

        public Builder typeDefinitions(Map<String, String> typeDefinitions) {
            this.typeDefinitions = typeDefinitions;
            return this;
        }

        public Builder communicationProtocols(List<String> communicationProtocols) {
            this.communicationProtocols = communicationProtocols;
            return this;
        }

        public Builder contractMetadata(Map<String, Object> contractMetadata) {
            this.contractMetadata = contractMetadata;
            return this;
        }

        public CrossServiceContractResult build() {
            return new CrossServiceContractResult(
                    generatedContracts,
                    apiSchemas,
                    clientLibraries,
                    typeDefinitions,
                    communicationProtocols,
                    contractMetadata);
        }
    }
}
