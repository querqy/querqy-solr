package querqy.solr;

import static querqy.solr.CacheStatsTestSupport.readCacheStats;
import static querqy.solr.QuerqyQParserPlugin.PARAM_REWRITERS;
import static querqy.solr.StandaloneSolrTestSupport.withCommonRulesRewriter;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QueryParsing;
import org.junit.BeforeClass;
import org.junit.Test;

@SolrTestCaseJ4.SuppressSSL
public class SolrTermQueryCachePreloadTest extends SolrTestCaseJ4 {

    private static final String CACHE_NAME = "querqyTermQueryCache";

    @BeforeClass
    public static void beforeTest() throws Exception{
        initCore("solrconfig.xml", "schema.xml", getFile("cache-preload-test/collection1").getParent());
        withCommonRulesRewriter(h.getCore(), "common_rules", "configs/commonrules/rules-cache.txt");

        // this leaves the rewriter file in place so that it will be available
        // for the firstSearcher event in testThatCacheIsAvailableAndPrefilledNotUpdatedByQueryAndUpdatedByRewriter()
        h.close();
        initCore("solrconfig.xml", "schema.xml", getFile("cache-preload-test/collection1").getParent());

        // The newSearcher listener (which preloads both f1 and f2) only fires when a commit
        // actually opens a new searcher. Index a doc so the commit is non-empty and a new
        // searcher gets opened. Without this, only the firstSearcher listener (f1 only) runs
        // and the test's f2-related assertions can't be satisfied.
        assertU(adoc("id", "warmup"));
        assertU(commit());
    }

    @Test
    public void testThatCacheIsAvailableAndPrefilledNotUpdatedByQueryAndUpdatedByRewriter() throws Exception {

        // The cache is prefilled asynchronously by the newSearcher listener (both f1 and f2).
        // Retry up to 10 times to wait for the async preload to complete.
        int attempts = 10;
        do {
            try {
                assertEquals("Cache not prefilled by newSearcher listener",
                        2L, readCacheStats(h.getCore(), CACHE_NAME).size());
                attempts = 0;
            } catch (final AssertionError e) {
                if (attempts <= 1) throw e;
                attempts--;
                synchronized (this) { wait(200L); }
            }
        } while (attempts > 0);

        String q = "a b c";
        SolrQueryRequest req3 = req(
                CommonParams.Q, q,
                DisMaxParams.QF, "f1 f2",
                QueryParsing.OP, "AND",
                "defType", "querqy",
                "debugQuery", "true",
                PARAM_REWRITERS, "common_rules"
        );

        // f1:b and f2:b would be produced by synonym rule, but
        // due to pre-testing for hits in preload they should not
        // occur in the parsed query
        assertQ("Terms w/o hits found in parsedquery",
                req3,
                "//result[@name='response'][@numFound='0']",
                "//str[@name='parsedquery'][not(contains(.,'f1:b'))]",
                "//str[@name='parsedquery'][not(contains(.,'f2:b'))]"
        );
        req3.close();

        assertEquals("Cache was updated unexpectedly by query",
                2L, readCacheStats(h.getCore(), CACHE_NAME).size());

        withCommonRulesRewriter(h.getCore(), "common_rules", "configs/commonrules/rules-cache-update.txt");

        // The new rules produce 2 rhs terms searched in 2 fields each = 4 entries.
        // The preload is triggered asynchronously on rewriter reload — retry up to 10 times.
        attempts = 10;
        do {
            try {
                assertEquals("common_rules update didn't trigger preloader",
                        4L, readCacheStats(h.getCore(), CACHE_NAME).size());
                attempts = 0;
            } catch (final AssertionError e) {
                if (attempts <= 1) throw e;
                attempts--;
                synchronized (this) { wait(200L); }
            }
        } while (attempts > 0);
    }

}
