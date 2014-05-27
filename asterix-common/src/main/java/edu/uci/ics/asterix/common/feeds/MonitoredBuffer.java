/*
 * Copyright 2009-2014 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.common.feeds;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import edu.uci.ics.asterix.common.feeds.MonitoredBufferTimerTasks.MonitoredBufferProcessRateTimerTask;
import edu.uci.ics.asterix.common.feeds.MonitoredBufferTimerTasks.MonitoredBufferStorageTimerTask;
import edu.uci.ics.asterix.common.feeds.api.IExceptionHandler;
import edu.uci.ics.asterix.common.feeds.api.IFeedMetricCollector;
import edu.uci.ics.asterix.common.feeds.api.IFeedMetricCollector.MetricType;
import edu.uci.ics.asterix.common.feeds.api.IFeedMetricCollector.ValueType;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.FeedRuntimeType;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.Mode;
import edu.uci.ics.asterix.common.feeds.api.IFrameEventCallback;
import edu.uci.ics.asterix.common.feeds.api.IFrameEventCallback.FrameEvent;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;

public class MonitoredBuffer extends MessageReceiver<DataBucket> {

    private static final long MONITOR_FREQUENCY = 2000; // 2 seconds
    private static final long PROCESSING_RATE_MEASURE_FREQUENCY = 10000; // 10 seconds
    private static final long STORAGE_TIME_TRACKING_FREQUENCY = 5000; // 10 seconds

    private static final int PROCESS_RATE_REFRESH = 2; // refresh processing rate every 10th frame

    private final FeedRuntimeId runtimeId;
    private final FrameTupleAccessor inflowFta;
    private final FrameTupleAccessor outflowFta;
    private final FeedRuntimeInputHandler inputHandler;
    private final IFrameEventCallback callback;
    private final Timer timer;
    private final int frameSize;
    private final RecordDescriptor recordDesc;
    private final IExceptionHandler exceptionHandler;
    private final FeedPolicyAccessor policyAccessor;

    private IFrameWriter frameWriter;
    private IFeedMetricCollector metricCollector;
    private boolean processingRateEnabled = false;
    private boolean trackDataMovementRate = false;
    private boolean storageTimeTrackingEnabled = false;
    private int inflowReportSenderId = -1;
    private int outflowReportSenderId = -1;
    private TimerTask monitorTask;
    private TimerTask processingRateTask;
    private MonitoredBufferStorageTimerTask storageTimeTrackingRateTask;
    private StorageFrameHandler storageFromeHandler;

    private int processingRate = -1;
    private int frameCount = 0;
    private long avgDelayPersistence = 0;
    private boolean active;
    private Map<Integer, Long> tupleTimeStats;

    public MonitoredBuffer(FeedRuntimeInputHandler inputHandler, IFrameWriter frameWriter, FrameTupleAccessor fta,
            int frameSize, RecordDescriptor recordDesc, IFeedMetricCollector metricCollector,
            FeedConnectionId connectionId, FeedRuntimeId runtimeId, IExceptionHandler exceptionHandler,
            IFrameEventCallback callback, int nPartitions, FeedPolicyAccessor policyAccessor) {
        this.frameWriter = frameWriter;
        this.inflowFta = new FrameTupleAccessor(frameSize, recordDesc);
        this.outflowFta = new FrameTupleAccessor(frameSize, recordDesc);
        this.runtimeId = runtimeId;
        this.metricCollector = metricCollector;
        this.exceptionHandler = exceptionHandler;
        this.callback = callback;
        this.inputHandler = inputHandler;
        this.timer = new Timer();
        this.frameSize = frameSize;
        this.recordDesc = recordDesc;
        this.policyAccessor = policyAccessor;

        initializePeriodicMonitoring(connectionId, nPartitions, policyAccessor);
        this.active = true;

    }

    private void initializePeriodicMonitoring(FeedConnectionId connectionId, int nPartitions,
            FeedPolicyAccessor policyAccessor) {
        switch (runtimeId.getFeedRuntimeType()) {
            case COMPUTE:
                processingRateEnabled = true;
                trackDataMovementRate = true;
                break;
            case STORE:
                storageTimeTrackingEnabled = policyAccessor.isTimeTrackingEnabled();
                if (storageTimeTrackingEnabled) {
                    storageFromeHandler = new StorageFrameHandler();
                }
            default:
                break;
        }
        if (trackDataMovementRate) {
            this.monitorTask = new MonitoredBufferTimerTasks.MonitoredBufferDataFlowRateMeasureTimerTask(this, callback);
            this.timer.scheduleAtFixedRate(monitorTask, 0, MONITOR_FREQUENCY);
        }
        if (processingRateEnabled) {
            this.processingRateTask = new MonitoredBufferTimerTasks.MonitoredBufferProcessRateTimerTask(this,
                    inputHandler.getFeedManager(), connectionId, nPartitions);
            this.timer.scheduleAtFixedRate(processingRateTask, 0, PROCESSING_RATE_MEASURE_FREQUENCY);
        }
        if (trackDataMovementRate || processingRateEnabled) {
            this.inflowReportSenderId = metricCollector.createReportSender(connectionId, runtimeId,
                    ValueType.INFLOW_RATE, MetricType.RATE);
            this.outflowReportSenderId = metricCollector.createReportSender(connectionId, runtimeId,
                    ValueType.OUTFLOW_RATE, MetricType.RATE);
        }

        if (storageTimeTrackingEnabled) {
            this.storageTimeTrackingRateTask = new MonitoredBufferTimerTasks.MonitoredBufferStorageTimerTask(this,
                    inputHandler.getFeedManager(), connectionId, runtimeId.getPartition(), policyAccessor,
                    storageFromeHandler);
            this.timer.scheduleAtFixedRate(storageTimeTrackingRateTask, 0, STORAGE_TIME_TRACKING_FREQUENCY);
        }
    }

    @Override
    public void sendMessage(DataBucket message) {
        inbox.add(message);
        if (trackDataMovementRate
                && !(inputHandler.getMode().equals(Mode.PROCESS_BACKLOG) || inputHandler.getMode().equals(
                        Mode.PROCESS_SPILL))) {
            inflowFta.reset(message.getContent());
            metricCollector.sendReport(inflowReportSenderId, inflowFta.getTupleCount());
        }
    }

    /** return rate in terms of tuples/sec **/
    public int getInflowRate() {
        return metricCollector.getMetric(inflowReportSenderId);
    }

    /** return rate in terms of tuples/sec **/
    public int getOutflowRate() {
        return metricCollector.getMetric(outflowReportSenderId);
    }

    /** return the number of pending frames from the input queue **/
    public int getWorkSize() {
        return inbox.size();
    }

    /** reset the number of partitions (cardinality) for the runtime **/
    public void setNumberOfPartitions(int nPartitions) {
        if (processingRateTask != null) {
            int currentPartitions = ((MonitoredBufferProcessRateTimerTask) processingRateTask).getNumberOfPartitions();
            if (currentPartitions != nPartitions) {
                ((MonitoredBufferProcessRateTimerTask) processingRateTask).setNumberOfPartitions(nPartitions);
            }
        }
    }

    public synchronized void close(boolean processPending, boolean disableMonitoring) {
        super.close(processPending);
        if (disableMonitoring) {
            if (monitorTask != null) {
                monitorTask.cancel();
            }
            if (processingRateTask != null) {
                processingRateTask.cancel();
            }

            if (storageTimeTrackingRateTask != null) {
                storageTimeTrackingRateTask.cancel();
            }

            if (trackDataMovementRate || processingRateEnabled) {
                metricCollector.removeReportSender(inflowReportSenderId);
                metricCollector.removeReportSender(outflowReportSenderId);
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Disabled monitoring for " + this.runtimeId);
            }
        }
        active = false;
    }

    @Override
    public synchronized void processMessage(DataBucket message) throws Exception {
        if (!active) {
            message.doneReading();
            return;
        }
        switch (message.getContentType()) {
            case DATA:
                boolean finishedProcessing = false;
                ByteBuffer frame = message.getContent();
                outflowFta.reset(frame);
                long startTime = 0;
                long endTime = 0;
                while (!finishedProcessing) {
                    try {
                        inflowFta.reset(frame);
                        startTime = System.currentTimeMillis();

                        try {
                            if (runtimeId.getFeedRuntimeType().equals(FeedRuntimeType.STORE)
                                    && storageTimeTrackingEnabled) {
                                if (storageTimeTrackingEnabled) {
                                    storageFromeHandler.updateTrackingInformation(frame, inflowFta);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        frameWriter.nextFrame(frame);
                        if (processingRateEnabled) {
                            frameCount++;
                            if (frameCount % PROCESS_RATE_REFRESH == 0) {
                                endTime = System.currentTimeMillis();
                                processingRate = (int) ((double) outflowFta.getTupleCount() * 1000 / (endTime - startTime));
                                if (LOGGER.isLoggable(Level.INFO)) {
                                    LOGGER.info("Processing Rate :" + processingRate + " tuples/sec");
                                }
                                frameCount = 0;
                            }
                        }
                        if (trackDataMovementRate) {
                            metricCollector.sendReport(outflowReportSenderId, outflowFta.getTupleCount());
                        }
                        finishedProcessing = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        frame = exceptionHandler.handleException(e, frame);
                    }
                }
                message.doneReading();
                break;
            case EOD:
                message.doneReading();
                timer.cancel();
                callback.frameEvent(FrameEvent.FINISHED_PROCESSING);
                break;
            case EOSD:
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Done processing spillage");
                }
                message.doneReading();
                callback.frameEvent(FrameEvent.FINISHED_PROCESSING_SPILLAGE);
                break;

        }
    }

    public Mode getMode() {
        return inputHandler.getMode();
    }

    public FeedRuntimeId getRuntimeId() {
        return runtimeId;
    }

    public void setFrameWriter(IFrameWriter frameWriter) {
        this.frameWriter = frameWriter;
    }

    public void reset() {
        active = true;
        if (trackDataMovementRate || processingRateEnabled) {
            metricCollector.resetReportSender(inflowReportSenderId);
            metricCollector.resetReportSender(outflowReportSenderId);
        }
    }

    public int getProcessingRate() {
        return processingRate;
    }

    public Map<Integer, Long> getTupleTimeStats() {
        return tupleTimeStats;
    }

    public long getAvgDelayRecordPersistence() {
        return avgDelayPersistence;
    }

    public MonitoredBufferStorageTimerTask getStorageTimeTrackingRateTask() {
        return storageTimeTrackingRateTask;
    }

}