package querqy.solr;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.admin.MetricsHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.stats.MetricUtils;

import java.util.Map;

/**
 * In-process helper for reading Solr user-cache stats via the {@link MetricsHandler} +
 * Prometheus metric snapshot. Replaces the {@code /admin/mbeans} handler that was removed in
 * Solr 9; Solr 10 exposes cache metrics through OpenTelemetry / Prometheus.
 */
final class CacheStatsTestSupport {

    private static final String LOOKUPS_METRIC = "solr_core_indexsearcher_cache_lookups";
    private static final String SIZE_METRIC = "solr_core_indexsearcher_cache_size";

    record CacheStats(long lookups, long hits, long size) { }

    private CacheStatsTestSupport() { }

    /** Reads {@code lookups}, {@code hits} and {@code size} for the named user cache. */
    static CacheStats readCacheStats(SolrCore core, String cacheName) throws Exception {
        MetricSnapshots snapshots = collect(core, LOOKUPS_METRIC + "," + SIZE_METRIC);
        long hits = sum(snapshots, LOOKUPS_METRIC, cacheName, "hit");
        long misses = sum(snapshots, LOOKUPS_METRIC, cacheName, "miss");
        long size = sum(snapshots, SIZE_METRIC, cacheName, null);
        return new CacheStats(hits + misses, hits, size);
    }

    /** Reads only the {@code hits} count for the named user cache. */
    static long readCacheHits(SolrCore core, String cacheName) throws Exception {
        return sum(collect(core, LOOKUPS_METRIC), LOOKUPS_METRIC, cacheName, "hit");
    }

    private static MetricSnapshots collect(SolrCore core, String metricNames) throws Exception {
        try (MetricsHandler handler = new MetricsHandler(core.getCoreContainer())) {
            SolrParams params = new MapSolrParams(Map.of(
                    CommonParams.WT, MetricUtils.PROMETHEUS_METRICS_WT,
                    MetricUtils.METRIC_NAME_PARAM, metricNames));
            SolrQueryRequest req = new SolrQueryRequestBase(core, params) {};
            SolrQueryResponse resp = new SolrQueryResponse();
            try {
                handler.handleRequestBody(req, resp);
                return (MetricSnapshots) resp.getValues().get("metrics");
            } finally {
                req.close();
            }
        }
    }

    /**
     * Sums data point values for {@code metricName} where the {@code name} label matches
     * {@code cacheName}, and (if non-null) the {@code result} label matches {@code resultLabel}.
     */
    private static long sum(MetricSnapshots snapshots, String metricName,
                            String cacheName, String resultLabel) {
        long total = 0L;
        for (MetricSnapshot snapshot : snapshots) {
            if (!metricName.equals(snapshot.getMetadata().getPrometheusName())) continue;
            for (DataPointSnapshot dp : snapshot.getDataPoints()) {
                if (!cacheName.equals(dp.getLabels().get("name"))) continue;
                if (resultLabel != null && !resultLabel.equals(dp.getLabels().get("result"))) continue;
                if (dp instanceof CounterSnapshot.CounterDataPointSnapshot c) {
                    total += (long) c.getValue();
                } else if (dp instanceof GaugeSnapshot.GaugeDataPointSnapshot g) {
                    total += (long) g.getValue();
                }
            }
        }
        return total;
    }
}
