/**
 * Autonomy Controller Module - Graduated autonomy management for AI actions.
 *
 * <p>This module implements a graduated autonomy system that controls the level
 * of human oversight required for AI-initiated actions. As confidence builds
 * through successful outcomes, the system can gradually increase autonomy levels.
 *
 * <h2>Autonomy Levels</h2>
 * <table>
 *   <tr><th>Level</th><th>Description</th><th>Human Involvement</th></tr>
 *   <tr><td>SUGGEST</td><td>AI recommends, human decides</td><td>Required</td></tr>
 *   <tr><td>CONFIRM</td><td>AI proposes, human approves</td><td>Approval needed</td></tr>
 *   <tr><td>NOTIFY</td><td>AI acts, human is informed</td><td>Notification only</td></tr>
 *   <tr><td>AUTONOMOUS</td><td>AI acts independently</td><td>None (audit only)</td></tr>
 * </table>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.client.autonomy.AutonomyLevel} - Defines autonomy levels</li>
 *   <li>{@link com.ghatana.datacloud.client.autonomy.AutonomyContext} - Context for autonomy decisions</li>
 *   <li>{@link com.ghatana.datacloud.client.autonomy.AutonomyPolicy} - Configurable autonomy policies</li>
 *   <li>{@link com.ghatana.datacloud.client.autonomy.AutonomyController} - Manages autonomy transitions</li>
 *   <li>{@link com.ghatana.datacloud.client.autonomy.ActionGate} - Gates actions based on autonomy level</li>
 * </ul>
 *
 * <h2>Graduated Autonomy Flow</h2>
 * <pre>
 *     SUGGEST ──────► CONFIRM ──────► NOTIFY ──────► AUTONOMOUS
 *        │              │               │                │
 *        │   success    │   success     │   success      │
 *        │   builds     │   builds      │   builds       │
 *        │   trust      │   trust       │   trust        │
 *        ▼              ▼               ▼                ▼
 *     (human          (quick         (async            (full
 *      picks)          approve)       oversight)        auto)
 *
 *                    ◄───────── failures degrade ─────────
 * </pre>
 *
 * @doc.type package
 * @doc.purpose Graduated autonomy management for AI actions
 * @doc.layer core
 * @doc.pattern State Machine, Policy
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see com.ghatana.datacloud.client.feedback.LearningLoop
 */
@org.jetbrains.annotations.ApiStatus.Experimental
package com.ghatana.datacloud.client.autonomy;
