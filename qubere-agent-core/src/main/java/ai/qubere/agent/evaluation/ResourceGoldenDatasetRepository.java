package ai.qubere.agent.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ResourceGoldenDatasetRepository extends InMemoryGoldenDatasetRepository {

    public static final String DEFAULT_LOCATION = "classpath*:agent-evaluation/*.json";

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourcePatternResolver;
    private final boolean failOnInvalidDataset;

    public ResourceGoldenDatasetRepository(Collection<String> locations, ResourceLoader resourceLoader, boolean failOnInvalidDataset) {
        this(locations, resourceLoader, failOnInvalidDataset, defaultObjectMapper());
    }

    public ResourceGoldenDatasetRepository(
            Collection<String> locations,
            ResourceLoader resourceLoader,
            boolean failOnInvalidDataset,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper == null ? defaultObjectMapper() : objectMapper.copy();
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        this.failOnInvalidDataset = failOnInvalidDataset;
        loadAll(locations == null || locations.isEmpty() ? Set.of(DEFAULT_LOCATION) : locations);
    }

    private void loadAll(Collection<String> locations) {
        for (String location : locations) {
            if (location == null || location.isBlank()) {
                continue;
            }
            loadLocation(location.trim());
        }
    }

    private void loadLocation(String location) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(location);
            for (Resource resource : resources) {
                if (resource.exists() && resource.isReadable()) {
                    loadResource(resource);
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (failOnInvalidDataset) {
                throw new IllegalStateException("Unable to load golden dataset location: " + location, ex);
            }
        }
    }

    private void loadResource(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            if (root == null || root.isNull()) {
                return;
            }
            if (root.isArray()) {
                objectMapper.readerForListOf(GoldenDataset.class).<List<GoldenDataset>>readValue(root)
                        .forEach(this::save);
                return;
            }
            if (root.has("datasets")) {
                objectMapper.treeToValue(root, GoldenDatasetCollection.class).datasets().forEach(this::save);
                return;
            }
            save(objectMapper.treeToValue(root, GoldenDataset.class));
        } catch (IOException | RuntimeException ex) {
            if (failOnInvalidDataset) {
                throw new IllegalStateException("Unable to load golden dataset resource: " + resource.getDescription(), ex);
            }
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .build();
    }
}