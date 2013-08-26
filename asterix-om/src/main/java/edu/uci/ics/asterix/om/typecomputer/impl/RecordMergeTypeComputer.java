package edu.uci.ics.asterix.om.typecomputer.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.om.typecomputer.base.IResultTypeComputer;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.AUnionType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.om.types.TypeHelper;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;

public class RecordMergeTypeComputer implements IResultTypeComputer {
    private static final long serialVersionUID = 1L;

    public static final RecordMergeTypeComputer INSTANCE = new RecordMergeTypeComputer();

    private RecordMergeTypeComputer() {
    }

    private ARecordType extractRecordType(IAType t) {
        if (t.getTypeTag() == ATypeTag.RECORD) {
            return (ARecordType) t;
        }

        if (t.getTypeTag() == ATypeTag.UNION) {
            IAType innerType = ((AUnionType) t).getUnionList().get(1);
            if (innerType.getTypeTag() == ATypeTag.RECORD) {
                return (ARecordType) innerType;
            }
        }

        return null;
    }

    @Override
    public IAType computeType(ILogicalExpression expression, IVariableTypeEnvironment env,
            IMetadataProvider<?, ?> metadataProvider) throws AlgebricksException {
        AbstractFunctionCallExpression f = (AbstractFunctionCallExpression) expression;
        IAType t0 = (IAType) env.getType(f.getArguments().get(0).getValue());
        IAType t1 = (IAType) env.getType(f.getArguments().get(1).getValue());
        boolean nullable = TypeHelper.canBeNull(t0) || TypeHelper.canBeNull(t1);
        ARecordType recType0 = extractRecordType(t0);
        ARecordType recType1 = extractRecordType(t1);

        if (recType0 == null || recType1 == null) {
            throw new AlgebricksException("record-merge expects possibly NULL records as arguments, but got (" + t0
                    + ", " + t1 + ")");
        }

        List<String> resultFieldNames = new ArrayList<>();
        for (String fieldName : recType0.getFieldNames()) {
            resultFieldNames.add(fieldName);
        }
        Collections.sort(resultFieldNames);
        List<IAType> resultFieldTypes = new ArrayList<>();
        for (String fieldName : resultFieldNames) {
            try {
                resultFieldTypes.add(recType0.getFieldType(fieldName));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        List<String> additionalFieldNames = new ArrayList<>();
        List<IAType> additionalFieldTypes = new ArrayList<>();
        for (int i = 0; i < recType1.getFieldNames().length; ++i) {
            String fieldName = recType1.getFieldNames()[i];
            IAType fieldType = recType1.getFieldTypes()[i];
            int pos = Collections.binarySearch(resultFieldNames, fieldName);
            if (pos >= 0) {
                resultFieldNames.set(pos, fieldName);
                resultFieldTypes.set(pos, fieldType);
            } else {
                additionalFieldNames.add(fieldName);
                additionalFieldTypes.add(fieldType);
            }
        }

        resultFieldNames.addAll(additionalFieldNames);
        resultFieldTypes.addAll(additionalFieldTypes);
        String resultTypeName = "merged(" + recType0.getTypeName() + ", " + recType1.getTypeName() + ")";
        boolean isOpen = recType0.isOpen() || recType1.isOpen();
        IAType resultType = null;
        try {
            resultType = new ARecordType(resultTypeName, resultFieldNames.toArray(new String[] {}),
                    resultFieldTypes.toArray(new IAType[] {}), isOpen);
        } catch (AsterixException e) {
            throw new AlgebricksException(e);
        };

        if (nullable) {
            resultType = AUnionType.createNullableType(resultType);
        }
        return resultType;
    }
}
