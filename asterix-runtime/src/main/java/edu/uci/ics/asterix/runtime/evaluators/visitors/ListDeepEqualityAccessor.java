package edu.uci.ics.asterix.runtime.evaluators.visitors;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.dataflow.data.nontagged.comparators.ListItemBinaryComparatorFactory;
import edu.uci.ics.asterix.dataflow.data.nontagged.hash.ListItemBinaryHashFunctionFactory;
import edu.uci.ics.asterix.om.pointables.AFlatValuePointable;
import edu.uci.ics.asterix.om.pointables.AListPointable;
import edu.uci.ics.asterix.om.pointables.ARecordPointable;
import edu.uci.ics.asterix.om.pointables.base.IVisitablePointable;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.runtime.evaluators.functions.BinaryHashMap;
import edu.uci.ics.asterix.runtime.evaluators.functions.BinaryHashMap.BinaryEntry;
import edu.uci.ics.hyracks.algebricks.common.utils.Pair;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunction;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.data.std.primitive.IntegerPointable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class ListDeepEqualityAccessor {
    private DeepEqualityVisitor visitor;

    private final int TABLE_SIZE = 100;
    private final int TABLE_FRAME_SIZE = 32768;

    private IBinaryHashFunction putHashFunc = ListItemBinaryHashFunctionFactory.INSTANCE.createBinaryHashFunction();
    private IBinaryHashFunction getHashFunc = ListItemBinaryHashFunctionFactory.INSTANCE.createBinaryHashFunction();
    private IBinaryComparator cmp = ListItemBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private BinaryHashMap hashMap = new BinaryHashMap(TABLE_SIZE, TABLE_FRAME_SIZE, putHashFunc, getHashFunc, cmp);
    private BinaryEntry keyEntry = new BinaryEntry();
    private BinaryEntry valEntry = new BinaryEntry();



    public ListDeepEqualityAccessor() {
        byte[] emptyValBuf = new byte[8];
        Arrays.fill(emptyValBuf, (byte) 0);
        valEntry.set(emptyValBuf, 0, 8);
    }

    public boolean accessList(IVisitablePointable listAccessor0, IVisitablePointable listAccessor1,
            DeepEqualityVisitor visitor) throws IOException, AsterixException {

        this.visitor = visitor;

        AListPointable list0 = (AListPointable)listAccessor0;
        List<IVisitablePointable> items0 = list0.getItems();
        List<IVisitablePointable> itemTagTypes0 = list0.getItemTags();


        AListPointable list1 = (AListPointable)listAccessor1;
        List<IVisitablePointable> items1 = list1.getItems();
        List<IVisitablePointable> itemTagTypes1 = list1.getItemTags();

        if (items0.size() != items1.size()) return false;

        boolean isOrdered1 = list0.ordered();
        if (isOrdered1 != list1.ordered())
            return false;

        if( isOrdered1) {
            return processOrderedList(items0, itemTagTypes0, items1, itemTagTypes1);
        } else {
            return processUnorderedList(items0, itemTagTypes0, items1, itemTagTypes1);
        }
    }

    private boolean compareListItems(ATypeTag fieldType0, IVisitablePointable item0, IVisitablePointable item1)
            throws AsterixException {
        Pair<IVisitablePointable, Boolean> arg = new Pair<IVisitablePointable, Boolean>(item1, false);
        switch (fieldType0) {
            case ORDEREDLIST:
            case UNORDEREDLIST:
                ((AListPointable)item0).accept(visitor, arg);
                break;
            case RECORD:
                ((ARecordPointable)item0).accept(visitor, arg);
                break;
            case ANY:
                return false;
            // TODO Should have a way to check "ANY" types too
            default:
                ((AFlatValuePointable)item0).accept(visitor, arg);
        }

        return arg.second;
    }

    private boolean processOrderedList(List<IVisitablePointable> items0, List<IVisitablePointable> itemTagTypes0,
            List<IVisitablePointable> items1, List<IVisitablePointable> itemTagTypes1)
            throws HyracksDataException, AsterixException {
        Pair<IVisitablePointable, Boolean> arg=null;
        for(int i=0; i<items0.size(); i++) {
            ATypeTag fieldType0 = visitor.getTypeTag(itemTagTypes0.get(i));
            ATypeTag fieldType1 = visitor.getTypeTag(itemTagTypes1.get(i));
            if(fieldType0 != fieldType1) {
                return false;
            }

            IVisitablePointable item1 = items1.get(i);
            if (!compareListItems(fieldType0, items0.get(i), items1.get(i)))
                return false;
        }

        return true;
    }

    private boolean processUnorderedList(List<IVisitablePointable> items0, List<IVisitablePointable> itemTagTypes0,
            List<IVisitablePointable> items1, List<IVisitablePointable> itemTagTypes1)
            throws HyracksDataException, AsterixException {

        hashMap.clear();
        // Build phase: Add items into hash map, starting with first list.
        for(int i=0; i<items0.size(); i++) {
            IVisitablePointable item = items0.get(i);
            byte[] buf = item.getByteArray();
            int off = item.getStartOffset();
            int len = item.getLength();
            keyEntry.set(buf, off, len);
            IntegerPointable.setInteger(valEntry.buf, 0, i);
            BinaryEntry entry = hashMap.put(keyEntry, valEntry);
        }

        return probeHashMap(items0, itemTagTypes0, items1, itemTagTypes1);
    }


    private boolean probeHashMap(List<IVisitablePointable> items0, List<IVisitablePointable> itemTagTypes0,
            List<IVisitablePointable> items1, List<IVisitablePointable> itemTagTypes1)
            throws HyracksDataException, AsterixException {
        // Probe phase: Probe items from second list
        for(int index1=0; index1<items1.size(); index1++) {
            IVisitablePointable item1 = items1.get(index1);
            byte[] buf = item1.getByteArray();
            int off = item1.getStartOffset();
            int len = item1.getLength();
            keyEntry.set(buf, off, len);
            BinaryEntry entry = hashMap.get(keyEntry);

            // The fieldnames doesn't match
            if (entry == null) {
                return false;
            }

            int index0 = IntegerPointable.getInteger(entry.buf, entry.off);
            ATypeTag fieldType0 = visitor.getTypeTag(itemTagTypes0.get(index0));
            if(fieldType0 != visitor.getTypeTag(itemTagTypes1.get(index1))) {
                return false;
            }

            if (!compareListItems(fieldType0, items0.get(index0), item1))
                return false;
        }
        return true;
    }
}

