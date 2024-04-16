package com.netflix.hollow.api.producer.metrics;

import static org.mockito.Mockito.when;

import com.netflix.hollow.api.producer.HollowProducer;
import com.netflix.hollow.api.producer.Status;
import com.netflix.hollow.api.producer.listener.CycleListener;
import com.netflix.hollow.core.read.engine.HollowReadStateEngine;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AbstractProducerMetricsListenerTest {

    private static final long TEST_VERSION = 123L;
    private static final long TEST_LAST_CYCLE_NANOS = 100L;
    private static final long TEST_LAST_ANNOUNCEMENT_NANOS = 200L;
    private static final long TEST_DATA_SIZE = 55L;
    private final com.netflix.hollow.api.producer.Status testStatusSuccess = new Status(Status.StatusType.SUCCESS, null);
    private final com.netflix.hollow.api.producer.Status testStatusFail = new Status(Status.StatusType.FAIL, null);
    private final Duration testCycleDurationMillis = Duration.ofMillis(4l);
    private static final long TEST_ANNOUNCEMENT_DURATION_MILLIS = 2L;

    @Mock
    private HollowProducer.ReadState mockReadState;

    @Mock
    private HollowReadStateEngine mockStateEngine;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mockReadState.getStateEngine()).thenReturn(mockStateEngine);
        when(mockStateEngine.calcApproxDataSize()).thenReturn(TEST_DATA_SIZE);
    }

    @Test
    public void testCycleSkipWhenNeverBeenPrimaryProducer() {
        final class TestProducerMetricsListener extends AbstractProducerMetricsListener {
            @Override
            public void cycleMetricsReporting(CycleMetrics cycleMetrics) {
                Assert.assertNotNull(cycleMetrics);
                Assert.assertEquals(0l, cycleMetrics.getConsecutiveFailures());
                Assert.assertEquals(Optional.empty(), cycleMetrics.getIsCycleSuccess());
                Assert.assertEquals(OptionalLong.empty(), cycleMetrics.getCycleDurationMillis());
                Assert.assertEquals(OptionalLong.empty(), cycleMetrics.getLastCycleSuccessTimeNano());
            }
        }
        AbstractProducerMetricsListener concreteProducerMetricsListener = new TestProducerMetricsListener();
        concreteProducerMetricsListener.onCycleSkip(CycleListener.CycleSkipReason.NOT_PRIMARY_PRODUCER);
    }

    @Test
    public void testCycleSkipWhenPreviouslyPrimaryProducer() {
        final class TestProducerMetricsListener extends AbstractProducerMetricsListener {
            @Override
            public void cycleMetricsReporting(CycleMetrics cycleMetrics) {
                Assert.assertNotNull(cycleMetrics);
                Assert.assertEquals(0l, cycleMetrics.getConsecutiveFailures());
                Assert.assertEquals(Optional.empty(), cycleMetrics.getIsCycleSuccess());
                Assert.assertEquals(OptionalLong.empty(), cycleMetrics.getCycleDurationMillis());
                Assert.assertEquals(OptionalLong.of(TEST_LAST_CYCLE_NANOS), cycleMetrics.getLastCycleSuccessTimeNano());
            }
        }
        AbstractProducerMetricsListener concreteProducerMetricsListener = new TestProducerMetricsListener();
        concreteProducerMetricsListener.lastCycleSuccessTimeNanoOptional = OptionalLong.of(TEST_LAST_CYCLE_NANOS);
        concreteProducerMetricsListener.onCycleSkip(CycleListener.CycleSkipReason.NOT_PRIMARY_PRODUCER);
    }

    @Test
    public void testCycleCompleteWithSuccess() {
        final class TestProducerMetricsListener extends AbstractProducerMetricsListener {
            @Override
            public void cycleMetricsReporting(CycleMetrics cycleMetrics) {
                Assert.assertNotNull(cycleMetrics);
                Assert.assertEquals(0l, cycleMetrics.getConsecutiveFailures());
                Assert.assertEquals(Optional.of(true), cycleMetrics.getIsCycleSuccess());
                Assert.assertEquals(OptionalLong.of(testCycleDurationMillis.toMillis()), cycleMetrics.getCycleDurationMillis());
                Assert.assertNotEquals(OptionalLong.of(TEST_LAST_CYCLE_NANOS), cycleMetrics.getLastCycleSuccessTimeNano());
                Assert.assertNotEquals(OptionalLong.empty(), cycleMetrics.getLastCycleSuccessTimeNano());
            }
        }

        AbstractProducerMetricsListener concreteProducerMetricsListener = new TestProducerMetricsListener();
        concreteProducerMetricsListener.lastCycleSuccessTimeNanoOptional = OptionalLong.of(TEST_LAST_CYCLE_NANOS);
        concreteProducerMetricsListener.onCycleStart(TEST_VERSION);
        concreteProducerMetricsListener.onCycleComplete(testStatusSuccess, mockReadState, TEST_VERSION, testCycleDurationMillis);
    }

    @Test
    public void testCycleCompleteWithFail() {
        final class TestProducerMetricsListener extends AbstractProducerMetricsListener {
            @Override
            public void cycleMetricsReporting(CycleMetrics cycleMetrics) {
                Assert.assertNotNull(cycleMetrics);
                Assert.assertEquals(1l, cycleMetrics.getConsecutiveFailures());
                Assert.assertEquals(Optional.of(false), cycleMetrics.getIsCycleSuccess());
                Assert.assertEquals(OptionalLong.of(testCycleDurationMillis.toMillis()), cycleMetrics.getCycleDurationMillis());
                Assert.assertEquals(OptionalLong.of(TEST_LAST_CYCLE_NANOS), cycleMetrics.getLastCycleSuccessTimeNano());
            }
        }

        AbstractProducerMetricsListener concreteProducerMetricsListener = new TestProducerMetricsListener();
        concreteProducerMetricsListener.lastCycleSuccessTimeNanoOptional = OptionalLong.of(TEST_LAST_CYCLE_NANOS);
        concreteProducerMetricsListener.onCycleStart(TEST_VERSION);
        concreteProducerMetricsListener.onCycleComplete(testStatusFail, mockReadState, TEST_VERSION, testCycleDurationMillis);
    }

    @Test
    public void testAnnouncementCompleteWithSuccess() {
        final class TestProducerMetricsListener extends AbstractProducerMetricsListener {
            @Override
            public void announcementMetricsReporting(AnnouncementMetrics announcementMetrics) {
                Assert.assertNotNull(announcementMetrics);
                Assert.assertEquals(TEST_DATA_SIZE, announcementMetrics.getDataSizeBytes());
                Assert.assertEquals(true, announcementMetrics.getIsAnnouncementSuccess());
                Assert.assertEquals(TEST_ANNOUNCEMENT_DURATION_MILLIS,
                        announcementMetrics.getAnnouncementDurationMillis());
                Assert.assertNotEquals(OptionalLong.of(TEST_LAST_ANNOUNCEMENT_NANOS),
                        announcementMetrics.getLastAnnouncementSuccessTimeNano());
            }
        }

        AbstractProducerMetricsListener concreteProducerMetricsListener = new TestProducerMetricsListener();
        concreteProducerMetricsListener.lastAnnouncementSuccessTimeNanoOptional = OptionalLong.of(
                TEST_LAST_ANNOUNCEMENT_NANOS);
        concreteProducerMetricsListener.onAnnouncementStart(TEST_VERSION);
        concreteProducerMetricsListener.onAnnouncementComplete(testStatusSuccess, mockReadState, TEST_VERSION, Duration.ofMillis(TEST_ANNOUNCEMENT_DURATION_MILLIS));
    }

    @Test
    public void testAnnouncementCompleteWithFail() {
        final class TestProducerMetricsListener extends AbstractProducerMetricsListener {
            @Override
            public void announcementMetricsReporting(AnnouncementMetrics announcementMetrics) {
                Assert.assertNotNull(announcementMetrics);
                Assert.assertEquals(TEST_DATA_SIZE, announcementMetrics.getDataSizeBytes());
                Assert.assertFalse(announcementMetrics.getIsAnnouncementSuccess());
                Assert.assertEquals(TEST_ANNOUNCEMENT_DURATION_MILLIS,
                        announcementMetrics.getAnnouncementDurationMillis());
                Assert.assertEquals(OptionalLong.of(TEST_LAST_ANNOUNCEMENT_NANOS),
                        announcementMetrics.getLastAnnouncementSuccessTimeNano());
            }
        }

        AbstractProducerMetricsListener concreteProducerMetricsListener = new TestProducerMetricsListener();
        concreteProducerMetricsListener.lastAnnouncementSuccessTimeNanoOptional = OptionalLong.of(
                TEST_LAST_ANNOUNCEMENT_NANOS);
        concreteProducerMetricsListener.onAnnouncementStart(TEST_VERSION);
        concreteProducerMetricsListener.onAnnouncementComplete(testStatusFail, mockReadState, TEST_VERSION, Duration.ofMillis(TEST_ANNOUNCEMENT_DURATION_MILLIS));
    }
}
