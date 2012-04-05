package edu.uci.ics.asterix.formats.nontagged;

import java.io.Serializable;

import edu.uci.ics.asterix.dataflow.data.nontagged.comparators.AObjectAscBinaryComparatorFactory;
import edu.uci.ics.asterix.dataflow.data.nontagged.comparators.AObjectDescBinaryComparatorFactory;
import edu.uci.ics.asterix.dataflow.data.nontagged.comparators.BooleanBinaryComparatorFactory;
import edu.uci.ics.asterix.dataflow.data.nontagged.comparators.LongBinaryComparatorFactory;
import edu.uci.ics.asterix.dataflow.data.nontagged.comparators.RectangleBinaryComparatorFactory;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.hyracks.algebricks.core.algebra.data.IBinaryComparatorFactoryProvider;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.NotImplementedException;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import edu.uci.ics.hyracks.data.std.primitive.DoublePointable;
import edu.uci.ics.hyracks.data.std.primitive.FloatPointable;
import edu.uci.ics.hyracks.data.std.primitive.IntegerPointable;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;

public class AqlBinaryComparatorFactoryProvider implements IBinaryComparatorFactoryProvider, Serializable {

    private static final long serialVersionUID = 1L;
    public static final AqlBinaryComparatorFactoryProvider INSTANCE = new AqlBinaryComparatorFactoryProvider();
    public static final PointableBinaryComparatorFactory INTEGER_POINTABLE_INSTANCE = new PointableBinaryComparatorFactory(IntegerPointable.FACTORY);
    public static final PointableBinaryComparatorFactory FLOAT_POINTABLE_INSTANCE = new PointableBinaryComparatorFactory(FloatPointable.FACTORY);
    public static final PointableBinaryComparatorFactory DOUBLE_POINTABLE_INSTANCE = new PointableBinaryComparatorFactory(DoublePointable.FACTORY);
    public static final PointableBinaryComparatorFactory UTF8STRING_POINTABLE_INSTANCE = new PointableBinaryComparatorFactory(UTF8StringPointable.FACTORY);
    // Equivalent to UTF8STRING_POINTABLE_INSTANCE but all characters are considered lower case to implement case-insensitive comparisons.    
    public static final PointableBinaryComparatorFactory UTF8STRING_LOWERCASE_POINTABLE_INSTANCE = new PointableBinaryComparatorFactory(UTF8StringLowercasePointable.FACTORY);
    
    private AqlBinaryComparatorFactoryProvider() {
    }

    // This method add the option of ignoring the case in string comparisons.
    // TODO: We should incorporate this option more nicely, but I'd have to change algebricks.
    public IBinaryComparatorFactory getBinaryComparatorFactory(Object type, OrderKind orderKind, boolean ignoreCase) {
        if (type == null) {
            return anyBinaryComparatorFactory(orderKind);
        }
        IAType aqlType = (IAType) type;
        if (aqlType.getTypeTag() == ATypeTag.STRING && ignoreCase) {
            return addOffset(UTF8STRING_LOWERCASE_POINTABLE_INSTANCE, orderKind);
        }
        return getBinaryComparatorFactory(type, orderKind);
    }
    
    @Override
    public IBinaryComparatorFactory getBinaryComparatorFactory(Object type, OrderKind orderKind) {
        if (type == null) {
            return anyBinaryComparatorFactory(orderKind);
        }
        IAType aqlType = (IAType) type;
        switch (aqlType.getTypeTag()) {
            case ANY:
            case UNION: { // we could do smth better for nullable fields
                return anyBinaryComparatorFactory(orderKind);
            }
            case NULL: {
                return new IBinaryComparatorFactory() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public IBinaryComparator createBinaryComparator() {
                        return new IBinaryComparator() {

                            @Override
                            public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
                                return 0;
                            }
                        };
                    }
                };
            }
            case BOOLEAN: {
                return addOffset(BooleanBinaryComparatorFactory.INSTANCE, orderKind);
            }
            case INT32: {
                return addOffset(INTEGER_POINTABLE_INSTANCE, orderKind);
            }
            case INT64: {
                return addOffset(LongBinaryComparatorFactory.INSTANCE, orderKind);
            }
            case FLOAT: {
                return addOffset(FLOAT_POINTABLE_INSTANCE, orderKind);
            }
            case DOUBLE: {
                return addOffset(DOUBLE_POINTABLE_INSTANCE, orderKind);
            }
            case STRING: {
                return addOffset(UTF8STRING_POINTABLE_INSTANCE, orderKind);
            }
            case RECTANGLE: {
                return addOffset(RectangleBinaryComparatorFactory.INSTANCE, orderKind);
            }
            default: {
                throw new NotImplementedException("No binary comparator factory implemented for type "
                        + aqlType.getTypeTag() + " .");
            }
        }
    }

    private IBinaryComparatorFactory addOffset(final IBinaryComparatorFactory inst, final OrderKind kind) {
        return new IBinaryComparatorFactory() {

            private static final long serialVersionUID = 1L;

            @Override
            public IBinaryComparator createBinaryComparator() {
                final IBinaryComparator bc = inst.createBinaryComparator();
                switch (kind) {
                    case ASC: {
                        return new IBinaryComparator() {

                            @Override
                            public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
                                return bc.compare(b1, s1 + 1, l1 - 1, b2, s2 + 1, l2 - 1);
                            }
                        };
                    }
                    case DESC: {
                        return new IBinaryComparator() {

                            @Override
                            public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
                                return -bc.compare(b1, s1 + 1, l1 - 1, b2, s2 + 1, l2 - 1);
                            }
                        };
                    }
                    default: {
                        throw new NotImplementedException();
                    }
                }
            }
        };
    }

    private IBinaryComparatorFactory anyBinaryComparatorFactory(OrderKind kind) {
        switch (kind) {
            case ASC: {
                return AObjectAscBinaryComparatorFactory.INSTANCE;
            }
            case DESC: {
                return AObjectDescBinaryComparatorFactory.INSTANCE;
            }
            default: {
                throw new NotImplementedException();
            }
        }

    }
}
