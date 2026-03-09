package com.ghatana.tutorputor.ai.service;

import com.ghatana.ai.gateway.AIGateway;
import com.ghatana.tutorputor.contracts.ai.AiLearningProto;
import com.ghatana.tutorputor.contracts.ai.AiLearningServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.util.UUID;

/**
 * Implementation of AI Learning gRPC service.
 */
public class AiLearningServiceImpl extends AiLearningServiceGrpc.AiLearningServiceImplBase {

    private final AIGateway aiGateway;

    public AiLearningServiceImpl(AIGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    @Override
    public void generateLearningPath(
            AiLearningProto.GeneratePathRequest request,
            StreamObserver<AiLearningProto.GeneratePathResponse> responseObserver) {
        String subject = request.getSubject().isBlank() ? "General" : request.getSubject();
        String goal = request.getGoal().isBlank() ? "Learning Goal" : request.getGoal();
        String learnerLevel = request.getLearnerLevel().isBlank() ? "Intermediate" : request.getLearnerLevel();

        String prompt = String.format(
                "Create learning path for subject=%s, goal=%s, learnerLevel=%s",
                subject, goal, learnerLevel
        );

        aiGateway.generatePattern(prompt).whenComplete((generatedPlan, error) -> {
            if (error != null) {
                responseObserver.onError(error);
                return;
            }

            String description = (generatedPlan != null && !generatedPlan.isBlank())
                    ? generatedPlan
                    : "AI-generated learning path for " + goal;

            AiLearningProto.GeneratePathResponse response = AiLearningProto.GeneratePathResponse.newBuilder()
                    .setPathId(UUID.randomUUID().toString())
                    .setTitle(subject + " Mastery")
                    .setDescription(description)
                    .addNodes(AiLearningProto.LearningNode.newBuilder()
                            .setId("node-1")
                            .setTitle("Foundations")
                            .setType("READING")
                            .setDescription("Build core knowledge required for " + subject)
                            .setEstimatedMinutes(20)
                            .addLearningObjectives("Understand foundational concepts")
                            .build())
                    .addNodes(AiLearningProto.LearningNode.newBuilder()
                            .setId("node-2")
                            .setTitle("Applied Practice")
                            .setType("PROJECT")
                            .setDescription("Apply concepts toward: " + goal)
                            .setEstimatedMinutes(40)
                            .addPrerequisites("node-1")
                            .addLearningObjectives("Demonstrate practical understanding")
                            .build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        });
    }
}
