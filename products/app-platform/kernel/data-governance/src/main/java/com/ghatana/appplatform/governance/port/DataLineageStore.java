/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import com.ghatana.appplatform.governance.DataLineageService.LineageEdge;
import com.ghatana.appplatform.governance.DataLineageService.LineageNode;

import java.util.List;

/**
 * Repository port for data lineage persistence.
 *
 * @doc.type interface
 * @doc.purpose Abstracts lineage DAG persistence from domain service
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DataLineageStore {

    LineageEdge insertEdge(String edgeId, String sourceAssetId, String targetAssetId,
                           String transformationDesc) throws Exception;

    List<LineageNode> fetchDirectDownstream(String sourceAssetId) throws Exception;

    List<LineageNode> fetchConnectedNodes(String assetId) throws Exception;

    List<LineageEdge> fetchConnectedEdges(String assetId) throws Exception;
}
