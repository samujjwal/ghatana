(() => {
  const RootCause = {
    SITE: 'SITE',
    CLIENT: 'CLIENT',
    NETWORK: 'NETWORK',
    UNKNOWN: 'UNKNOWN',
  };

  const MODEL_WEIGHTS = {
    site_intercept: -0.5,
    client_intercept: -0.2,
    network_intercept: -0.8,
    site: {
      server_response_time: 0.8,
      dom_content_loaded: 0.6,
      ttfb_ratio: 0.7,
      resource_count: 0.3,
    },
    client: {
      dom_processing_time: 0.9,
      render_blocking_resources: 0.7,
      memory_usage: 0.5,
      cpu_intensive_scripts: 0.6,
    },
    network: {
      connection_time: 0.8,
      dns_lookup_time: 0.7,
      download_time: 0.6,
      bandwidth_estimate: -0.4,
    },
  };

  const THRESHOLDS = {
    slow_page_load: 3000,
    slow_ttfb: 800,
    slow_dns: 200,
    slow_connection: 500,
  };

  function normalize(value, min, max) {
    return Math.max(0, Math.min(1, (value - min) / (max - min)));
  }

  function estimateMemoryPressure() {
    const perf =
      /** @type {{memory?: { usedJSHeapSize: number; jsHeapSizeLimit: number }}} */ performance;
    if (perf.memory) {
      const used = perf.memory.usedJSHeapSize;
      const limit = perf.memory.jsHeapSizeLimit;
      return Math.min(1, used / limit);
    }
    return 0.5;
  }

  function estimateScriptComplexity(resourceTimings) {
    const scriptResources = resourceTimings.filter((r) => r.name.includes('.js'));
    const totalScriptTime = scriptResources.reduce(
      (sum, r) => sum + (r.responseEnd - r.responseStart),
      0
    );
    return normalize(totalScriptTime, 0, 2000);
  }

  function extractFeatures(navigationTiming, resourceTimings) {
    const timing = navigationTiming;
    const ttfb = timing.responseStart - timing.requestStart;
    const serverTime = timing.responseEnd - timing.responseStart;
    const domProcessing = timing.domContentLoadedEventStart - timing.responseEnd;
    const dnsTime = timing.domainLookupEnd - timing.domainLookupStart;
    const connectionTime = timing.connectEnd - timing.connectStart;
    const downloadTime = timing.responseEnd - timing.responseStart;
    const totalTime = timing.loadEventEnd - timing.navigationStart;

    const resourceCount = resourceTimings.length;
    const renderBlockingCount = resourceTimings.filter(
      (r) => r.name.includes('.css') || r.name.includes('.js')
    ).length;

    const totalBytes = resourceTimings.reduce((sum, r) => sum + (r.transferSize || 0), 0);
    const bandwidthEstimate = totalBytes > 0 ? totalBytes / Math.max(totalTime, 1) : 0;

    return {
      server_response_time: normalize(serverTime, 0, 2000),
      dom_content_loaded: normalize(domProcessing, 0, 3000),
      ttfb_ratio: normalize(ttfb / Math.max(totalTime, 1), 0, 0.5),
      resource_count: normalize(resourceCount, 0, 100),
      dom_processing_time: normalize(domProcessing, 0, 2000),
      render_blocking_resources: normalize(renderBlockingCount, 0, 20),
      memory_usage: estimateMemoryPressure(),
      cpu_intensive_scripts: estimateScriptComplexity(resourceTimings),
      connection_time: normalize(connectionTime, 0, 1000),
      dns_lookup_time: normalize(dnsTime, 0, 500),
      download_time: normalize(downloadTime, 0, 3000),
      bandwidth_estimate: normalize(bandwidthEstimate, 0, 1000),
    };
  }

  function predictLatencyCause(features) {
    const siteScore =
      MODEL_WEIGHTS.site_intercept +
      Object.entries(MODEL_WEIGHTS.site).reduce(
        (sum, [key, weight]) => sum + weight * (features[key] ?? 0),
        0
      );

    const clientScore =
      MODEL_WEIGHTS.client_intercept +
      Object.entries(MODEL_WEIGHTS.client).reduce(
        (sum, [key, weight]) => sum + weight * (features[key] ?? 0),
        0
      );

    const networkScore =
      MODEL_WEIGHTS.network_intercept +
      Object.entries(MODEL_WEIGHTS.network).reduce(
        (sum, [key, weight]) => sum + weight * (features[key] ?? 0),
        0
      );

    const scores = [siteScore, clientScore, networkScore];
    const maxScore = Math.max(...scores);
    const expScores = scores.map((s) => Math.exp(s - maxScore));
    const sumExp = expScores.reduce((a, b) => a + b, 0);
    const probabilities = expScores.map((s) => s / sumExp);

    const causes = [RootCause.SITE, RootCause.CLIENT, RootCause.NETWORK];
    const maxIdx = probabilities.indexOf(Math.max(...probabilities));
    const prediction = causes[maxIdx] ?? RootCause.UNKNOWN;
    const confidence = probabilities[maxIdx] ?? 0;

    return {
      prediction,
      confidence,
      probabilities: {
        SITE: probabilities[0] ?? 0,
        CLIENT: probabilities[1] ?? 0,
        NETWORK: probabilities[2] ?? 0,
      },
      features,
    };
  }

  function analyzeLatency(navigationTiming, resourceTimings) {
    try {
      const totalTime = navigationTiming.loadEventEnd - navigationTiming.navigationStart;
      if (totalTime < THRESHOLDS.slow_page_load) {
        return {
          prediction: RootCause.UNKNOWN,
          confidence: 0,
          probabilities: { SITE: 0, CLIENT: 0, NETWORK: 0 },
          reason: 'Page load time within normal range',
          totalTime,
        };
      }

      const features = extractFeatures(navigationTiming, resourceTimings);
      const result = predictLatencyCause(features);
      result.totalTime = totalTime;
      result.timing_breakdown = {
        ttfb: navigationTiming.responseStart - navigationTiming.requestStart,
        server_response: navigationTiming.responseEnd - navigationTiming.responseStart,
        dom_processing: navigationTiming.domContentLoadedEventStart - navigationTiming.responseEnd,
        resource_loading:
          navigationTiming.loadEventEnd - navigationTiming.domContentLoadedEventStart,
      };
      result.features = features;
      return result;
    } catch (error) {
      console.error('Latency analysis failed:', error);
      return {
        prediction: RootCause.UNKNOWN,
        confidence: 0,
        probabilities: { SITE: 0, CLIENT: 0, NETWORK: 0 },
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  window.__dcmaarLatency = {
    RootCause,
    analyzeLatency,
    predictLatencyCause,
    extractFeatures,
  };
})();
