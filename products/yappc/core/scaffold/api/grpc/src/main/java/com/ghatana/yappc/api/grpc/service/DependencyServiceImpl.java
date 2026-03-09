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
import com.ghatana.yappc.api.model.DependencyAnalysis;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;

/**
 * gRPC service implementation for dependency operations.
 *
 * @doc.type class
 * @doc.purpose Dependency gRPC service
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DependencyServiceImpl extends DependencyServiceGrpc.DependencyServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyServiceImpl.class);

    private final YappcApi api;

    public DependencyServiceImpl(YappcApi api) {
        this.api = api;
    }

    @Override
    public void analyzePack(AnalyzePackRequest request, 
                           StreamObserver<com.ghatana.yappc.api.grpc.DependencyAnalysis> responseObserver) {
        LOG.debug("gRPC: analyzePack({})", request.getPackName());
        
        try {
            DependencyAnalysis result = api.dependencies().analyzePack(request.getPackName());
            
            responseObserver.onNext(toProto(result));
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error analyzing pack dependencies", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void analyzeProject(ProjectPathRequest request, 
                              StreamObserver<com.ghatana.yappc.api.grpc.DependencyAnalysis> responseObserver) {
        LOG.debug("gRPC: analyzeProject({})", request.getProjectPath());
        
        try {
            DependencyAnalysis result = api.dependencies().analyzeProject(Paths.get(request.getProjectPath()));
            
            responseObserver.onNext(toProto(result));
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error analyzing project dependencies", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void checkConflicts(CheckConflictsRequest request, 
                              StreamObserver<ConflictListResponse> responseObserver) {
        LOG.debug("gRPC: checkConflicts({} packs)", request.getPackNamesCount());
        
        try {
            List<com.ghatana.yappc.api.model.ConflictInfo> conflicts = 
                    api.dependencies().checkConflicts(request.getPackNamesList());
            
            ConflictListResponse.Builder builder = ConflictListResponse.newBuilder()
                    .setCompatible(conflicts.isEmpty());
            
            for (var conflict : conflicts) {
                builder.addConflicts(ConflictInfo.newBuilder()
                        .setType(conflict.type() != null ? conflict.type().name() : "UNKNOWN")
                        .setDescription(conflict.dependencyName() + ": " + conflict.version1() + " vs " + conflict.version2())
                        .setResolution(conflict.resolution() != null ? conflict.resolution() : "")
                        .build());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error checking conflicts", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void checkAddConflicts(CheckAddConflictsRequest request, 
                                  StreamObserver<ConflictListResponse> responseObserver) {
        LOG.debug("gRPC: checkAddConflicts({}, {})", request.getProjectPath(), request.getPackName());
        
        try {
            List<com.ghatana.yappc.api.model.ConflictInfo> conflicts = 
                    api.dependencies().checkConflicts(List.of(request.getPackName()));
            
            ConflictListResponse.Builder builder = ConflictListResponse.newBuilder()
                    .setCompatible(conflicts.isEmpty());
            
            for (var conflict : conflicts) {
                builder.addConflicts(ConflictInfo.newBuilder()
                        .setType(conflict.type() != null ? conflict.type().name() : "UNKNOWN")
                        .setDescription(conflict.dependencyName() + ": " + conflict.version1() + " vs " + conflict.version2())
                        .setResolution(conflict.resolution() != null ? conflict.resolution() : "")
                        .build());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error checking add conflicts", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private com.ghatana.yappc.api.grpc.DependencyAnalysis toProto(DependencyAnalysis analysis) {
        com.ghatana.yappc.api.grpc.DependencyAnalysis.Builder builder = 
                com.ghatana.yappc.api.grpc.DependencyAnalysis.newBuilder()
                .setHasConflicts(analysis.hasConflicts());
        
        // Add direct dependencies
        for (var dep : analysis.directDependencies()) {
            builder.addDependencies(DependencyInfo.newBuilder()
                    .setName(dep.getCoordinates())
                    .setVersion(dep.version() != null ? dep.version() : "")
                    .setScope(dep.scope() != null ? dep.scope() : "")
                    .setOptional(dep.transitive())
                    .build());
        }
        
        // Add dev dependencies (transitive)
        for (var dep : analysis.transitiveDependencies()) {
            builder.addDevDependencies(DependencyInfo.newBuilder()
                    .setName(dep.getCoordinates())
                    .setVersion(dep.version() != null ? dep.version() : "")
                    .setScope(dep.scope() != null ? dep.scope() : "")
                    .setOptional(dep.transitive())
                    .build());
        }
        
        // Add conflicts
        for (var conflict : analysis.conflicts()) {
            builder.addConflicts(ConflictInfo.newBuilder()
                    .setType(conflict.type() != null ? conflict.type().name() : "UNKNOWN")
                    .setDescription(conflict.dependencyName() + ": " + conflict.version1() + " vs " + conflict.version2())
                    .setResolution(conflict.resolution() != null ? conflict.resolution() : "")
                    .build());
        }
        
        return builder.build();
    }
}
