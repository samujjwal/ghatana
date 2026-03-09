/**
 * DAG Pipeline Runtime for composing and executing {@link com.ghatana.platform.workflow.operator.UnifiedOperator}s.
 *
 * <p><b>Key types:</b>
 * <ul>
 *   <li>{@link com.ghatana.platform.workflow.pipeline.Pipeline} — immutable DAG of operator nodes</li>
 *   <li>{@link com.ghatana.platform.workflow.pipeline.PipelineBuilder} — fluent builder (sequential, parallel, conditional)</li>
 *   <li>{@link com.ghatana.platform.workflow.pipeline.DAGPipelineExecutor} — execution engine using ActiveJ Promises</li>
 *   <li>{@link com.ghatana.platform.workflow.pipeline.PipelineNode} — node wrapping an operator with edges</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose DAG pipeline runtime for operator composition
 * @doc.layer core
 */
package com.ghatana.platform.workflow.pipeline;
