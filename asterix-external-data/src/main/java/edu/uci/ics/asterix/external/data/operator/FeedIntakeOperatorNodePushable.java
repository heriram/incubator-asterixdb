package edu.uci.ics.asterix.external.data.operator;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import edu.uci.ics.asterix.external.dataset.adapter.IDatasourceAdapter;
import edu.uci.ics.asterix.external.feed.lifecycle.AlterFeedMessage;
import edu.uci.ics.asterix.external.feed.lifecycle.FeedId;
import edu.uci.ics.asterix.external.feed.lifecycle.FeedSystemProvider;
import edu.uci.ics.asterix.external.feed.lifecycle.IFeedManager;
import edu.uci.ics.asterix.external.feed.lifecycle.IFeedMessage;
import edu.uci.ics.asterix.feed.managed.adapter.IManagedFeedAdapter;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryInputUnaryOutputOperatorNodePushable;

public class FeedIntakeOperatorNodePushable extends AbstractUnaryInputUnaryOutputOperatorNodePushable {

    private final IDatasourceAdapter adapter;
    private final int partition;
    private final IFeedManager feedManager;
    private final FeedId feedId;
    private final LinkedBlockingQueue<IFeedMessage> inbox;
    private FeedInboxMonitor feedInboxMonitor;

    public FeedIntakeOperatorNodePushable(FeedId feedId, IDatasourceAdapter adapter, int partition) {
        this.adapter = adapter;
        this.partition = partition;
        this.feedManager = (IFeedManager) FeedSystemProvider.getFeedManager();
        this.feedId = feedId;
        inbox = new LinkedBlockingQueue<IFeedMessage>();
    }

    @Override
    public void open() throws HyracksDataException {
        if (adapter instanceof IManagedFeedAdapter) {
            feedInboxMonitor = new FeedInboxMonitor((IManagedFeedAdapter) adapter, inbox, partition);
            feedInboxMonitor.start();
            feedManager.registerFeedOperatorMsgQueue(feedId, inbox);
        }
        writer.open();
        try {
            adapter.start(partition, writer);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HyracksDataException(e);
            // we do not throw an exception, but allow the operator to close
            // gracefully
            // Throwing an exception here would result in a job abort and a
            // transaction roll back
            // that undoes all the work done so far.

        } finally {
            writer.close();
            if (adapter instanceof IManagedFeedAdapter) {
                feedManager.unregisterFeedOperatorMsgQueue(feedId, inbox);
            }
        }
    }

    @Override
    public void fail() throws HyracksDataException {
        writer.close();
    }

    @Override
    public void close() throws HyracksDataException {
        writer.close();
    }

    @Override
    public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
        // do nothing
    }
}

class FeedInboxMonitor extends Thread {

    private LinkedBlockingQueue<IFeedMessage> inbox;
    private final IManagedFeedAdapter adapter;

    public FeedInboxMonitor(IManagedFeedAdapter adapter, LinkedBlockingQueue<IFeedMessage> inbox, int partition) {
        this.inbox = inbox;
        this.adapter = adapter;
    }

    @Override
    public void run() {
        while (true) {
            try {
                IFeedMessage feedMessage = inbox.take();
                switch (feedMessage.getMessageType()) {
                    case STOP:
                        adapter.stop();
                        break;
                    case ALTER:
                        adapter.alter(((AlterFeedMessage) feedMessage).getAlteredConfParams());
                        break;
                }
            } catch (InterruptedException ie) {
                break;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}