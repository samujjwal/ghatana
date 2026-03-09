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
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for template operations.
 *
 * @doc.type class
 * @doc.purpose Template gRPC service
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class TemplateServiceImpl extends TemplateServiceGrpc.TemplateServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateServiceImpl.class);

    private final YappcApi api;

    public TemplateServiceImpl(YappcApi api) {
        this.api = api;
    }

    @Override
    public void render(RenderRequest request, StreamObserver<RenderResponse> responseObserver) {
        LOG.debug("gRPC: render");
        
        try {
            Map<String, Object> variables = new HashMap<>(request.getVariablesMap());
            String rendered = api.templates().render(request.getTemplate(), variables);
            
            responseObserver.onNext(RenderResponse.newBuilder()
                    .setSuccess(true)
                    .setOutput(rendered)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error rendering template", e);
            responseObserver.onNext(RenderResponse.newBuilder()
                    .setSuccess(false)
                    .addErrors(e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void validateSyntax(ValidateSyntaxRequest request, 
                              StreamObserver<ValidateSyntaxResponse> responseObserver) {
        LOG.debug("gRPC: validateSyntax");
        
        try {
            // Simple validation - try to parse the template
            boolean isValid = true;
            try {
                api.templates().render(request.getTemplate(), Map.of());
            } catch (Exception e) {
                // Template requires variables, but syntax is valid
                if (!e.getMessage().contains("syntax") && !e.getMessage().contains("parse")) {
                    isValid = true;
                } else {
                    isValid = false;
                }
            }
            
            responseObserver.onNext(ValidateSyntaxResponse.newBuilder()
                    .setValid(isValid)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error validating template syntax", e);
            responseObserver.onNext(ValidateSyntaxResponse.newBuilder()
                    .setValid(false)
                    .addErrors(e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getHelpers(Empty request, StreamObserver<StringListResponse> responseObserver) {
        LOG.debug("gRPC: getHelpers");
        
        try {
            // Return common handlebars helpers
            List<String> helpers = List.of(
                "upperCase", "lowerCase", "camelCase", "pascalCase", "snakeCase", 
                "kebabCase", "capitalize", "plural", "singular", "eq", "ne", 
                "gt", "lt", "and", "or", "not", "if", "unless", "each", "with"
            );
            
            responseObserver.onNext(StringListResponse.newBuilder()
                    .addAllValues(helpers)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error getting helpers", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void listTemplates(ListTemplatesRequest request, 
                             StreamObserver<TemplateListResponse> responseObserver) {
        LOG.debug("gRPC: listTemplates({})", request.getPackName());
        
        try {
            var pack = api.packs().get(request.getPackName());
            
            if (pack.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Pack not found: " + request.getPackName())
                        .asRuntimeException());
                return;
            }
            
            TemplateListResponse.Builder builder = TemplateListResponse.newBuilder();
            
            var templates = pack.get().getTemplates();
            if (templates != null) {
                for (String template : templates) {
                    builder.addTemplates(TemplateInfo.newBuilder()
                            .setName(template)
                            .setPath(template)
                            .build());
                }
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error listing templates", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
