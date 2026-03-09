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

package com.ghatana.yappc.api.grpc.service;

import com.ghatana.yappc.api.YappcApi;
import com.ghatana.yappc.api.grpc.*;
import com.ghatana.yappc.api.model.PackInfo;
import com.ghatana.yappc.api.model.PackValidationResult;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for pack operations.
 *
 * @doc.type class
 * @doc.purpose Pack gRPC service
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class PackServiceImpl extends PackServiceGrpc.PackServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(PackServiceImpl.class);

    private final YappcApi api;

    public PackServiceImpl(YappcApi api) {
        this.api = api;
    }

    @Override
    public void listPacks(ListPacksRequest request, StreamObserver<ListPacksResponse> responseObserver) {
        LOG.debug("gRPC: listPacks");
        
        try {
            List<PackInfo> packs;
            
            if (!request.getLanguage().isEmpty()) {
                packs = api.packs().byLanguage(request.getLanguage());
            } else if (!request.getCategory().isEmpty()) {
                packs = api.packs().byCategory(request.getCategory());
            } else if (!request.getPlatform().isEmpty()) {
                packs = api.packs().byPlatform(request.getPlatform());
            } else if (!request.getSearchQuery().isEmpty()) {
                packs = api.packs().search(request.getSearchQuery());
            } else {
                packs = api.packs().list();
            }

            ListPacksResponse.Builder responseBuilder = ListPacksResponse.newBuilder()
                    .setTotal(packs.size())
                    .setPage(request.getPage())
                    .setPageSize(request.getPageSize());

            for (PackInfo pack : packs) {
                responseBuilder.addPacks(toProto(pack));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error listing packs", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPack(GetPackRequest request, StreamObserver<com.ghatana.yappc.api.grpc.PackInfo> responseObserver) {
        LOG.debug("gRPC: getPack({})", request.getName());
        
        try {
            Optional<PackInfo> pack = api.packs().get(request.getName());
            
            if (pack.isPresent()) {
                responseObserver.onNext(toProto(pack.get()));
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Pack not found: " + request.getName())
                        .asRuntimeException());
            }
        } catch (Exception e) {
            LOG.error("Error getting pack", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void validatePack(ValidatePackRequest request, 
                            StreamObserver<com.ghatana.yappc.api.grpc.PackValidationResult> responseObserver) {
        LOG.debug("gRPC: validatePack({})", request.getName());
        
        try {
            PackValidationResult result = api.packs().validate(request.getName());
            
            com.ghatana.yappc.api.grpc.PackValidationResult.Builder builder = 
                    com.ghatana.yappc.api.grpc.PackValidationResult.newBuilder()
                    .setValid(result.isValid())
                    .setTemplateCount(result.getTemplateCount());
            
            // Convert errors to strings
            List<String> errorMessages = result.getErrors().stream()
                    .map(err -> err.message())
                    .collect(Collectors.toList());
            builder.addAllErrors(errorMessages);
            
            // Convert warnings to strings  
            List<String> warningMessages = result.getWarnings().stream()
                    .map(warn -> warn.message())
                    .collect(Collectors.toList());
            builder.addAllWarnings(warningMessages);
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error validating pack", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getLanguages(Empty request, StreamObserver<StringListResponse> responseObserver) {
        LOG.debug("gRPC: getLanguages");
        
        try {
            List<String> languages = api.packs().getAvailableLanguages();
            
            responseObserver.onNext(StringListResponse.newBuilder()
                    .addAllValues(languages)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error getting languages", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getCategories(Empty request, StreamObserver<StringListResponse> responseObserver) {
        LOG.debug("gRPC: getCategories");
        
        try {
            List<String> categories = api.packs().getAvailableCategories();
            
            responseObserver.onNext(StringListResponse.newBuilder()
                    .addAllValues(categories)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error getting categories", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPlatforms(Empty request, StreamObserver<StringListResponse> responseObserver) {
        LOG.debug("gRPC: getPlatforms");
        
        try {
            List<String> platforms = api.packs().getAvailablePlatforms();
            
            responseObserver.onNext(StringListResponse.newBuilder()
                    .addAllValues(platforms)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error getting platforms", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void refreshCache(Empty request, StreamObserver<RefreshResponse> responseObserver) {
        LOG.info("gRPC: refreshCache");
        
        try {
            api.packs().refresh();
            
            responseObserver.onNext(RefreshResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Pack cache refreshed successfully")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error refreshing cache", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private com.ghatana.yappc.api.grpc.PackInfo toProto(PackInfo pack) {
        com.ghatana.yappc.api.grpc.PackInfo.Builder builder = com.ghatana.yappc.api.grpc.PackInfo.newBuilder()
                .setName(pack.getName() != null ? pack.getName() : "")
                .setVersion(pack.getVersion() != null ? pack.getVersion() : "")
                .setDescription(pack.getDescription() != null ? pack.getDescription() : "")
                .setLanguage(pack.getLanguage() != null ? pack.getLanguage() : "")
                .setCategory(pack.getCategory() != null ? pack.getCategory() : "")
                .setPlatform(pack.getPlatform() != null ? pack.getPlatform() : "")
                .setBuildSystem(pack.getBuildSystem() != null ? pack.getBuildSystem() : "")
                .setArchetype(pack.getArchetype() != null ? pack.getArchetype() : "")
                .setIsComposition(pack.isComposition());
        
        if (pack.getTemplates() != null) {
            builder.addAllTemplates(pack.getTemplates());
        }
        if (pack.getRequiredVariables() != null) {
            builder.addAllRequiredVariables(pack.getRequiredVariables());
        }
        if (pack.getOptionalVariables() != null) {
            builder.addAllOptionalVariables(pack.getOptionalVariables());
        }
        if (pack.getDefaults() != null) {
            builder.putAllDefaults(pack.getDefaults());
        }
        if (pack.getComposedPacks() != null) {
            builder.addAllComposedPacks(pack.getComposedPacks());
        }
        
        return builder.build();
    }
}
