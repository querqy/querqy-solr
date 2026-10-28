package querqy.solr.rewriter.numberunit;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.util.ResourceLoader;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import querqy.rewrite.RewriterFactory;
import querqy.solr.SolrRewriterFactoryAdapter;
import querqy.solr.rewriter.ClassicConfigurationParser;
import querqy.solr.utils.ConfigUtils;
import querqy.solr.utils.JacksonJsonProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static querqy.solr.RewriterConfigRequestBuilder.CONF_CLASS;
import static querqy.solr.RewriterConfigRequestBuilder.CONF_CONFIG;
import static querqy.solr.utils.ConfigUtils.ifNotNull;

public class NumberUnitRewriterFactory extends SolrRewriterFactoryAdapter implements ClassicConfigurationParser {

    public static final String CONF_PROPERTY = "config";

    private querqy.rewriter.numberunit.NumberUnitRewriterFactory delegate = null;

    public NumberUnitRewriterFactory(final String rewriterId) {
        super(rewriterId);
    }

    @Override
    public void configure(final Map<String, Object> config) {

        final String configString = ConfigUtils.getStringArg(config, CONF_PROPERTY)
                .orElseThrow(() -> new IllegalStateException("Config could not be parsed after successful validation"));

        try {
            delegate = new querqy.rewriter.numberunit.NumberUnitRewriterFactory(rewriterId, configString,
                    NumberUnitQueryCreatorSolr::new, JacksonJsonProvider.INSTANCE);
        } catch (final IOException e) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Could not create delegate factory", e);
        }

    }

    @Override
    public List<String> validateConfiguration(final Map<String, Object> config) {

        return ConfigUtils.getStringArg(config, CONF_PROPERTY)
                .map(configString -> querqy.rewriter.numberunit.NumberUnitRewriterFactory
                        .validateConfiguration(configString, JacksonJsonProvider.INSTANCE))
                .orElse(Collections.singletonList("Property '" + CONF_PROPERTY + "' not configured"));

    }

    @Override
    public RewriterFactory getRewriterFactory() {
        return delegate;
    }

    @Override
    public Map<String, Object> parseConfigurationToRequestHandlerBody(final NamedList<Object> configuration,
                                                                      final ResourceLoader resourceLoader) throws RuntimeException {

        final Map<String, Object> result = new HashMap<>();
        final Map<String, Object> conf = new HashMap<>();
        result.put(CONF_CONFIG, conf);

        ifNotNull((String) configuration.get(CONF_PROPERTY), configJsonFile -> {
            try {
                conf.put(CONF_CONFIG, IOUtils.toString(resourceLoader.openResource(configJsonFile), UTF_8));
            } catch (IOException e) {
                throw new RuntimeException("Could not load file: " + configJsonFile + " because " + e.getMessage());
            }
        });

        ifNotNull(configuration.get(CONF_CLASS), v -> result.put(CONF_CLASS, v));

        return result;
    }

}
