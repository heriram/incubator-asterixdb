package edu.uci.ics.asterix.external.library.java;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ABooleanSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ACircleSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ADateSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ADateTimeSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ADoubleSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ADurationSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AFloatSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt16SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt32SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt64SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt8SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AIntervalSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ALineSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.APoint3DSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.APointSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.APolygonSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ARectangleSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AStringSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ATimeSerializerDeserializer;
import edu.uci.ics.asterix.external.library.java.JObjects.JBoolean;
import edu.uci.ics.asterix.external.library.java.JObjects.JByte;
import edu.uci.ics.asterix.external.library.java.JObjects.JCircle;
import edu.uci.ics.asterix.external.library.java.JObjects.JDate;
import edu.uci.ics.asterix.external.library.java.JObjects.JDateTime;
import edu.uci.ics.asterix.external.library.java.JObjects.JDouble;
import edu.uci.ics.asterix.external.library.java.JObjects.JDuration;
import edu.uci.ics.asterix.external.library.java.JObjects.JFloat;
import edu.uci.ics.asterix.external.library.java.JObjects.JInt;
import edu.uci.ics.asterix.external.library.java.JObjects.JInterval;
import edu.uci.ics.asterix.external.library.java.JObjects.JLine;
import edu.uci.ics.asterix.external.library.java.JObjects.JList;
import edu.uci.ics.asterix.external.library.java.JObjects.JLong;
import edu.uci.ics.asterix.external.library.java.JObjects.JOrderedList;
import edu.uci.ics.asterix.external.library.java.JObjects.JPoint;
import edu.uci.ics.asterix.external.library.java.JObjects.JPoint3D;
import edu.uci.ics.asterix.external.library.java.JObjects.JPolygon;
import edu.uci.ics.asterix.external.library.java.JObjects.JRecord;
import edu.uci.ics.asterix.external.library.java.JObjects.JRectangle;
import edu.uci.ics.asterix.external.library.java.JObjects.JString;
import edu.uci.ics.asterix.external.library.java.JObjects.JTime;
import edu.uci.ics.asterix.external.library.java.JObjects.JUnorderedList;
import edu.uci.ics.asterix.om.base.ACircle;
import edu.uci.ics.asterix.om.base.ADuration;
import edu.uci.ics.asterix.om.base.ALine;
import edu.uci.ics.asterix.om.base.APoint;
import edu.uci.ics.asterix.om.base.APoint3D;
import edu.uci.ics.asterix.om.base.APolygon;
import edu.uci.ics.asterix.om.base.ARectangle;
import edu.uci.ics.asterix.om.pointables.AFlatValuePointable;
import edu.uci.ics.asterix.om.pointables.AListPointable;
import edu.uci.ics.asterix.om.pointables.ARecordPointable;
import edu.uci.ics.asterix.om.pointables.base.IVisitablePointable;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.AbstractCollectionType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.EnumDeserializer;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.om.util.container.IObjectPool;
import edu.uci.ics.hyracks.algebricks.common.utils.Triple;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;

public class JObjectAccessors {

    public static IJObjectAccessor createFlatJObjectAccessor(ATypeTag aTypeTag) {
        IJObjectAccessor accessor = null;
        switch (aTypeTag) {
            case BOOLEAN:
                accessor = new JBooleanAccessor();
                break;
            case INT8:
                accessor = new JInt8Accessor();
                break;
            case INT16:
                accessor = new JInt16Accessor();
                break;
            case INT32:
                accessor = new JInt32Accessor();
                break;
            case INT64:
                accessor = new JInt64Accessor();
                break;
            case FLOAT:
                accessor = new JFloatAccessor();
                break;
            case DOUBLE:
                accessor = new JDoubleAccessor();
                break;
            case STRING:
                accessor = new JStringAccessor();
                break;
            case POINT:
                accessor = new JPointAccessor();
                break;
            case POINT3D:
                accessor = new JPoint3DAccessor();
                break;
            case LINE:
                accessor = new JLineAccessor();
                break;
            case DATE:
                accessor = new JDateAccessor();
                break;
            case DATETIME:
                accessor = new JDateTimeAccessor();
                break;
            case DURATION:
                accessor = new JDurationAccessor();
                break;
        }
        return accessor;
    }

