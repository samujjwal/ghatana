/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Channel;
import com.ghatana.yappc.api.domain.Notification;
import com.ghatana.yappc.api.domain.Team;
import com.ghatana.yappc.api.repository.ChannelRepository;
import com.ghatana.yappc.api.repository.NotificationRepository;
import com.ghatana.yappc.api.repository.TeamRepository;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CollaborationResolver}.
 *
 * <p>Covers teams, notifications, channels, channel, and createChannel data fetchers.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CollaborationResolver GraphQL data fetchers
 * @doc.layer product
 * @doc.pattern Test
 */
class CollaborationResolverTest extends EventloopTestBase {

  private TeamRepository teamRepository;
  private ChannelRepository channelRepository;
  private NotificationRepository notificationRepository;
  private CollaborationResolver resolver;

  private static final String TENANT = "tenant-collab";
  private static final String ORG_ID = "org-1";
  private static final UUID TEAM_ID = UUID.randomUUID();
  private static final UUID CHANNEL_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    teamRepository = mock(TeamRepository.class);
    channelRepository = mock(ChannelRepository.class);
    notificationRepository = mock(NotificationRepository.class);
    resolver = new CollaborationResolver(teamRepository, channelRepository, notificationRepository);
  }

  // =========================================================================
  // teams(tenantId, organizationId) — Query
  // =========================================================================

  @Nested
  class TeamsQuery {

    @Test
    void shouldReturnTeamsForOrganization() throws Exception {
      Team t = new Team();
      t.setTenantId(TENANT);
      t.setName("Engineering");
      when(teamRepository.findByOrganization(TENANT, ORG_ID)).thenReturn(Promise.of(List.of(t)));

      DataFetchingEnvironment env = envForTeams(TENANT, ORG_ID);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.teams();

      @SuppressWarnings("unchecked")
      List<Team> result = (List<Team>) fetcher.get(env).get();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("Engineering");
      verify(teamRepository).findByOrganization(TENANT, ORG_ID);
    }

    @Test
    void shouldReturnEmptyListWhenNoTeams() throws Exception {
      when(teamRepository.findByOrganization(TENANT, ORG_ID)).thenReturn(Promise.of(List.of()));

      DataFetchingEnvironment env = envForTeams(TENANT, ORG_ID);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.teams();

      @SuppressWarnings("unchecked")
      List<Team> result = (List<Team>) fetcher.get(env).get();

      assertThat(result).isEmpty();
    }
  }

  // =========================================================================
  // notifications(tenantId) — Query
  // =========================================================================

  @Nested
  class NotificationsQuery {

    @Test
    void shouldReturnNotificationsForAuthenticatedUser() throws Exception {
      Notification n = new Notification();
      n.setTenantId(TENANT);
      n.setUserId("user-42");
      when(notificationRepository.findByUser(TENANT, "user-42", 100, 0))
          .thenReturn(Promise.of(List.of(n)));

      TenantContextExtractor.RequestContext ctx =
          TenantContextExtractor.RequestContext.of(TENANT, "user-42", "developer");
      DataFetchingEnvironment env = envForNotifications(TENANT, ctx);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.notifications();

      @SuppressWarnings("unchecked")
      List<Notification> result = (List<Notification>) fetcher.get(env).get();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getUserId()).isEqualTo("user-42");
      verify(notificationRepository).findByUser(TENANT, "user-42", 100, 0);
    }

    @Test
    void shouldFallbackToSystemUserWhenNoRequestContext() throws Exception {
      when(notificationRepository.findByUser(TENANT, "system", 100, 0))
          .thenReturn(Promise.of(List.of()));

      DataFetchingEnvironment env = envForNotifications(TENANT, null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.notifications();

      @SuppressWarnings("unchecked")
      List<Notification> result = (List<Notification>) fetcher.get(env).get();

      assertThat(result).isEmpty();
      verify(notificationRepository).findByUser(TENANT, "system", 100, 0);
    }
  }

  // =========================================================================
  // channels(tenantId, teamId) — Query
  // =========================================================================

  @Nested
  class ChannelsQuery {

    @Test
    void shouldReturnChannelsForTeam() throws Exception {
      Channel c = makeChannel("general", "PUBLIC");
      when(channelRepository.findByTeamId(TENANT, TEAM_ID)).thenReturn(Promise.of(List.of(c)));

      DataFetchingEnvironment env = envForChannels(TENANT, TEAM_ID.toString(), null);
      DataFetcher<CompletableFuture<?>> fetcher = resolver.channels();

      @SuppressWarnings("unchecked")
      List<Channel> result = (List<Channel>) fetcher.get(env).get();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("general");
    }
  }

  // =========================================================================
  // channel(tenantId, id) — Query
  // =========================================================================

  @Nested
  class ChannelQuery {

    @Test
    void shouldReturnChannelWhenFound() throws Exception {
      Channel c = makeChannel("engineering", "PRIVATE");
      c.setId(CHANNEL_ID);
      when(channelRepository.findById(TENANT, CHANNEL_ID)).thenReturn(Promise.of(Optional.of(c)));

      DataFetchingEnvironment env = envForChannels(TENANT, null, CHANNEL_ID.toString());
      DataFetcher<CompletableFuture<?>> fetcher = resolver.channel();

      Channel result = (Channel) fetcher.get(env).get();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("engineering");
    }

    @Test
    void shouldReturnNullWhenChannelNotFound() throws Exception {
      when(channelRepository.findById(TENANT, CHANNEL_ID)).thenReturn(Promise.of(Optional.empty()));

      DataFetchingEnvironment env = envForChannels(TENANT, null, CHANNEL_ID.toString());
      DataFetcher<CompletableFuture<?>> fetcher = resolver.channel();

      Object result = fetcher.get(env).get();

      assertThat(result).isNull();
    }
  }

  // =========================================================================
  // createChannel(tenantId, input) — Mutation
  // =========================================================================

  @Nested
  class CreateChannelMutation {

    @Test
    void shouldCreateChannelWithGivenInput() throws Exception {
      Map<String, Object> input =
          Map.of(
              "teamId", TEAM_ID.toString(),
              "name", "releases",
              "type", "PUBLIC",
              "description", "Release announcements");

      when(channelRepository.save(any(Channel.class)))
          .thenAnswer(inv -> Promise.of(inv.getArgument(0)));

      DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
      when(env.getArgument("tenantId")).thenReturn(TENANT);
      when(env.getArgument("input")).thenReturn(input);

      DataFetcher<CompletableFuture<?>> fetcher = resolver.createChannel();
      Channel result = (Channel) fetcher.get(env).get();

      assertThat(result.getTenantId()).isEqualTo(TENANT);
      assertThat(result.getName()).isEqualTo("releases");
      assertThat(result.getType()).isEqualTo("PUBLIC");
      assertThat(result.getDescription()).isEqualTo("Release announcements");
      assertThat(result.getTeamId()).isEqualTo(TEAM_ID);
      assertThat(result.getUnreadCount()).isEqualTo(0);
      verify(channelRepository).save(any(Channel.class));
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private Channel makeChannel(String name, String type) {
    Channel c = new Channel();
    c.setTenantId(TENANT);
    c.setTeamId(TEAM_ID);
    c.setName(name);
    c.setType(type);
    c.setUnreadCount(0);
    return c;
  }

  private DataFetchingEnvironment envForTeams(String tenantId, String organizationId) {
    DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
    when(env.getArgument("tenantId")).thenReturn(tenantId);
    when(env.getArgument("organizationId")).thenReturn(organizationId);
    return env;
  }

  private DataFetchingEnvironment envForNotifications(
      String tenantId, TenantContextExtractor.RequestContext requestContext) {
    DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
    GraphQLContext gqlCtx = mock(GraphQLContext.class);
    when(env.getArgument("tenantId")).thenReturn(tenantId);
    when(env.getGraphQlContext()).thenReturn(gqlCtx);
    when(gqlCtx.get("requestContext")).thenReturn(requestContext);
    return env;
  }

  private DataFetchingEnvironment envForChannels(String tenantId, String teamId, String id) {
    DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
    when(env.getArgument("tenantId")).thenReturn(tenantId);
    when(env.getArgument("teamId")).thenReturn(teamId);
    when(env.getArgument("id")).thenReturn(id);
    return env;
  }
}
