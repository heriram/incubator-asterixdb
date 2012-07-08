/*
 * Numeric function Round half to even
 * Author : Xiaoyu Ma@UC Irvine
 * 01/30/2012
 */
package edu.uci.ics.asterix.runtime.evaluators.functions;


import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.*;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.*;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptor;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptorFactory;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.EnumDeserializer;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.common.exceptions.NotImplementedException;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;
import java.io.DataOutput;

public class NumericRoundHalfToEvenDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    public final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS,
            "numeric-round-half-to-even", 1, true);
    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        public IFunctionDescriptor createFunctionDescriptor() {
            return new NumericRoundHalfToEvenDescriptor();
        }
    };    
    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }    
    

    @Override
    public ICopyEvaluatorFactory createEvaluatorFactory(final ICopyEvaluatorFactory[] args) {
        return new ICopyEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public ICopyEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {

                return new ICopyEvaluator() {

                    private DataOutput out = output.getDataOutput();
                    private ArrayBackedValueStorage argOut = new ArrayBackedValueStorage();
                    private ICopyEvaluator eval = args[0].createEvaluator(argOut);
                    private byte serNullTypeTag = ATypeTag.NULL.serialize();
                    private byte serInt8TypeTag = ATypeTag.INT8.serialize();
                    private byte serInt16TypeTag = ATypeTag.INT16.serialize();
                    private byte serInt32TypeTag = ATypeTag.INT32.serialize();
                    private byte serInt64TypeTag = ATypeTag.INT64.serialize();
                    private byte serFloatTypeTag = ATypeTag.FLOAT.serialize();
                    private byte serDoubleTypeTag = ATypeTag.DOUBLE.serialize();
                    
                    private AMutableDouble aDouble = new AMutableDouble(0);
                    private AMutableFloat aFloat = new AMutableFloat(0);
                    private AMutableInt64 aInt64 = new AMutableInt64(0);
                    private AMutableInt32 aInt32 = new AMutableInt32(0);
                    private AMutableInt16 aInt16 = new AMutableInt16((short) 0);
                    private AMutableInt8 aInt8 = new AMutableInt8((byte) 0);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer serde;

                    @SuppressWarnings("unchecked")
                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        argOut.reset();
                        eval.evaluate(tuple);
                        try {
                            if (argOut.getByteArray()[0] == serNullTypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.ANULL);
                                serde.serialize(ANull.NULL, out);
                                return;
                            } else if (argOut.getByteArray()[0] == serInt8TypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.AINT8);
                                byte val = (byte)AInt8SerializerDeserializer.getByte(argOut.getByteArray(), 1);
                                aInt8.setValue(val);
                                serde.serialize(aInt8, out);
                            } else if (argOut.getByteArray()[0] == serInt16TypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.AINT16);
                                short val = (short)AInt16SerializerDeserializer.getShort(argOut.getByteArray(), 1);
                                aInt16.setValue(val);
                                serde.serialize(aInt16, out);
                            } else if (argOut.getByteArray()[0] == serInt32TypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.AINT32);
                                int val = (int)AInt32SerializerDeserializer.getInt(argOut.getByteArray(), 1);
                                aInt32.setValue(val);
                                serde.serialize(aInt32, out);
                            } else if (argOut.getByteArray()[0] == serInt64TypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.AINT64);
                                long val = (long)AInt64SerializerDeserializer.getLong(argOut.getByteArray(), 1);
                                aInt64.setValue(val);                                
                                serde.serialize(aInt64, out);
                            } else if (argOut.getByteArray()[0] == serFloatTypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.AFLOAT);
                                float val = (float)AFloatSerializerDeserializer.getFloat(argOut.getByteArray(), 1);
                                aFloat.setValue((float)Math.rint(val));                                 
                                serde.serialize(aFloat, out);
                            } else if (argOut.getByteArray()[0] == serDoubleTypeTag) {
                                serde = AqlSerializerDeserializerProvider.INSTANCE
                                        .getSerializerDeserializer(BuiltinType.ADOUBLE);
                                double val = (double)ADoubleSerializerDeserializer.getDouble(argOut.getByteArray(), 1);
                                aDouble.setValue(Math.rint(val));                                     
                                serde.serialize(aDouble, out);
                            } else {
                                throw new NotImplementedException("Numeric Round Half to Even is not implemented for "
                                        + EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(argOut.getByteArray()[0]));
                            }
                        } catch (HyracksDataException e) {
                            throw new AlgebricksException(e);
                        }
                    }
                };
            }
        };
    }

}
