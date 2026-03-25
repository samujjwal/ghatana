/*
 * Copyright (c) 2024 Ghatana, Inc.
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

package com.ghatana.yappc.core.pack;

/**
 * Exception thrown when pack operations fail. Week 2, Day 7 deliverable.
 * @doc.type class
 * @doc.purpose Exception thrown when pack operations fail. Week 2, Day 7 deliverable.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PackException extends Exception {

    private static final long serialVersionUID = 1L;

    public PackException(String message) {
        super(message);
    }

    public PackException(String message, Throwable cause) {
        super(message, cause);
    }

    public PackException(Throwable cause) {
        super(cause);
    }
}
