package edu.uci.ics.asterix.metadata.feeds;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.asterix.common.api.IAsterixAppRuntimeContext;
import edu.uci.ics.asterix.common.feeds.DistributeFeedFrameWriter;
import edu.uci.ics.asterix.common.feeds.FeedConnectionId;
import edu.uci.ics.asterix.common.feeds.FeedFrameCollector;
import edu.uci.ics.asterix.common.feeds.FeedRuntime;
import edu.uci.ics.asterix.common.feeds.FeedRuntimeId;
import edu.uci.ics.asterix.common.feeds.FeedRuntimeInputHandler;
import edu.uci.ics.asterix.common.feeds.SubscribableFeedRuntimeId;
import edu.uci.ics.asterix.common.feeds.SubscribableRuntime;
import edu.uci.ics.asterix.common.feeds.api.IFeedManager;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.FeedRuntimeType;
import edu.uci.ics.asterix.common.feeds.api.IFeedRuntime.Mode;
import edu.uci.ics.asterix.common.feeds.api.ISubscribableRuntime;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.IActivity;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryInputUnaryOutputOperatorNodePushable;

public class FeedMetaComputeNodePushable extends AbstractUnaryInputUnaryOutputOperatorNodePushable {

    private static final Logger LOGGER = Logger.getLogger(FeedMetaComputeNodePushable.class.getName());

    /** Runtime node pushable corresponding to the core feed operator **/
    private AbstractUnaryInputUnaryOutputOperatorNodePushable coreOperator;

    /**
     * A policy enforcer that ensures dyanmic decisions for a feed are taken
     * in accordance with the associated ingestion policy
     **/
    private FeedPolicyEnforcer policyEnforcer;

    /**
     * The Feed Runtime instance associated with the operator. Feed Runtime
     * captures the state of the operator while the feed is active.
     */
    private FeedRuntime feedRuntime;

    /**
     * A unique identifier for the feed instance. A feed instance represents
     * the flow of data from a feed to a dataset.
     **/
    private FeedConnectionId connectionId;

    /**
     * Denotes the i'th operator instance in a setting where K operator
     * instances are scheduled to run in parallel
     **/
    private int partition;

    private int nPartitions;

    /** The (singleton) instance of IFeedManager **/
    private IFeedManager feedManager;

    private FrameTupleAccessor fta;

    private final IHyracksTaskContext ctx;

    private final String operandId;

    private final FeedRuntimeType runtimeType = FeedRuntimeType.COMPUTE;

    private FeedRuntimeInputHandler inputSideHandler;

    public FeedMetaComputeNodePushable(IHyracksTaskContext ctx, IRecordDescriptorProvider recordDescProvider,
            int partition, int nPartitions, IOperatorDescriptor coreOperator, FeedConnectionId feedConnectionId,
            Map<String, String> feedPolicyProperties, String operationId) throws HyracksDataException {
        this.ctx = ctx;
        this.coreOperator = (AbstractUnaryInputUnaryOutputOperatorNodePushable) ((IActivity) coreOperator)
                .createPushRuntime(ctx, recordDescProvider, partition, nPartitions);
        this.policyEnforcer = new FeedPolicyEnforcer(feedConnectionId, feedPolicyProperties);
        this.partition = partition;
        this.nPartitions = nPartitions;
        this.connectionId = feedConnectionId;
        this.feedManager = ((IAsterixAppRuntimeContext) (IAsterixAppRuntimeContext) ctx.getJobletContext()
                .getApplicationContext().getApplicationObject()).getFeedManager();
        IAsterixAppRuntimeContext runtimeCtx = (IAsterixAppRuntimeContext) ctx.getJobletContext()
                .getApplicationContext().getApplicationObject();
        this.feedManager = runtimeCtx.getFeedManager();
        this.operandId = operationId;
    }

    @Override
    public void open() throws HyracksDataException {
        FeedRuntimeId runtimeId = new SubscribableFeedRuntimeId(connectionId.getFeedId(), runtimeType, partition);
        try {
            feedRuntime = feedManager.getFeedConnectionManager().getFeedRuntime(connectionId, runtimeId);
            if (feedRuntime == null) {
                initializeNewFeedRuntime(runtimeId);
            } else {
                reviveOldFeedRuntime(runtimeId);
            }
            writer.open();
            coreOperator.open();
        } catch (Exception e) {
            e.printStackTrace();
            throw new HyracksDataException(e);
        }
    }

