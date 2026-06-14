package querqy.solr.it;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.testcontainers.containers.SolrClientUtils;
import querqy.rewrite.commonrules.WhiteSpaceQuerqyParserFactory;
import querqy.rewrite.lookup.preprocessing.LookupPreprocessorType;
import querqy.solr.RewriterConfigRequestBuilder;
import querqy.solr.RewriterConfigRequestBuilder.SaveRewriterConfigSolrResponse;
import querqy.solr.rewriter.commonrules.CommonRulesConfigRequestBuilder;
import querqy.solr.rewriter.replace.ReplaceConfigRequestBuilder;
import querqy.solr.rewriter.wordbreak.WordBreakCompoundConfigRequestBuilder;

/**
 * Mostly copied code from {@link SolrClientUtils} as it's not open for
 * extension :-/
 */
public class QuerqySolrClientUtils extends SolrClientUtils {

    /**
     * 
     * @param collectionName    the name of the collection which should be created
     * @param configurationName the name of the configuration which should used to
     *                          create the collection or null if the default
     *                          configuration should be used
     * @param numShards         the number of shards in the new collection
     *
     * @see SolrClientUtils
     */
    public static void createCollection(QuerqySolrContainer solr, String collectionName, String configurationName,
            int numShards) {

        URI uri = URI.create(String.format(
                "%s/admin/collections?action=CREATE&name=%s&numShards=%s&replicationFactor=1&wt=json&collection.configName=%s&maxShardsPerNode=%s",
                solr.getSolrUrl(), collectionName, numShards, configurationName, numShards));

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() > 299) {
                throw new IllegalArgumentException("HTTP " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void createRewriters(QuerqySolrContainer solr, String collectionName) throws IOException {
        try (final SolrClient solrClient = solr.newSolrClient()) {

            if (new CommonRulesConfigRequestBuilder()
                    .rules(QuerqySolrClientUtils.class.getClassLoader()
                            .getResourceAsStream("integration-test/rewriter/rules.txt"))
                    .lookupPreprocessorType(LookupPreprocessorType.LOWERCASE)
                    .rhsParser(WhiteSpaceQuerqyParserFactory.class)
                    .buildSaveRequest("common_rules")
                    .process(solrClient, collectionName).getStatus() != 0) {
                throw new RuntimeException("Could not create common_rules rewriter");
            }

            if (new WordBreakCompoundConfigRequestBuilder()
                    .dictionaryField("dictionary")
                    .verifyDecompoundCollation(true)
                    .lowerCaseInput(true)
                    .buildSaveRequest("word_break")
                    .process(solrClient, collectionName).getStatus() != 0) {
                throw new RuntimeException("Could not create word_break rewriter");
            }

            if (new ReplaceConfigRequestBuilder()
                .rules(QuerqySolrClientUtils.class.getClassLoader()
                        .getResourceAsStream("integration-test/rewriter/replace-rules.txt"))
                    .inputDelimiter(";")
                    .ignoreCase(true).rhsParser(WhiteSpaceQuerqyParserFactory.class)
                    .buildSaveRequest("replace")
                    .process(solrClient, collectionName).getStatus() != 0) {
                throw new RuntimeException("Could not create word_break rewriter");
            }

        } catch (final SolrServerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Imports the icecat chorus dataset into given collection
     */
    public static void importChorusDataset(QuerqySolrContainer solr, String collectionName) throws IOException {

        String chorusDataSet = FileUtils.readFileToString(solr.getTestDataPath().toFile(), "UTF-8");

        URI uri = URI.create(String.format("%s/%s/update?commit=true", solr.getSolrUrl(), collectionName));

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(chorusDataSet))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() > 299) {
                throw new IllegalArgumentException("HTTP " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
