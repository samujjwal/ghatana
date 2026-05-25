/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
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
package com.ghatana.datacloud.plugins.trino;

import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorContext;
import io.trino.spi.connector.ConnectorFactory;

import java.util.Map;

/**
 * Factory for creating EventLog Trino connectors.
 *
 * <p>
 * This factory is the entry point for Trino to instantiate EventLog
 * connectors. It configures the connector with proper EventLog backend
 * connections and multi-tenant isolation.</p>
 *
 * <p>
 * <b>Configuration Properties:</b></p>
 * <ul>
 * <li>eventlog.url - EventLog API endpoint</li>
 * <li>eventlog.auth.token - Authentication token</li>
 * <li>eventlog.tenant.id - Default tenant ID</li>
 * <li>eventlog.cache.enabled - Enable query result caching</li>
 * </ul>
 *
 * <p>
 * <b>Usage in Trino:</b></p>
 * <pre>
 * -- etc/catalog/eventlog.properties
 * connector.name=eventlog
 * eventlog.url=http://localhost:8080
 * eventlog.auth.token=your-token
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Trino connector factory for EventLog
 * @doc.layer product
 * @doc.pattern Factory
 */
public class EventCloudConnectorFactory implements ConnectorFactory {

    /**
     * Connector name registered with Trino.
     */
    public static final String CONNECTOR_NAME = "eventlog";

    @Override
    public String getName() {
        return CONNECTOR_NAME;
    }

    @Override
    public Connector create(
            String catalogName,
            Map<String, String> config,
            ConnectorContext context) {

        // Parse configuration
        EventCloudConnectorConfig connectorConfig = EventCloudConnectorConfig.fromMap(config);

        // Create and return the connector
        return new EventCloudConnector(connectorConfig, context);
    }
}