    private void initializeNewFeedRuntime(FeedRuntimeId runtimeId) throws Exception {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning("Runtime not found for  " + runtimeId + "[" + partition + "]" + "connection id "
                    + connectionId);
        }
        this.fta = new FrameTupleAccessor(ctx.getFrameSize(), recordDesc);
        this.inputSideHandler = new FeedRuntimeInputHandler(connectionId, runtimeId, coreOperator,
                policyEnforcer.getFeedPolicyAccessor(), true, ctx.getFrameSize(), fta, recordDesc, feedManager,
                nPartitions);

        DistributeFeedFrameWriter distributeWriter = new DistributeFeedFrameWriter(connectionId.getFeedId(), writer,
                runtimeType, partition, new FrameTupleAccessor(ctx.getFrameSize(), recordDesc), feedManager,
                ctx.getFrameSize());
        coreOperator.setOutputFrameWriter(0, distributeWriter, recordDesc);

        feedRuntime = new SubscribableRuntime(connectionId.getFeedId(), runtimeId, inputSideHandler, distributeWriter,
                recordDesc);
        feedManager.getFeedSubscriptionManager().registerFeedSubscribableRuntime((ISubscribableRuntime) feedRuntime);
        feedManager.getFeedConnectionManager().registerFeedRuntime(connectionId, feedRuntime);

        distributeWriter.subscribeFeed(policyEnforcer.getFeedPolicyAccessor(), writer, connectionId);
    }

    private void reviveOldFeedRuntime(FeedRuntimeId runtimeId) throws Exception {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Reviving old state from zombie instance  " + runtimeType + "[" + partition + "]" + " mode "
                    + feedRuntime.getInputHandler().getMode());
        }
        this.fta = new FrameTupleAccessor(ctx.getFrameSize(), recordDesc);
        this.inputSideHandler = feedRuntime.getInputHandler();
        this.inputSideHandler.setCoreOperator(coreOperator);

        DistributeFeedFrameWriter distributeWriter = new DistributeFeedFrameWriter(connectionId.getFeedId(), writer,
                runtimeType, partition, new FrameTupleAccessor(ctx.getFrameSize(), recordDesc), feedManager,
                ctx.getFrameSize());
        coreOperator.setOutputFrameWriter(0, distributeWriter, recordDesc);
        distributeWriter.subscribeFeed(policyEnforcer.getFeedPolicyAccessor(), writer, connectionId);

        inputSideHandler.resetMetrics();
        inputSideHandler.resetNumberOfPartitions(nPartitions);
        feedRuntime.setMode(Mode.PROCESS);
    }

    @Override
    public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
        try {
            inputSideHandler.nextFrame(buffer);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HyracksDataException(e);
        }
    }

    @Override
    public void fail() throws HyracksDataException {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.info("Core Op:" + coreOperator.getDisplayName() + " fail ");
        }
        feedRuntime.setMode(Mode.FAIL);
        coreOperator.fail();
    }

    @Override
    public void close() throws HyracksDataException {
        System.out.println("CLOSE CALLED FOR " + this.feedRuntime.getRuntimeId());
        boolean stalled = inputSideHandler.getMode().equals(Mode.STALL);
        boolean end = inputSideHandler.getMode().equals(Mode.END);
        try {
            if (inputSideHandler != null) {
                if (!(stalled || end)) {
                    inputSideHandler.nextFrame(null); // signal end of data
                    while (!inputSideHandler.isFinished()) {
                        synchronized (coreOperator) {
                            coreOperator.wait();
                        }
                    }
                    // inputSideHandler.close();
                }
            }
            coreOperator.close();
            System.out.println("CLOSED " + coreOperator);
        } catch (Exception e) {
            e.printStackTrace();
            // ignore
        } finally {
            if (!stalled) {
                deregister();
                System.out.println("DEREGISTERING " + this.feedRuntime.getRuntimeId());
            } else {
                System.out.println("NOT DEREGISTERING " + this.feedRuntime.getRuntimeId());
            }
            if (inputSideHandler != null) {
                inputSideHandler.close();
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Ending Operator  " + this.feedRuntime.getRuntimeId());
            }
        }
    }

    private void deregister() {
        if (feedRuntime != null) {
            SubscribableFeedRuntimeId runtimeId = (SubscribableFeedRuntimeId) feedRuntime.getRuntimeId();
            feedManager.getFeedSubscriptionManager().deregisterFeedSubscribableRuntime(runtimeId);
        }
    }

}