/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — StoryService Tests
 */
package com.ghatana.yappc.api.service;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Story;
import com.ghatana.yappc.api.domain.Story.*;
import com.ghatana.yappc.api.repository.StoryRepository;
import com.ghatana.yappc.api.service.StoryService.*;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StoryService}.
 *
 * <p>Covers story CRUD, lifecycle transitions, task management,
 * acceptance criteria, blocker management, and LLM-assisted generation.
 
 * @doc.type class
 * @doc.purpose Handles story service test operations
 * @doc.layer product
 * @doc.pattern Test
*/
class StoryServiceTest extends EventloopTestBase {

    private StoryRepository repository;
    private AuditService auditService;
    private LLMGateway llmGateway;
    private StoryService service;

    private static final String TENANT = "test-tenant";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SPRINT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(StoryRepository.class);
        auditService = mock(AuditService.class);
        llmGateway = mock(LLMGateway.class);
        service = new StoryService(repository, auditService, llmGateway);

        // Default: repository.save returns the story it receives
        when(repository.save(any(Story.class)))
                .thenAnswer(inv -> Promise.of(inv.getArgument(0)));
    }

    // =========================================================================
    // createStory
    // =========================================================================

    @Nested
    class CreateStory {

        @Test
        void shouldCreateStoryWithDefaults() {
            // LLM returns null → fallback tasks/criteria
            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(null);

            CreateStoryInput input = new CreateStoryInput(
                    PROJECT_ID, null, "Add login page", "User login with OAuth",
                    null, null, null, "user-1", true, true);

            Story result = runPromise(() -> service.createStory(TENANT, input));

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Add login page");
            assertThat(result.getType()).isEqualTo(StoryType.FEATURE);
            assertThat(result.getPriority()).isEqualTo(Priority.P2);
            assertThat(result.getStatus()).isEqualTo(StoryStatus.BACKLOG);
            assertThat(result.getTenantId()).isEqualTo(TENANT);
            assertThat(result.getProjectId()).isEqualTo(PROJECT_ID.toString());
            // Fallback generates 3 tasks and 3 criteria
            assertThat(result.getTasks()).hasSize(3);
            assertThat(result.getAcceptanceCriteria()).hasSize(3);
            verify(repository).save(any(Story.class));
        }

        @Test
        void shouldCreateStoryWithSprint() {
            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(null);

            CreateStoryInput input = new CreateStoryInput(
                    PROJECT_ID, SPRINT_ID, "Fix bug", "NPE on checkout",
                    StoryType.BUG, Priority.P0, 3, "user-2", false, false);

            Story result = runPromise(() -> service.createStory(TENANT, input));

            assertThat(result.getStatus()).isEqualTo(StoryStatus.TODO);
            assertThat(result.getSprintId()).isEqualTo(SPRINT_ID.toString());
            assertThat(result.getType()).isEqualTo(StoryType.BUG);
            assertThat(result.getPriority()).isEqualTo(Priority.P0);
            assertThat(result.getStoryPoints()).isEqualTo(3);
            assertThat(result.getTasks()).isEmpty();
            assertThat(result.getAcceptanceCriteria()).isEmpty();
        }

        @Test
        void shouldGenerateTasksFromLLM() {
            CompletionResult llmResult = mock(CompletionResult.class);
            when(llmResult.getText()).thenReturn(
                    "Design API|||Create REST endpoint design\n" +
                    "Implement handler|||Write HTTP handler code");
            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.of(llmResult));

            CreateStoryInput input = new CreateStoryInput(
                    PROJECT_ID, null, "New endpoint", "Add /api/foo",
                    null, null, null, "user-1", true, false);

            Story result = runPromise(() -> service.createStory(TENANT, input));

            assertThat(result.getTasks()).hasSize(2);
            assertThat(result.getTasks().get(0).getTitle()).isEqualTo("Design API");
            assertThat(result.getTasks().get(0).isAiGenerated()).isTrue();
        }
    }

    // =========================================================================
    // getStory / getStoryByKey
    // =========================================================================

    @Nested
    class GetStory {

        @Test
        void shouldReturnStoryById() {
            Story story = createSampleStory();
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Optional<Story> result = runPromise(() -> service.getStory(TENANT, story.getId()));

            assertThat(result).isPresent();
            assertThat(result.get().getTitle()).isEqualTo("Sample");
        }

        @Test
        void shouldReturnEmptyForMissingStory() {
            when(repository.findById(eq(TENANT), any(UUID.class)))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<Story> result = runPromise(() -> service.getStory(TENANT, UUID.randomUUID()));

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnStoryByKey() {
            Story story = createSampleStory();
            story.setStoryKey("PROJ-42");
            when(repository.findByKey(TENANT, "PROJ-42"))
                    .thenReturn(Promise.of(Optional.of(story)));

            Optional<Story> result = runPromise(() -> service.getStoryByKey(TENANT, "PROJ-42"));

            assertThat(result).isPresent();
            assertThat(result.get().getStoryKey()).isEqualTo("PROJ-42");
        }
    }

    // =========================================================================
    // updateStory
    // =========================================================================

    @Nested
    class UpdateStory {

        @Test
        void shouldUpdateTitleAndPriority() {
            Story story = createSampleStory();
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            UpdateStoryInput input = new UpdateStoryInput(
                    "Updated title", null, null, Priority.P1, null);

            Story result = runPromise(() -> service.updateStory(TENANT, story.getId(), input));

            assertThat(result.getTitle()).isEqualTo("Updated title");
            assertThat(result.getPriority()).isEqualTo(Priority.P1);
            assertThat(result.getDescription()).isEqualTo("Sample description");
        }

        @Test
        void shouldThrowForMissingStoryOnUpdate() {
            when(repository.findById(eq(TENANT), any(UUID.class)))
                    .thenReturn(Promise.of(Optional.empty()));

            UpdateStoryInput input = new UpdateStoryInput("x", null, null, null, null);

            assertThatThrownBy(() ->
                    runPromise(() -> service.updateStory(TENANT, UUID.randomUUID(), input)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Story not found");
        }
    }

    // =========================================================================
    // moveStory (lifecycle transitions)
    // =========================================================================

    @Nested
    class MoveStory {

        @Test
        void shouldTransitionFromBacklogToTodo() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.BACKLOG);
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.moveStory(TENANT, story.getId(), StoryStatus.TODO));

            assertThat(result.getStatus()).isEqualTo(StoryStatus.TODO);
        }

        @Test
        void shouldRejectInvalidTransition() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.DONE);
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            assertThatThrownBy(() ->
                    runPromise(() -> service.moveStory(TENANT, story.getId(), StoryStatus.IN_PROGRESS)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldTransitionThroughFullLifecycle() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.BACKLOG);
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            runPromise(() -> service.moveStory(TENANT, story.getId(), StoryStatus.TODO));
            assertThat(story.getStatus()).isEqualTo(StoryStatus.TODO);

            runPromise(() -> service.moveStory(TENANT, story.getId(), StoryStatus.IN_PROGRESS));
            assertThat(story.getStatus()).isEqualTo(StoryStatus.IN_PROGRESS);

            runPromise(() -> service.moveStory(TENANT, story.getId(), StoryStatus.REVIEW));
            assertThat(story.getStatus()).isEqualTo(StoryStatus.REVIEW);

            runPromise(() -> service.moveStory(TENANT, story.getId(), StoryStatus.DONE));
            assertThat(story.getStatus()).isEqualTo(StoryStatus.DONE);
            assertThat(story.getCompletedAt()).isNotNull();
        }
    }

    // =========================================================================
    // assignStory / unassignStory
    // =========================================================================

    @Nested
    class AssignStory {

        @Test
        void shouldAssignUser() {
            Story story = createSampleStory();
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.assignStory(TENANT, story.getId(), "user-x"));

            assertThat(result.getAssignedTo()).contains("user-x");
        }

        @Test
        void shouldNotDuplicateAssignment() {
            Story story = createSampleStory();
            story.assign("user-x");
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            runPromise(() -> service.assignStory(TENANT, story.getId(), "user-x"));

            assertThat(story.getAssignedTo().stream()
                    .filter("user-x"::equals).count()).isEqualTo(1);
        }

        @Test
        void shouldUnassignUser() {
            Story story = createSampleStory();
            story.assign("user-y");
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.unassignStory(TENANT, story.getId(), "user-y"));

            assertThat(result.getAssignedTo()).doesNotContain("user-y");
        }
    }

    // =========================================================================
    // moveToSprint
    // =========================================================================

    @Nested
    class MoveToSprint {

        @Test
        void shouldMoveBacklogStoryToSprint() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.BACKLOG);
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.moveToSprint(TENANT, story.getId(), SPRINT_ID));

            assertThat(result.getSprintId()).isEqualTo(SPRINT_ID.toString());
            assertThat(result.getStatus()).isEqualTo(StoryStatus.TODO);
        }

        @Test
        void shouldMoveStoryBackToBacklog() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.TODO);
            story.setSprintId(SPRINT_ID.toString());
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.moveToSprint(TENANT, story.getId(), null));

            assertThat(result.getSprintId()).isNull();
            assertThat(result.getStatus()).isEqualTo(StoryStatus.BACKLOG);
        }
    }

    // =========================================================================
    // addTask / updateTaskStatus
    // =========================================================================

    @Nested
    class TaskManagement {

        @Test
        void shouldAddTask() {
            Story story = createSampleStory();
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Task task = new Task();
            task.setTitle("Implement feature");
            Story result = runPromise(() -> service.addTask(TENANT, story.getId(), task));

            assertThat(result.getTasks()).hasSize(1);
            assertThat(result.getTasks().get(0).getTitle()).isEqualTo("Implement feature");
            assertThat(result.getTasks().get(0).getStoryId()).isEqualTo(story.getId().toString());
        }

        @Test
        void shouldUpdateTaskStatus() {
            Story story = createSampleStory();
            Task task = new Task();
            task.setTitle("Do something");
            story.addTask(task);
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            runPromise(() -> service.updateTaskStatus(TENANT, story.getId(),
                    task.getId().toString(), "DONE"));

            assertThat(task.getStatus()).isEqualTo(Task.TaskStatus.DONE);
            assertThat(task.getCompletedAt()).isNotNull();
        }
    }

    // =========================================================================
    // Blocker management
    // =========================================================================

    @Nested
    class BlockerManagement {

        @Test
        void shouldAddBlocker() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.TODO);
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.addBlocker(TENANT, story.getId(), "blocker-1"));

            assertThat(result.getBlockedBy()).contains("blocker-1");
            assertThat(result.getStatus()).isEqualTo(StoryStatus.BLOCKED);
        }

        @Test
        void shouldRemoveBlockerAndUnblock() {
            Story story = createSampleStory();
            story.setStatus(StoryStatus.BLOCKED);
            story.getBlockedBy().add("blocker-1");
            when(repository.findById(TENANT, story.getId()))
                    .thenReturn(Promise.of(Optional.of(story)));

            Story result = runPromise(() -> service.removeBlocker(TENANT, story.getId(), "blocker-1"));

            assertThat(result.getBlockedBy()).isEmpty();
            assertThat(result.getStatus()).isEqualTo(StoryStatus.TODO);
        }
    }

    // =========================================================================
    // deleteStory
    // =========================================================================

    @Test
    void shouldDeleteStory() {
        UUID storyId = UUID.randomUUID();
        when(repository.delete(TENANT, storyId)).thenReturn(Promise.of(true));

        Boolean result = runPromise(() -> service.deleteStory(TENANT, storyId));

        assertThat(result).isTrue();
        verify(repository).delete(TENANT, storyId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Story createSampleStory() {
        Story story = new Story();
        story.setTenantId(TENANT);
        story.setProjectId(PROJECT_ID.toString());
        story.setTitle("Sample");
        story.setDescription("Sample description");
        story.setType(StoryType.FEATURE);
        story.setPriority(Priority.P2);
        return story;
    }
}
