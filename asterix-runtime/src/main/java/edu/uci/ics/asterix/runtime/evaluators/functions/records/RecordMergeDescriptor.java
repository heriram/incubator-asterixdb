package edu.uci.ics.asterix.runtime.evaluators.functions.records;

import edu.uci.ics.asterix.builders.RecordBuilder;
import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AStringSerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ANull;
import edu.uci.ics.asterix.om.functions.AsterixBuiltinFunctions;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptor;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptorFactory;
import edu.uci.ics.asterix.om.pointables.ARecordPointable;
import edu.uci.ics.asterix.om.pointables.PointableAllocator;
import edu.uci.ics.asterix.om.pointables.base.IVisitablePointable;
import edu.uci.ics.asterix.om.typecomputer.impl.RecordMergeTypeComputer;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.asterix.runtime.evaluators.comparisons.DeepEqualAssessor;
import edu.uci.ics.asterix.runtime.evaluators.functions.PointableUtils;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.data.std.api.IDataOutputProvider;
import edu.uci.ics.hyracks.data.std.util.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.data.std.util.ByteArrayAccessibleOutputStream;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * record merge evaluator is used to combine two records with no matching fieldnames
 * If both records have the same fieldname for a non-record field anywhere in the schema, the merge will fail
 * This function is performed on a recursive level, meaning that nested records can be combined
 * for instance if both records have a nested field called "metadata"
 * where metadata from A is {"comments":"this rocks"} and metadata from B is {"index":7, "priority":5}
 * Records A and B can be combined yielding a nested record called "metadata"
 * That will have all three fields
 */
