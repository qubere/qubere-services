package ai.qubere.agent.evaluation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeGoldenDatasetRepositoryTest {

    private final InMemoryGoldenDatasetRepository primary = new InMemoryGoldenDatasetRepository();
    private final InMemoryGoldenDatasetRepository fallback = new InMemoryGoldenDatasetRepository();
    private final CompositeGoldenDatasetRepository composite = new CompositeGoldenDatasetRepository(primary, fallback);

    @Test
    void findsDatasetFromFallbackWhenPrimaryHasNone() {
        fallback.save(dataset("packaged", "from classpath"));

        assertThat(composite.find("packaged"))
                .get()
                .satisfies(dataset -> assertThat(dataset.description()).isEqualTo("from classpath"));
    }

    @Test
    void primaryOverridesSameNamedFallbackDataset() {
        fallback.save(dataset("shared", "packaged version"));
        primary.save(dataset("shared", "operationally curated version"));

        assertThat(composite.find("shared"))
                .get()
                .satisfies(dataset -> assertThat(dataset.description()).isEqualTo("operationally curated version"));
    }

    @Test
    void listMergesBothSourcesWithPrimaryWinningOnCollision() {
        fallback.save(dataset("only-packaged", "packaged"));
        fallback.save(dataset("shared", "packaged version"));
        primary.save(dataset("shared", "curated version"));
        primary.save(dataset("only-curated", "curated"));

        assertThat(composite.list())
                .hasSize(3)
                .extracting(GoldenDataset::name)
                .containsExactlyInAnyOrder("only-packaged", "shared", "only-curated");

        assertThat(composite.find("shared"))
                .get()
                .satisfies(dataset -> assertThat(dataset.description()).isEqualTo("curated version"));
    }

    @Test
    void savesGoToPrimaryBecauseClasspathResourcesAreNotWritable() {
        composite.save(dataset("new-dataset", "written"));

        assertThat(primary.find("new-dataset")).isPresent();
        assertThat(fallback.find("new-dataset")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNeitherSourceHasTheDataset() {
        assertThat(composite.find("missing")).isEmpty();
    }

    private GoldenDataset dataset(String name, String description) {
        return new GoldenDataset(name, description, List.of(), Map.of());
    }
}
