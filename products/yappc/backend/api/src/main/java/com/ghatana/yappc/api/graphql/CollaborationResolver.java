package com.ghatana.yappc.api.graphql;

import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Channel;
import com.ghatana.yappc.api.repository.ChannelRepository;
import com.ghatana.yappc.api.repository.NotificationRepository;
import com.ghatana.yappc.api.repository.TeamRepository;
import graphql.schema.DataFetcher;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * GraphQL resolver for Team, Channel, and Notification queries/mutations.
 *
 * <p>All data fetchers return {@link Promise} instances that are obtained
 * non-blockingly from ActiveJ {@link Promise} via {@link Promise#toCompletableFuture()}.
 * The GraphQL engine handles the async completion — no {@code .get()} calls are used.
 *
 * @doc.type class
 * @doc.purpose Team/Channel/Notification GraphQL data fetchers
 * @doc.layer product
 * @doc.pattern Resolver
 */
public class CollaborationResolver {

    private final TeamRepository teamRepository;
    private final ChannelRepository channelRepository;
    private final NotificationRepository notificationRepository;

    @Inject
    public CollaborationResolver(
            TeamRepository teamRepository,
            ChannelRepository channelRepository,
            NotificationRepository notificationRepository) {
        this.teamRepository = teamRepository;
        this.channelRepository = channelRepository;
        this.notificationRepository = notificationRepository;
    }

    /** Query: teams(tenantId, organizationId) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> teams() {
        return env -> teamRepository
                .findByOrganization(env.getArgument("tenantId"), env.getArgument("organizationId"))
                .toCompletableFuture();
    }

    /** Query: notifications(tenantId) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> notifications() {
        return env -> {
            TenantContextExtractor.RequestContext requestContext =
                    env.getGraphQlContext().get("requestContext");
            String userId = requestContext != null && requestContext.userId() != null
                    ? requestContext.userId()
                    : "system";
            return notificationRepository
                    .findByUser(env.getArgument("tenantId"), userId, 100, 0)
                    .toCompletableFuture();
        };
    }

    /** Query: channels(tenantId, teamId) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> channels() {
        return env -> {
            String teamId = env.getArgument("teamId");
            String tenantId = env.getArgument("tenantId");
            return channelRepository.findByTeamId(tenantId, UUID.fromString(teamId))
                    .toCompletableFuture();
        };
    }

    /** Query: channel(tenantId, id) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> channel() {
        return env -> {
            String id = env.getArgument("id");
            String tenantId = env.getArgument("tenantId");
            return channelRepository.findById(tenantId, UUID.fromString(id))
                    .map(opt -> opt.orElse(null))
                    .toCompletableFuture();
        };
    }

    /** Mutation: createChannel(tenantId, input) — async, non-blocking. */
    public DataFetcher<CompletableFuture<?>> createChannel() {
        return env -> {
            Map<String, Object> input = env.getArgument("input");
            Channel c = new Channel();
            c.setTenantId(env.getArgument("tenantId"));
            c.setTeamId(UUID.fromString((String) input.get("teamId")));
            c.setName((String) input.get("name"));
            c.setType((String) input.get("type"));
            c.setDescription((String) input.get("description"));
            c.setUnreadCount(0);
            return channelRepository.save(c).toCompletableFuture();
        };
    }
}
