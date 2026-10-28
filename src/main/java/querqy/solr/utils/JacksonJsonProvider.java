package querqy.solr.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import querqy.json.JsonProvider;

import java.io.IOException;
import java.util.Map;

/**
 * A {@link JsonProvider} backed by the Jackson {@link ObjectMapper} that is provided by Solr. Null and empty
 * values are omitted when serializing. The mapper does not use polymorphic (default) typing, as required by the
 * {@link JsonProvider} contract.
 */
public class JacksonJsonProvider implements JsonProvider {

    public static final JacksonJsonProvider INSTANCE = new JacksonJsonProvider();

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY,
                    JsonInclude.Include.NON_EMPTY));

    @Override
    public Map<String, Object> readMap(final String json) throws IOException {
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Override
    public <T> T readValue(final String json, final Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }

    @Override
    public String writeValueAsString(final Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }
}
