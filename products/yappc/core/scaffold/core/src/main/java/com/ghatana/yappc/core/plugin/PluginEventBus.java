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

package com.ghatana.yappc.core.plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Event bus for plugin lifecycle and operation events.
 *
 * @doc.type class
 * @doc.purpose Plugin event distribution
 * @doc.layer platform
 * @doc.pattern Observer/Event Bus
 */
public class PluginEventBus {

    private final List<Consumer<PluginEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribes to plugin events.
     *
     * @param listener event listener
     */
    public void subscribe(Consumer<PluginEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Unsubscribes from plugin events.
     *
     * @param listener event listener
     */
    public void unsubscribe(Consumer<PluginEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Publishes an event to all listeners.
     *
     * @param event plugin event
     */
    public void publish(PluginEvent event) {
        listeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // Log but don't fail
            }
        });
    }

    /**
     * Clears all listeners.
     */
    public void clear() {
        listeners.clear();
    }
}
