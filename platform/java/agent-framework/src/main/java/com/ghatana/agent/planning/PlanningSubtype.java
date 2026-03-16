/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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

package com.ghatana.agent.planning;

/**
 * Subtypes of planning agent behaviour.
 *
 * <p>All subtypes share: an explicit planning phase that decomposes a high-level goal
 * into steps before execution. They handle blocked/waiting lifecycle states and can
 * revise the plan when sub-steps fail.
 *
 * <h2>Type Boundaries</h2>
 * <ul>
 *   <li>Use {@code WORKFLOW} when the execution order is static and known upfront (BPMN, YAML).</li>
 *   <li>Use {@code REACT} when the agent interleaves reasoning and tool use iteratively.</li>
 *   <li>Use {@code HTN} when hierarchical task decomposition is needed (sub-tasks of sub-tasks).</li>
 *   <li>Use {@code TOT} when exploring multiple reasoning branches in parallel.</li>
 *   <li>Use {@code OBJECTIVE_DECOMPOSITION} for high-level goal→milestone→task breakdown.</li>
 * </ul>
 *
 * @since 2.1.0
 *
 * @doc.type enum
 * @doc.purpose Subtypes of planning agent strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum PlanningSubtype {

    /**
     * Hierarchical Task Network decomposition.
     * Breaks compound tasks into primitive actions via a domain-specific method hierarchy.
     * Best for structured, well-defined task domains.
     */
    HTN,

    /**
     * Reasoning + Acting (ReAct pattern).
     * Interleaves natural-language reasoning steps with tool use and
     * observation in an iterative loop: Thought → Action → Observation → Thought…
     * Best for open-ended, tool-heavy tasks.
     */
    REACT,

    /**
     * Tree-of-Thought exploration.
     * Generates and evaluates multiple reasoning branches in parallel
     * before committing to the best-scoring path.
     * Best for problems with branching solution spaces.
     */
    TOT,

    /**
     * Static workflow execution (BPMN, YAML workflow, DAG).
     * The complete plan is specified upfront via a workflow definition;
     * the agent executes the steps in declared order with gateway/branch resolution.
     * Best for repeatable, auditable business processes.
     */
    WORKFLOW,

    /**
     * Objective → Milestone → Task decomposition (OKR-style).
     * Breaks a top-level objective into strategic milestones and
     * tactical tasks, assigning ownership and tracking completion.
     * Best for YAPPC orchestrators managing long-horizon goals.
     */
    OBJECTIVE_DECOMPOSITION
}
