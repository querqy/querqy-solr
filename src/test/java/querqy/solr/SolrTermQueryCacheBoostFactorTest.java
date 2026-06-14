package querqy.solr;

import static querqy.solr.CacheStatsTestSupport.readCacheHits;
import static querqy.solr.QuerqyDismaxParams.GFB;
import static querqy.solr.QuerqyQParserPlugin.PARAM_REWRITERS;
import static querqy.solr.StandaloneSolrTestSupport.withCommonRulesRewriter;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QueryParsing;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@SolrTestCaseJ4.SuppressSSL
public class SolrTermQueryCacheBoostFactorTest extends SolrTestCaseJ4 {

    public void index() {

        assertU(adoc("id", "1", "f1", "a"));
        assertU(adoc("id", "2", "f1", "a", "f2", "b"));
        assertU(adoc("id", "3", "f1", "a", "f2", "c"));
        assertU(commit());
    }

    @BeforeClass
    public static void beforeTests() throws Exception {
        initCore("solrconfig-cache.xml", "schema.xml");
        withCommonRulesRewriter(h.getCore(), "common_rules", "configs/commonrules/rules-cache.txt");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        clearIndex();
        index();
    }

    @Test
    public void testThatRequestDependentBoostFactorsAreApplied() throws Exception {
        String q = "a c";

        SolrQueryRequest req = req("q", q,
                DisMaxParams.QF, "f1^10 f2^200",
                QueryParsing.OP, "OR",
                GFB, "0.4",
                DisMaxParams.TIE, "0.1",
                "defType", "querqy",
                "debugQuery", "true",
                PARAM_REWRITERS, "common_rules"
        );

        assertQ("Boost factors are not applied to terms",
                req,
                "//str[@name='parsedquery'][contains(.,'f1:a^10.0') and contains(.,'f2:a^200.0') and " +
                        "contains(.,'f1:b^4.0') and contains(.,'f2:b^80.0')]",
                "//str[@name='parsedquery'][contains(.,'f1:c^10.0 | f2:c^200.0') or contains(.,'f2:c^200.0 | f1:c^10.0')]");
        req.close();

        SolrQueryRequest  req2 = req("q", q,
                DisMaxParams.QF, "f1^88 f2^1600",
                QueryParsing.OP, "OR",
                GFB, "0.25",
                DisMaxParams.TIE, "0.1",
                "defType", "querqy",
                "debugQuery", "true",
                PARAM_REWRITERS, "common_rules"
        );

        assertQ("Boost factors are not applied to terms",
                req2,
                "//str[@name='parsedquery'][contains(.,'f1:a^88.0') and contains(.,'f2:a^1600.0') and " +
                        "contains(.,'f1:b^22.0') and contains(.,'f2:b^400.0')]",
                "//str[@name='parsedquery'][contains(.,'f1:c^88.0 | f2:c^1600.0') or " +
                        "contains(.,'f2:c^1600.0 | f1:c^88.0')]");

        // should be 6 hits (2 fields x 3 keywords)
        assertEquals("Querqy cache not hit", 6L, readCacheHits(h.getCore(), "querqyTermQueryCache"));
    }
}
