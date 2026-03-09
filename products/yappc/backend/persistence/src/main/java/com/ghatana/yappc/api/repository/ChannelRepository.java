package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Channel;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ChannelRepository.
 *
 * @doc.type interface
 * @doc.purpose channel repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ChannelRepository {
    Promise<List<Channel>> findByTeamId(String tenantId, UUID teamId);
    Promise<Optional<Channel>> findById(String tenantId, UUID id);
    Promise<Channel> save(Channel channel);
}
