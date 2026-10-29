package querqy.solr;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SolrCache;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static querqy.solr.CacheStatsTestSupport.CacheStats;
import static querqy.solr.CacheStatsTestSupport.readCacheStats;

@SolrTestCaseJ4.SuppressSSL
public class SolrTermQueryCacheTest extends SolrTestCaseJ4 {

    private static final String CACHE_NAME = "querqyTermQueryCache";

    public void index() throws Exception {

        assertU(adoc("id", "1", "f1", "a"));
        assertU(adoc("id", "2", "f1", "a", "f2", "b"));
        assertU(adoc("id", "3", "f1", "a", "f2", "c"));
        assertU(commit());
    }

    @BeforeClass
    public static void beforeTests() throws Exception {
        initCore("solrconfig-cache.xml", "schema.xml");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        clearIndex();
        index();
    }

    @Test
    public void testThatCacheIsAvailable() throws IOException {
        SolrCache<?, ?> cache = h.getCore().withSearcher(s -> s.getCache(CACHE_NAME));
        assertNotNull("Missing querqy cache", cache);
    }

    @Test
    public void testThatTermQueriesArePutIntoAndServedFromCache() throws Exception {

        String q = "c";

        SolrQueryRequest req = req("q", q,
              DisMaxParams.QF, "f1 f2",
              QueryParsing.OP, "OR",
              DisMaxParams.TIE, "0.1",
              "defType", "querqy",
              "debugQuery", "true"
        );

        assertQ("Unexpected query result while caching",
                req,
                "//result[@name='response'][@numFound='1']");
        req.close();

        CacheStats stats1 = readCacheStats(h.getCore(), CACHE_NAME);
        assertEquals("lookups after first query", 2L, stats1.lookups());
        assertEquals("hits after first query", 0L, stats1.hits());
        assertEquals("size after first query", 2L, stats1.size());

        SolrQueryRequest req2 = req("q", q,
                DisMaxParams.QF, "f1 f2",
                QueryParsing.OP, "OR",
                DisMaxParams.TIE, "0.1",
                "defType", "querqy",
                "debugQuery", "true"
        );

        assertQ("Unexpected query result while using cache",
                req2,
                "//result[@name='response'][@numFound='1']");
        req2.close();

        CacheStats stats2 = readCacheStats(h.getCore(), CACHE_NAME);
        assertEquals("lookups after second query", 4L, stats2.lookups());
        assertEquals("hits after second query", 2L, stats2.hits());
        assertEquals("size after second query", 2L, stats2.size());
    }
}