public class RecordMergeDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;

    private static final byte SER_NULL_TYPE_TAG = ATypeTag.NULL.serialize();

    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        public IFunctionDescriptor createFunctionDescriptor() {
            return new RecordMergeDescriptor();
        }
    };

    private ARecordType outRecType;
    private ARecordType inRecType0;
    private ARecordType inRecType1;

    public void reset(IAType outType, IAType inType0, IAType inType1) {
        outRecType = RecordMergeTypeComputer.extractRecordType(outType);
        inRecType0 = RecordMergeTypeComputer.extractRecordType(inType0);
        inRecType1 = RecordMergeTypeComputer.extractRecordType(inType1);
    }

    @Override
    public ICopyEvaluatorFactory createEvaluatorFactory(final ICopyEvaluatorFactory[] args) throws AlgebricksException {
        return new ICopyEvaluatorFactory() {

            private static final long serialVersionUID = 1L;

            @SuppressWarnings("unchecked")
            private final ISerializerDeserializer<ANull> nullSerDe = AqlSerializerDeserializerProvider.INSTANCE
                    .getSerializerDeserializer(BuiltinType.ANULL);

            @Override
            public ICopyEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {
                final ARecordType recType;
                try {
                    recType = new ARecordType(outRecType.getTypeName(), outRecType.getFieldNames(),
                            outRecType.getFieldTypes(), outRecType.isOpen());
                } catch (AsterixException | HyracksDataException e) {
                    throw new IllegalStateException();
                }

                final PointableAllocator pa = new PointableAllocator();
                final IVisitablePointable vp0 = pa.allocateRecordValue(inRecType0);
                final IVisitablePointable vp1 = pa.allocateRecordValue(inRecType1);

                final ArrayBackedValueStorage abvs0 = new ArrayBackedValueStorage();
                final ArrayBackedValueStorage abvs1 = new ArrayBackedValueStorage();

                final ICopyEvaluator eval0 = args[0].createEvaluator(abvs0);
                final ICopyEvaluator eval1 = args[1].createEvaluator(abvs1);

                final List<RecordBuilder> rbStack = new ArrayList<>();

                final ArrayBackedValueStorage tabvs = new ArrayBackedValueStorage();

                final ByteArrayAccessibleOutputStream nameOutputStream = new ByteArrayAccessibleOutputStream();
                final ByteArrayInputStream namebais = new ByteArrayInputStream(nameOutputStream.getByteArray());
                final DataInputStream namedis = new DataInputStream(namebais);

                return new ICopyEvaluator() {

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        abvs0.reset();
                        abvs1.reset();

                        eval0.evaluate(tuple);
                        eval1.evaluate(tuple);

                        if (abvs0.getByteArray()[0] == SER_NULL_TYPE_TAG
                                || abvs1.getByteArray()[0] == SER_NULL_TYPE_TAG) {
                            try {
                                nullSerDe.serialize(ANull.NULL, output.getDataOutput());
                            } catch (HyracksDataException e) {
                                throw new AlgebricksException(e);
                            }
                            return;
                        }

                        vp0.set(abvs0);
                        vp1.set(abvs1);

                        ARecordPointable rp0 = (ARecordPointable) vp0;
                        ARecordPointable rp1 = (ARecordPointable) vp1;

                        try {
                            mergeFields(recType, rp0, rp1, true, 0);

                            rbStack.get(0).write(output.getDataOutput(), true);
                        } catch (IOException | AsterixException e) {
                            throw new AlgebricksException(e);
                        }
                    }

                    private void mergeFields(ARecordType combinedType, ARecordPointable leftRecord,
                            ARecordPointable rightRecord, boolean openFromParent, int nestedLevel) throws IOException,
                            AsterixException, AlgebricksException {
                        if (rbStack.size() < (nestedLevel + 1)) {
                            rbStack.add(new RecordBuilder());
                        }

                        rbStack.get(nestedLevel).reset(combinedType);
                        rbStack.get(nestedLevel).init();
                        //Add all fields from left record

                        for (int i = 0; i < leftRecord.getFieldNames().size(); i++) {
                            IVisitablePointable leftName = leftRecord.getFieldNames().get(i);
                            IVisitablePointable leftValue = leftRecord.getFieldValues().get(i);
                            IVisitablePointable leftType = leftRecord.getFieldTypeTags().get(i);
                            boolean foundMatch = false;
                            for (int j = 0; j < rightRecord.getFieldNames().size(); j++) {
                                IVisitablePointable rightName = rightRecord.getFieldNames().get(j);
                                IVisitablePointable rightValue = rightRecord.getFieldValues().get(j);
                                IVisitablePointable rightType = rightRecord.getFieldTypeTags().get(j);
                                // Check if same fieldname
                                if (PointableUtils.isEqual(leftName, rightName)
                                        && !DeepEqualAssessor.INSTANCE.isEqual(leftValue, rightValue)) {
                                    //Field was found on the right and are subrecords, merge them
                                    if (PointableUtils.isType(ATypeTag.RECORD, rightType) && PointableUtils
                                            .isType(ATypeTag.RECORD, leftType)) {
                                        //We are merging two sub records
                                        addFieldToSubRecord(combinedType, leftName, leftValue, rightValue,
                                                openFromParent, nestedLevel);
                                        foundMatch = true;
                                    } else {
                                        throw new AlgebricksException("Duplicate field found");
                                    }
                                }


                            }
                            if (!foundMatch) {
                                addFieldToSubRecord(combinedType, leftName, leftValue, null, openFromParent,
                                        nestedLevel);
                            }

                        }
                        //Repeat for right side (ignoring duplicates this time)
                        for (int j = 0; j < rightRecord.getFieldNames().size(); j++) {
                            IVisitablePointable rightName = rightRecord.getFieldNames().get(j);
                            IVisitablePointable rightValue = rightRecord.getFieldValues().get(j);
                            boolean foundMatch = false;
                            for (int i = 0; i < leftRecord.getFieldNames().size(); i++) {
                                IVisitablePointable leftName = leftRecord.getFieldNames().get(i);
                                if (rightName.equals(leftName)) {
                                    foundMatch = true;
                                }
                            }
                            if (!foundMatch) {
                                addFieldToSubRecord(combinedType, rightName, rightValue, null, openFromParent,
                                        nestedLevel);
                            }

                        }

                    }

                    //Takes in a record type, field name, and the field values (which are record) from two records
                    //Merges them into one record of combinedType
                    //And adds that record as a field to the Record in subrb
                    //the second value can be null, indicated that you just add the value of left as a field to subrb
                    private void addFieldToSubRecord(ARecordType combinedType, IVisitablePointable fieldNamePointable,
                            IVisitablePointable leftValue, IVisitablePointable rightValue, boolean openFromParent,
                            int nestedLevel) throws IOException, AsterixException, AlgebricksException {

                        nameOutputStream.reset();
                        nameOutputStream.write(fieldNamePointable.getByteArray(),
                                fieldNamePointable.getStartOffset() + 1, fieldNamePointable.getLength());
                        namedis.reset();
                        String fieldName = AStringSerializerDeserializer.INSTANCE.deserialize(namedis).getStringValue();

                        //Add the merged field
                        if (combinedType != null && combinedType.isClosedField(fieldName)) {
                            int pos = combinedType.findFieldPosition(fieldName);
                            if (rightValue == null) {
                                rbStack.get(nestedLevel).addField(pos, leftValue);
                            } else {
                                mergeFields((ARecordType) combinedType.getFieldType(fieldName),
                                        (ARecordPointable) leftValue, (ARecordPointable) rightValue, false,
                                        nestedLevel + 1);

                                tabvs.reset();
                                rbStack.get(nestedLevel + 1).write(tabvs.getDataOutput(), true);
                                rbStack.get(nestedLevel).addField(pos, tabvs);
                            }
                        } else {
                            if (rightValue == null) {
                                rbStack.get(nestedLevel).addField(fieldNamePointable, leftValue);
                            } else {
                                ARecordType ct = null;
                                if (combinedType != null) {
                                    ct = (ARecordType) combinedType.getFieldType(fieldName);
                                }
                                mergeFields(ct, (ARecordPointable) leftValue, (ARecordPointable) rightValue, false,
                                        nestedLevel + 1);
                                tabvs.reset();
                                rbStack.get(nestedLevel + 1).write(tabvs.getDataOutput(), true);
                                rbStack.get(nestedLevel).addField(fieldNamePointable, tabvs);
                            }
                        }
                    }

                };
            }
        };
    }

    @Override
    public FunctionIdentifier getIdentifier() {
        return AsterixBuiltinFunctions.RECORD_MERGE;
    }
}
