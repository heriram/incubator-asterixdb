package edu.uci.ics.asterix.external.dataset.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.common.feeds.api.IDatasourceAdapter;
import edu.uci.ics.asterix.metadata.utils.IAsterixTupleParserFactory;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.std.file.ITupleParser;

public abstract class StreamBasedAdapter implements IDatasourceAdapter {

    private static final long serialVersionUID = 1L;

    protected static final Logger LOGGER = Logger.getLogger(StreamBasedAdapter.class.getName());

    public abstract InputStream getInputStream(int partition) throws IOException;

    protected final ITupleParser tupleParser;

    protected final IAType sourceDatatype;

    public StreamBasedAdapter(IAsterixTupleParserFactory parserFactory, IAType sourceDatatype, IHyracksTaskContext ctx,
            int partition) throws HyracksDataException {
        this.tupleParser = parserFactory.createTupleParser(ctx);
        parserFactory.initialize(partition);
        this.sourceDatatype = sourceDatatype;
    }

    @Override
    public void start(int partition, IFrameWriter writer) throws Exception {
        InputStream in = getInputStream(partition);
        if (in != null) {
            tupleParser.parse(in, writer);
        } else {
            String message = "Could not obtain input stream for parsing from adaptor " + this + "[" + partition + "]";
            LOGGER.warning(message);
            throw new AsterixException(message);
        }
    }
}
