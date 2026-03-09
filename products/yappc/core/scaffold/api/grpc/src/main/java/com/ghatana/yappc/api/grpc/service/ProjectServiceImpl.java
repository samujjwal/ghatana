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
import com.ghatana.yappc.api.model.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * gRPC service implementation for project operations.
 *
 * @doc.type class
 * @doc.purpose Project gRPC service
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class ProjectServiceImpl extends ProjectServiceGrpc.ProjectServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final YappcApi api;

    public ProjectServiceImpl(YappcApi api) {
        this.api = api;
    }

    @Override
    public void createProject(CreateProjectRequest request, 
                             StreamObserver<CreateProjectResponse> responseObserver) {
        LOG.info("gRPC: createProject({}, {})", request.getProjectName(), request.getPackName());
        
        try {
            Map<String, Object> variables = new HashMap<>(request.getVariablesMap());
            
            com.ghatana.yappc.api.model.CreateRequest createRequest = 
                    com.ghatana.yappc.api.model.CreateRequest.builder()
                    .packName(request.getPackName())
                    .projectName(request.getProjectName())
                    .outputPath(Paths.get(request.getOutputPath()))
                    .variables(variables)
                    .overwrite(request.getOverwrite())
                    .dryRun(request.getDryRun())
                    .build();
            
            CreateResult result = api.projects().create(createRequest);
            
            CreateProjectResponse.Builder builder = CreateProjectResponse.newBuilder()
                    .setSuccess(result.isSuccess())
                    .setProjectPath(result.getProjectPath() != null ? result.getProjectPath().toString() : "")
                    .setDurationMs(result.getDurationMs());
            
            if (result.getFilesCreated() != null) {
                builder.addAllFilesCreated(result.getFilesCreated());
            }
            if (result.getWarnings() != null) {
                builder.addAllWarnings(result.getWarnings());
            }
            if (result.getErrorMessage() != null) {
                builder.setErrorMessage(result.getErrorMessage());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error creating project", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void createProjectWithProgress(CreateProjectRequest request, 
                                          StreamObserver<ProgressUpdate> responseObserver) {
        LOG.info("gRPC: createProjectWithProgress({}, {})", request.getProjectName(), request.getPackName());
        
        try {
            responseObserver.onNext(ProgressUpdate.newBuilder()
                    .setStep("Starting")
                    .setCurrent(0)
                    .setTotal(100)
                    .setMessage("Initializing project creation...")
                    .setPercent(0.0)
                    .build());

            Map<String, Object> variables = new HashMap<>(request.getVariablesMap());
            
            com.ghatana.yappc.api.model.CreateRequest createRequest = 
                    com.ghatana.yappc.api.model.CreateRequest.builder()
                    .packName(request.getPackName())
                    .projectName(request.getProjectName())
                    .outputPath(Paths.get(request.getOutputPath()))
                    .variables(variables)
                    .overwrite(request.getOverwrite())
                    .dryRun(request.getDryRun())
                    .build();
            
            responseObserver.onNext(ProgressUpdate.newBuilder()
                    .setStep("Loading pack")
                    .setCurrent(20)
                    .setTotal(100)
                    .setMessage("Loading pack: " + request.getPackName())
                    .setPercent(20.0)
                    .build());

            CreateResult result = api.projects().create(createRequest);
            
            responseObserver.onNext(ProgressUpdate.newBuilder()
                    .setStep("Complete")
                    .setCurrent(100)
                    .setTotal(100)
                    .setMessage(result.isSuccess() ? "Project created successfully" : "Project creation failed")
                    .setPercent(100.0)
                    .build());
            
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error creating project with progress", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void addFeature(com.ghatana.yappc.api.grpc.AddFeatureRequest request, 
                          StreamObserver<AddFeatureResponse> responseObserver) {
        LOG.info("gRPC: addFeature({}, {})", request.getFeature(), request.getProjectPath());
        
        try {
            Map<String, Object> variables = new HashMap<>(request.getVariablesMap());
            
            com.ghatana.yappc.api.model.AddFeatureRequest addRequest = 
                    com.ghatana.yappc.api.model.AddFeatureRequest.builder()
                    .projectPath(Paths.get(request.getProjectPath()))
                    .feature(request.getFeature())
                    .type(request.getType())
                    .variables(variables)
                    .force(request.getForce())
                    .dryRun(request.getDryRun())
                    .build();
            
            AddResult result = api.projects().addFeature(addRequest);
            
            AddFeatureResponse.Builder builder = AddFeatureResponse.newBuilder()
                    .setSuccess(result.isSuccess())
                    .setDurationMs(result.getDurationMs());
            
            if (result.getFilesCreated() != null) {
                builder.addAllFilesCreated(result.getFilesCreated());
            }
            if (result.getFilesModified() != null) {
                builder.addAllFilesModified(result.getFilesModified());
            }
            if (result.getWarnings() != null) {
                builder.addAllWarnings(result.getWarnings());
            }
            if (result.getErrorMessage() != null) {
                builder.setErrorMessage(result.getErrorMessage());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error adding feature", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void addFeatureWithProgress(com.ghatana.yappc.api.grpc.AddFeatureRequest request, 
                                       StreamObserver<ProgressUpdate> responseObserver) {
        LOG.info("gRPC: addFeatureWithProgress({}, {})", request.getFeature(), request.getProjectPath());
        
        try {
            responseObserver.onNext(ProgressUpdate.newBuilder()
                    .setStep("Starting")
                    .setCurrent(0)
                    .setTotal(100)
                    .setMessage("Adding feature: " + request.getFeature())
                    .setPercent(0.0)
                    .build());

            Map<String, Object> variables = new HashMap<>(request.getVariablesMap());
            
            com.ghatana.yappc.api.model.AddFeatureRequest addRequest = 
                    com.ghatana.yappc.api.model.AddFeatureRequest.builder()
                    .projectPath(Paths.get(request.getProjectPath()))
                    .feature(request.getFeature())
                    .type(request.getType())
                    .variables(variables)
                    .force(request.getForce())
                    .dryRun(request.getDryRun())
                    .build();
            
            AddResult result = api.projects().addFeature(addRequest);
            
            responseObserver.onNext(ProgressUpdate.newBuilder()
                    .setStep("Complete")
                    .setCurrent(100)
                    .setTotal(100)
                    .setMessage(result.isSuccess() ? "Feature added successfully" : "Feature addition failed")
                    .setPercent(100.0)
                    .build());
            
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error adding feature with progress", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void updateProject(UpdateProjectRequest request, 
                             StreamObserver<UpdateProjectResponse> responseObserver) {
        LOG.info("gRPC: updateProject({})", request.getProjectPath());
        
        try {
            com.ghatana.yappc.api.model.UpdateRequest updateRequest = 
                    com.ghatana.yappc.api.model.UpdateRequest.builder()
                    .projectPath(Paths.get(request.getProjectPath()))
                    .dryRun(request.getDryRun())
                    .force(request.getForce())
                    .backup(request.getBackup())
                    .build();
            
            UpdateResult result = api.projects().update(updateRequest);
            
            UpdateProjectResponse.Builder builder = UpdateProjectResponse.newBuilder()
                    .setSuccess(result.isSuccess())
                    .setFromVersion(result.getFromVersion() != null ? result.getFromVersion() : "")
                    .setToVersion(result.getToVersion() != null ? result.getToVersion() : "")
                    .setDurationMs(result.getDurationMs());
            
            if (result.getFilesUpdated() != null) {
                builder.addAllFilesUpdated(result.getFilesUpdated());
            }
            if (result.getFilesAdded() != null) {
                builder.addAllFilesAdded(result.getFilesAdded());
            }
            if (result.getFilesRemoved() != null) {
                builder.addAllFilesRemoved(result.getFilesRemoved());
            }
            if (result.getConflicts() != null) {
                builder.addAllConflicts(result.getConflicts());
            }
            if (result.getErrorMessage() != null) {
                builder.setErrorMessage(result.getErrorMessage());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error updating project", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getProjectInfo(ProjectPathRequest request, 
                              StreamObserver<com.ghatana.yappc.api.grpc.ProjectInfo> responseObserver) {
        LOG.debug("gRPC: getProjectInfo({})", request.getProjectPath());
        
        try {
            Optional<com.ghatana.yappc.api.model.ProjectInfo> info = 
                    api.projects().getInfo(Paths.get(request.getProjectPath()));
            
            if (info.isPresent()) {
                com.ghatana.yappc.api.model.ProjectInfo p = info.get();
                com.ghatana.yappc.api.grpc.ProjectInfo.Builder builder = 
                        com.ghatana.yappc.api.grpc.ProjectInfo.newBuilder()
                        .setProjectPath(p.getProjectPath() != null ? p.getProjectPath().toString() : "")
                        .setPackName(p.getPackName() != null ? p.getPackName() : "")
                        .setPackVersion(p.getPackVersion() != null ? p.getPackVersion() : "");
                
                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Not a YAPPC project: " + request.getProjectPath())
                        .asRuntimeException());
            }
        } catch (Exception e) {
            LOG.error("Error getting project info", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getProjectState(ProjectPathRequest request, 
                               StreamObserver<com.ghatana.yappc.api.grpc.ProjectState> responseObserver) {
        LOG.debug("gRPC: getProjectState({})", request.getProjectPath());
        
        try {
            Optional<com.ghatana.yappc.api.model.ProjectState> state = 
                    api.projects().getState(Paths.get(request.getProjectPath()));
            
            if (state.isPresent()) {
                com.ghatana.yappc.api.model.ProjectState s = state.get();
                com.ghatana.yappc.api.grpc.ProjectState.Builder builder = 
                        com.ghatana.yappc.api.grpc.ProjectState.newBuilder()
                        .setPackName(s.getPackName() != null ? s.getPackName() : "")
                        .setPackVersion(s.getPackVersion() != null ? s.getPackVersion() : "");
                
                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Not a YAPPC project: " + request.getProjectPath())
                        .asRuntimeException());
            }
        } catch (Exception e) {
            LOG.error("Error getting project state", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void validateProject(ProjectPathRequest request, 
                               StreamObserver<com.ghatana.yappc.api.grpc.ProjectValidationResult> responseObserver) {
        LOG.debug("gRPC: validateProject({})", request.getProjectPath());
        
        try {
            com.ghatana.yappc.api.model.ProjectValidationResult result = 
                    api.projects().validate(Paths.get(request.getProjectPath()));
            
            com.ghatana.yappc.api.grpc.ProjectValidationResult.Builder builder = 
                    com.ghatana.yappc.api.grpc.ProjectValidationResult.newBuilder()
                    .setValid(result.isValid())
                    .setIsYappcProject(result.isYappcProject());
            
            if (result.errors() != null) {
                builder.addAllErrors(result.errors());
            }
            if (result.warnings() != null) {
                builder.addAllWarnings(result.warnings());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error validating project", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void checkUpdates(ProjectPathRequest request, 
                            StreamObserver<com.ghatana.yappc.api.grpc.UpdateAvailability> responseObserver) {
        LOG.debug("gRPC: checkUpdates({})", request.getProjectPath());
        
        try {
            com.ghatana.yappc.api.model.UpdateAvailability result = 
                    api.projects().checkUpdates(Paths.get(request.getProjectPath()));
            
            com.ghatana.yappc.api.grpc.UpdateAvailability.Builder builder = 
                    com.ghatana.yappc.api.grpc.UpdateAvailability.newBuilder()
                    .setUpdateAvailable(result.updateAvailable())
                    .setCurrentVersion(result.currentVersion() != null ? result.currentVersion() : "")
                    .setLatestVersion(result.latestVersion() != null ? result.latestVersion() : "");
            
            if (result.changesSummary() != null) {
                builder.addAllChanges(result.changesSummary());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error checking updates", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getProjectFeatures(ProjectPathRequest request, 
                                   StreamObserver<FeatureListResponse> responseObserver) {
        LOG.debug("gRPC: getProjectFeatures({})", request.getProjectPath());
        
        try {
            List<com.ghatana.yappc.api.model.FeatureInfo> features = 
                    api.projects().getAddedFeatures(Paths.get(request.getProjectPath()));
            
            FeatureListResponse.Builder builder = FeatureListResponse.newBuilder();
            
            for (com.ghatana.yappc.api.model.FeatureInfo feature : features) {
                builder.addFeatures(com.ghatana.yappc.api.grpc.FeatureInfo.newBuilder()
                        .setName(feature.name() != null ? feature.name() : "")
                        .setType(feature.defaultType() != null ? feature.defaultType() : "")
                        .build());
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error getting project features", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void exportState(ProjectPathRequest request, 
                           StreamObserver<ExportStateResponse> responseObserver) {
        LOG.info("gRPC: exportState({})", request.getProjectPath());
        
        try {
            String stateJson = api.projects().exportState(Paths.get(request.getProjectPath()));
            
            responseObserver.onNext(ExportStateResponse.newBuilder()
                    .setSuccess(true)
                    .setStateJson(stateJson)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error exporting state", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void importState(ImportStateRequest request, 
                           StreamObserver<ImportStateResponse> responseObserver) {
        LOG.info("gRPC: importState({})", request.getProjectPath());
        
        try {
            api.projects().importState(Paths.get(request.getProjectPath()), request.getStateJson());
            
            responseObserver.onNext(ImportStateResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Project state imported successfully")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error importing state", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
