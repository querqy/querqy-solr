package querqy.solr.rewriter.wordbreak;

import com.google.common.collect.Lists;
import org.apache.solr.common.util.NamedList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import querqy.lucene.GZIPAwareResourceLoader;
import querqy.rewrite.RewriterFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static querqy.solr.RewriterConfigRequestBuilder.CONF_CLASS;
import static querqy.solr.RewriterConfigRequestBuilder.CONF_CONFIG;
import static querqy.solr.rewriter.wordbreak.WordBreakCompoundRewriterFactory.*;

@RunWith(MockitoJUnitRunner.class)
public class WordBreakCompoundRewriterFactoryTest {

    private final WordBreakCompoundRewriterFactory factory = new WordBreakCompoundRewriterFactory("test");

    @Mock
    private GZIPAwareResourceLoader resourceLoader;

    @Test
    public void testParseDeprecatedConfigurationWorksFine() {
        NamedList<Object> configuration = new NamedList<>();
        configuration.add(CONF_CLASS, WordBreakCompoundRewriterFactory.class.getName());
        configuration.add(CONF_DICTIONARY_FIELD, "f1");
        configuration.add(CONF_LOWER_CASE_INPUT, true);
        configuration.add(CONF_MORPHOLOGY, "GERMAN");
        configuration.add(CONF_DECOMPOUND + "." + CONF_DECOMPOUND_MAX_EXPANSIONS, 5);
        configuration.add(CONF_DECOMPOUND + "." + CONF_DECOMPOUND_VERIFY_COLLATION, true);
        configuration.add(CONF_DECOMPOUND + "." + CONF_MORPHOLOGY, "DEFAULT");
        configuration.add(CONF_COMPOUND + "." + CONF_MORPHOLOGY, "GERMAN");
        configuration.add(CONF_REVERSE_COMPOUND_TRIGGER_WORDS, Lists.newArrayList("for"));

        Map<String, Object> parsed = factory.parseConfigurationToRequestHandlerBody(configuration, resourceLoader);
        factory.configure((Map<String, Object>) parsed.get(CONF_CONFIG));

        RewriterFactory rewriterFactory = factory.getRewriterFactory();

        assertThat(rewriterFactory).isInstanceOf(querqy.rewriter.wordbreak.WordBreakCompoundRewriterFactory.class);
        assertThat(factory.validateConfiguration((Map<String, Object>) parsed.get(CONF_CONFIG))).isNull();
    }

    @Test
    public void testParseInvalidCompoundingConfigurationIsInvalid() {
        NamedList<Object> configuration = new NamedList<>();
        configuration.add(CONF_CLASS, WordBreakCompoundRewriterFactory.class.getName());
        configuration.add(CONF_DICTIONARY_FIELD, "f1");
        configuration.add(CONF_MORPHOLOGY, "INVALID");

        Map<String, Object> parsed = factory.parseConfigurationToRequestHandlerBody(configuration, resourceLoader);
        List<String> validations = factory.validateConfiguration((Map<String, Object>) parsed.get(CONF_CONFIG));
        assertThat(validations).isNotNull();
        assertThat(validations).containsOnly("Cannot load morphology: INVALID");

        configuration.remove(CONF_MORPHOLOGY);
        configuration.add(CONF_DECOMPOUND + "." + CONF_MORPHOLOGY, "INVALID");

        parsed = factory.parseConfigurationToRequestHandlerBody(configuration, resourceLoader);
        validations = factory.validateConfiguration((Map<String, Object>) parsed.get(CONF_CONFIG));
        assertThat(validations).isNotNull();
        assertThat(validations).containsOnly("Cannot load decompound morphology: INVALID");

        configuration.remove(CONF_DECOMPOUND + "." + CONF_MORPHOLOGY);
        configuration.add(CONF_COMPOUND + "." + CONF_MORPHOLOGY, "INVALID");

        parsed = factory.parseConfigurationToRequestHandlerBody(configuration, resourceLoader);
        validations = factory.validateConfiguration((Map<String, Object>) parsed.get(CONF_CONFIG));
        assertThat(validations).isNotNull();
        assertThat(validations).containsOnly("Cannot load compound morphology: INVALID");
    }

    @Test
    public void testParseOptionalModifierConfiguration() {
        final NamedList<Object> configuration = new NamedList<>();
        configuration.add(CONF_CLASS, WordBreakCompoundRewriterFactory.class.getName());
        configuration.add(CONF_DICTIONARY_FIELD, "f1");
        configuration.add(CONF_DECOMPOUND + "." + CONF_DECOMPOUND_OPTIONAL_MODIFIER_POSITION, "first");
        configuration.add(CONF_DECOMPOUND + "." + CONF_DECOMPOUND_OPTIONAL_MODIFIER_BOOST, 2.5f);

        final Map<String, Object> parsed = factory.parseConfigurationToRequestHandlerBody(configuration,
                resourceLoader);
        assertThat(factory.validateConfiguration((Map<String, Object>) parsed.get(CONF_CONFIG))).isNull();
        factory.configure((Map<String, Object>) parsed.get(CONF_CONFIG));

        final querqy.rewriter.wordbreak.WordBreakCompoundRewriterFactory delegate =
                (querqy.rewriter.wordbreak.WordBreakCompoundRewriterFactory) factory.getRewriterFactory();
        assertThat(delegate.getOptionalModifierConfig().position())
                .isEqualTo(querqy.rewriter.wordbreak.OptionalModifierPosition.FIRST);
        assertThat(delegate.getOptionalModifierConfig().boost()).isEqualTo(2.5f);
    }

    @Test
    public void testOptionalModifierDefaultsToNone() {
        factory.configure(Map.of(CONF_DICTIONARY_FIELD, "f1"));
        final querqy.rewriter.wordbreak.WordBreakCompoundRewriterFactory delegate =
                (querqy.rewriter.wordbreak.WordBreakCompoundRewriterFactory) factory.getRewriterFactory();
        assertThat(delegate.getOptionalModifierConfig())
                .isEqualTo(querqy.rewriter.wordbreak.OptionalModifierConfig.DISABLED);
    }

    @Test
    public void testInvalidOptionalModifierConfigurationIsInvalid() {
        assertThat(factory.validateConfiguration(Map.of(CONF_DICTIONARY_FIELD, "f1",
                CONF_DECOMPOUND, Map.of(CONF_DECOMPOUND_OPTIONAL_MODIFIER_POSITION, "MIDDLE"))))
                .singleElement().asString().contains(CONF_DECOMPOUND_OPTIONAL_MODIFIER_POSITION, "MIDDLE");

        // a boost needs a position
        assertThat(factory.validateConfiguration(Map.of(CONF_DICTIONARY_FIELD, "f1",
                CONF_DECOMPOUND, Map.of(CONF_DECOMPOUND_OPTIONAL_MODIFIER_BOOST, 2f))))
                .singleElement().asString().contains("optionalModifierBoost");

        assertThat(factory.validateConfiguration(Map.of(CONF_DICTIONARY_FIELD, "f1",
                CONF_DECOMPOUND, Map.of(CONF_DECOMPOUND_OPTIONAL_MODIFIER_POSITION, "FIRST",
                        CONF_DECOMPOUND_OPTIONAL_MODIFIER_BOOST, "high"))))
                .singleElement().asString().contains(CONF_DECOMPOUND_OPTIONAL_MODIFIER_BOOST, "number");
    }
}