    public static class JInt8Accessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            byte o = AInt8SerializerDeserializer.getByte(b, s + 1);
            IJObject jObject = objectPool.allocate(BuiltinType.AINT8);
            ((JByte) jObject).setValue(o);
            return null;
        }

    }

    public static class JInt16Accessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            short i = AInt16SerializerDeserializer.getShort(b, s + 1);
            IJObject jObject = objectPool.allocate(BuiltinType.AINT16);
            ((JInt) jObject).setValue(i);
            return null;
        }
    }

    public static class JInt32Accessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int i = AInt32SerializerDeserializer.getInt(b, s + 1);
            IJObject jObject = objectPool.allocate(BuiltinType.AINT32);
            ((JInt) jObject).setValue(i);
            return jObject;
        }
    }

    public static class JInt64Accessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            long v = AInt64SerializerDeserializer.getLong(b, s + 1);
            IJObject jObject = objectPool.allocate(BuiltinType.AINT64);
            ((JLong) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JFloatAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            float v = AFloatSerializerDeserializer.getFloat(b, s + 1);
            IJObject jObject = objectPool.allocate(BuiltinType.AFLOAT);
            ((JFloat) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JDoubleAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            double v = ADoubleSerializerDeserializer.getDouble(b, s + 1);
            IJObject jObject = objectPool.allocate(BuiltinType.ADOUBLE);
            ((JDouble) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JStringAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            String v = AStringSerializerDeserializer.INSTANCE.deserialize(
                    new DataInputStream(new ByteArrayInputStream(b, s + 1, l - 1))).getStringValue();
            IJObject jObject = objectPool.allocate(BuiltinType.ASTRING);
            ((JString) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JBooleanAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            Boolean v = ABooleanSerializerDeserializer.getBoolean(b, s);
            IJObject jObject = objectPool.allocate(BuiltinType.ABOOLEAN);
            ((JBoolean) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JDateAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int v = ADateSerializerDeserializer.getChronon(b, s);
            IJObject jObject = objectPool.allocate(BuiltinType.ADATE);
            ((JDate) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JDateTimeAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            long v = ADateTimeSerializerDeserializer.getChronon(b, s);
            IJObject jObject = objectPool.allocate(BuiltinType.ADATETIME);
            ((JDateTime) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JDurationAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            ADuration duration = ADurationSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(
                    new ByteArrayInputStream(b, s, l)));
            IJObject jObject = objectPool.allocate(BuiltinType.ADURATION);
            ((JDuration) jObject).setValue(duration.getMonths(), duration.getMilliseconds());
            return jObject;
        }
    }

    public static class JTimeAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int v = ATimeSerializerDeserializer.getChronon(b, s);
            IJObject jObject = objectPool.allocate(BuiltinType.ATIME);
            ((JTime) jObject).setValue(v);
            return jObject;
        }
    }

    public static class JIntervalAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            long intervalStart = AIntervalSerializerDeserializer.getIntervalStart(b, s);
            long intervalEnd = AIntervalSerializerDeserializer.getIntervalEnd(b, s);
            byte intervalType = AIntervalSerializerDeserializer.getIntervalTimeType(b, s);
            IJObject jObject = objectPool.allocate(BuiltinType.AINTERVAL);
            ((JInterval) jObject).setValue(intervalStart, intervalEnd, intervalType);
            return jObject;
        }
    }

    // Spatial Types

    public static class JCircleAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            ACircle v = ACircleSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(
                    new ByteArrayInputStream(b, s, l)));
            JPoint jpoint = (JPoint) objectPool.allocate(BuiltinType.APOINT);
            jpoint.setValue(v.getP().getX(), v.getP().getY());
            IJObject jObject = objectPool.allocate(BuiltinType.ACIRCLE);
            ((JCircle) jObject).setValue(jpoint, v.getRadius());
            return jObject;
        }
    }

    public static class JPointAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            APoint v = APointSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(new ByteArrayInputStream(
                    b, s, l)));
            JPoint jObject = (JPoint) objectPool.allocate(BuiltinType.APOINT);
            jObject.setValue(v.getX(), v.getY());
            return jObject;
        }
    }

    public static class JPoint3DAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            APoint3D v = APoint3DSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(
                    new ByteArrayInputStream(b, s, l)));
            JPoint3D jObject = (JPoint3D) objectPool.allocate(BuiltinType.APOINT3D);
            jObject.setValue(v.getX(), v.getY(), v.getZ());
            return jObject;
        }
    }

    public static class JLineAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            ALine v = ALineSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(new ByteArrayInputStream(b,
                    s, l)));
            JLine jObject = (JLine) objectPool.allocate(BuiltinType.ALINE);
            jObject.setValue(v.getP1(), v.getP2());
            return jObject;
        }
    }

    public static class JPolygonAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            APolygon v = APolygonSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(
                    new ByteArrayInputStream(b, s, l)));
            JPolygon jObject = (JPolygon) objectPool.allocate(BuiltinType.APOLYGON);
            jObject.setValue(v.getPoints());
            return jObject;
        }
    }

    public static class JRectangleAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            byte[] b = pointable.getByteArray();
            int s = pointable.getStartOffset();
            int l = pointable.getLength();
            ARectangle v = ARectangleSerializerDeserializer.INSTANCE.deserialize(new DataInputStream(
                    new ByteArrayInputStream(b, s, l)));
            JRectangle jObject = (JRectangle) objectPool.allocate(BuiltinType.ARECTANGLE);
            jObject.setValue(v.getP1(), v.getP2());
            return jObject;
        }
    }

    public static class JRecordAccessor implements IJRecordAccessor {

        @Override
        public JRecord access(ARecordPointable pointable, IObjectPool<IJObject, IAType> objectPool,
                ARecordType recordType, JObjectPointableVisitor pointableVisitor) throws HyracksDataException {
            ARecordPointable recordPointable = (ARecordPointable) pointable;
            List<IVisitablePointable> fieldPointables = recordPointable.getFieldValues();
            List<IVisitablePointable> fieldTypeTags = recordPointable.getFieldTypeTags();
            List<IVisitablePointable> fieldNames = recordPointable.getFieldNames();
            JRecord jRecord = null;
            List<IJObject> jObjects = new ArrayList<IJObject>();
            Map<String, IJObject> openFields = new LinkedHashMap<String, IJObject>();
            int index = 0;
            boolean closedPart = true;
            try {
                IJObject fieldObject = null;
                for (IVisitablePointable fieldPointable : fieldPointables) {
                    closedPart = index < recordType.getFieldTypes().length;
                    IVisitablePointable tt = fieldTypeTags.get(index);
                    IAType fieldType = closedPart ? recordType.getFieldTypes()[index] : null;
                    ATypeTag typeTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(tt.getByteArray()[tt
                            .getStartOffset()]);
                    IVisitablePointable fieldName = fieldNames.get(index);
                    switch (typeTag) {
                        case RECORD:
                            fieldObject = pointableVisitor.visit((ARecordPointable) fieldPointable, new Triple(
                                    objectPool, fieldType, typeTag));
                            break;
                        case ORDEREDLIST:
                        case UNORDEREDLIST:
                            if (fieldPointable instanceof AFlatValuePointable) {
                                // value is null
                                fieldObject = null;
                            } else {
                                fieldObject = pointableVisitor.visit((AListPointable) fieldPointable, new Triple(
                                        objectPool, fieldType, typeTag));
                            }
                            break;
                        case ANY:
                            break;
                        default:
                            fieldObject = pointableVisitor.visit((AFlatValuePointable) fieldPointable, new Triple(
                                    objectPool, fieldType, typeTag));
                    }
                    if (closedPart) {
                        jObjects.add(fieldObject);
                    } else {
                        byte[] b = fieldName.getByteArray();
                        int s = fieldName.getStartOffset();
                        int l = fieldName.getLength();
                        String v = AStringSerializerDeserializer.INSTANCE.deserialize(
                                new DataInputStream(new ByteArrayInputStream(b, s + 1, l - 1))).getStringValue();
                        openFields.put(v, fieldObject);
                    }
                    index++;
                    fieldObject = null;
                }

                jRecord = new JRecord(recordType, jObjects.toArray(new IJObject[] {}), openFields);
            } catch (Exception e) {
                e.printStackTrace();
                throw new HyracksDataException(e);
            }
            return jRecord;
        }
    }

    public static class JListAccessor implements IJListAccessor {

        @Override
        public IJObject access(AListPointable pointable, IObjectPool<IJObject, IAType> objectPool, IAType listType,
                JObjectPointableVisitor pointableVisitor) throws HyracksDataException {
            List<IVisitablePointable> items = pointable.getItems();
            List<IVisitablePointable> itemTags = pointable.getItemTags();
            JList list = pointable.ordered() ? new JOrderedList(listType) : new JUnorderedList(listType);
            IJObject listItem = null;
            int index = 0;
            try {
                for (IVisitablePointable itemPointable : items) {
                    IVisitablePointable itemTagPointable = itemTags.get(index);
                    ATypeTag itemTypeTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(itemTagPointable
                            .getByteArray()[itemTagPointable.getStartOffset()]);
                    switch (itemTypeTag) {
                        case RECORD:
                            listItem = pointableVisitor.visit((ARecordPointable) itemPointable, new Triple(objectPool,
                                    listType.getType(), listType.getTypeTag()));
                            break;
                        case UNORDEREDLIST:
                        case ORDEREDLIST:
                            listItem = pointableVisitor.visit((AListPointable) itemPointable, new Triple(objectPool,
                                    listType.getType(), listType.getTypeTag()));
                            break;
                        case ANY:
                            throw new IllegalArgumentException("Cannot parse list item of type "
                                    + listType.getTypeTag());
                        default:
                            listItem = pointableVisitor.visit((AFlatValuePointable) itemPointable, new Triple(
                                    objectPool, ((AbstractCollectionType) listType).getItemType(),
                                    ((AbstractCollectionType) listType).getTypeTag()));

                    }
                    ATypeTag typeTag = EnumDeserializer.ATYPETAGDESERIALIZER
                            .deserialize(itemPointable.getByteArray()[itemPointable.getStartOffset()]);

                    list.add(listItem);
                }
            } catch (AsterixException exception) {
                throw new HyracksDataException(exception);
            }
            return list;
        }
    }

    public static class JUnorderedListAccessor implements IJObjectAccessor {

        @Override
        public IJObject access(IVisitablePointable pointable, IObjectPool<IJObject, IAType> objectPool)
                throws HyracksDataException {
            return null;
        }

    }
}