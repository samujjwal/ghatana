package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.graph.TaskNode;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.util.List;

/**

 * @doc.type interface

 * @doc.purpose Defines the contract for project adapter

 * @doc.layer core

 * @doc.pattern Adapter

 */

public interface ProjectAdapter {
    String id();

    List<TaskNode> describeTasks(WorkspaceSpec spec);
}
