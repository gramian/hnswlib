package com.github.jelmerk.knn.metrics.dropwizard;

import com.codahale.metrics.MetricRegistry;
import com.github.jelmerk.knn.Index;
import com.github.jelmerk.knn.SearchResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static com.codahale.metrics.MetricRegistry.name;

@RunWith(MockitoJUnitRunner.class)
public class StatisticsDecoratorTest {

    @Mock
    private Index<String, float[], TestItem, Float> approximativeIndex;

    @Mock
    private Index<String, float[], TestItem, Float> groundTruthIndex;

    private String indexName  = "testindex";

    private int maxAccuracySampleFrequency = 1;

    private MetricRegistry metricRegistry;

    private TestItem item1 = new TestItem("1", new float[0]);
    private TestItem item2 = new TestItem("2", new float[0]);

    private int k = 10;

    private StatisticsDecorator<String, float[], TestItem, Float, Index<String, float[], TestItem, Float>, Index<String, float[], TestItem, Float>> decorator;

    @Before
    public void setUp() {
        this.metricRegistry = new MetricRegistry();
        this.decorator = new StatisticsDecorator<>(metricRegistry, StatisticsDecoratorTest.class,
                indexName, approximativeIndex, groundTruthIndex, maxAccuracySampleFrequency);
    }

    @Test
    public void timesAdd() {
        decorator.add(item1);
        verify(approximativeIndex).add(item1);
        assertThat(metricRegistry.timer(name(getClass(), indexName, "add")).getCount(), is(1L));
    }

    @Test
    public void timesRemove() {
        decorator.remove(item1.id(), item1.version());
        verify(approximativeIndex).remove(item1.id(), item1.version());
        assertThat(metricRegistry.timer(name(getClass(), indexName, "remove")).getCount(), is(1L));
    }

    @Test
    public void returnsSize() {
        int size = 10;
        given(approximativeIndex.size()).willReturn(size);
        assertThat(decorator.size(), is(size));
    }

    @Test
    public void timesGet() {
        Optional<TestItem> getResult = Optional.of(this.item1);
        given(approximativeIndex.get(this.item1.id())).willReturn(getResult);
        assertThat(decorator.get(this.item1.id()), is(getResult));
        assertThat(metricRegistry.timer(name(getClass(), indexName, "get")).getCount(), is(1L));
    }

    @Test
    public void returnsItems() {
        List<TestItem> items = Collections.singletonList(item1);
        given(approximativeIndex.items()).willReturn(items);
        assertThat(decorator.items(), is(items));
    }

    @Test
    public void timesFindNearest() {
        List<SearchResult<TestItem, Float>> searchResults = Collections.singletonList(new SearchResult<>(item1, 0.1f, Comparator.naturalOrder()));

        given(approximativeIndex.findNearest(item1.vector(), k)).willReturn(searchResults);
        assertThat(decorator.findNearest(item1.vector(), k), is(searchResults));
        assertThat(metricRegistry.timer(name(getClass(), indexName, "findNearest")).getCount(), is(1L));
    }

    @Test
    public void measuresFindNearestAccuracy() {
        List<SearchResult<TestItem, Float>> approximateResults = Collections.singletonList(
                new SearchResult<>(item1, 0.1f, Comparator.naturalOrder())
        );

        List<SearchResult<TestItem, Float>> groundTruthResults = Arrays.asList(
            new SearchResult<>(item1, 0.1f, Comparator.naturalOrder()),
            new SearchResult<>(item2, 0.1f, Comparator.naturalOrder())
        );

        given(approximativeIndex.findNearest(item1.vector(), k)).willReturn(approximateResults);
        given(groundTruthIndex.findNearest(item1.vector(), k)).willReturn(groundTruthResults);

        assertThat(decorator.findNearest(item1.vector(), k), is(approximateResults));
        await().untilAsserted(() -> assertThat(metricRegistry.histogram(name(getClass(), indexName, "accuracy")).getSnapshot().getMax(), is(50L)));
    }

    @Test
    public void timesSave() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        decorator.save(baos);
        assertThat(metricRegistry.timer(name(getClass(), indexName, "save")).getCount(), is(1L));
        verify(approximativeIndex).save(baos);
    }

    @Test
    public void returnsApproximativeIndex() {
        assertThat(decorator.getApproximativeIndex(), is(approximativeIndex));
    }

    @Test
    public void returnsGroundTruthIndex() {
        assertThat(decorator.getGroundTruthIndex(), is(groundTruthIndex));
    }
}
