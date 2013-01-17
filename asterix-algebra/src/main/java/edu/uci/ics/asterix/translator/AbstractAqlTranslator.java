package edu.uci.ics.asterix.translator;

import java.util.Map;

import edu.uci.ics.asterix.aql.base.Statement;
import edu.uci.ics.asterix.aql.expression.DataverseDropStatement;
import edu.uci.ics.asterix.aql.expression.DeleteStatement;
import edu.uci.ics.asterix.aql.expression.DropStatement;
import edu.uci.ics.asterix.aql.expression.InsertStatement;
import edu.uci.ics.asterix.aql.expression.NodeGroupDropStatement;
import edu.uci.ics.asterix.common.exceptions.AsterixException;
import edu.uci.ics.asterix.metadata.bootstrap.MetadataConstants;
import edu.uci.ics.asterix.metadata.entities.AsterixBuiltinTypeMap;
import edu.uci.ics.asterix.metadata.entities.Dataverse;
import edu.uci.ics.asterix.om.types.BuiltinType;

public abstract class AbstractAqlTranslator {

    protected static final Map<String, BuiltinType> builtinTypeMap = AsterixBuiltinTypeMap.getBuiltinTypes();

    public void validateOperation(Dataverse defaultDataverse, Statement stmt) throws AsterixException {
        boolean invalidOperation = false;
        String message = null;
        String dataverse = defaultDataverse != null ? defaultDataverse.getDataverseName() : null;
        switch (stmt.getKind()) {
            case INSERT:
                InsertStatement insertStmt = (InsertStatement) stmt;
                if (insertStmt.getDataverseName() != null) {
                    dataverse = insertStmt.getDataverseName().getValue();
                }
                invalidOperation = MetadataConstants.METADATA_DATAVERSE_NAME.equals(dataverse);
                if (invalidOperation) {
                    message = "Insert operation is not permitted in dataverse "
                            + MetadataConstants.METADATA_DATAVERSE_NAME;
                }
                break;

            case DELETE:
                DeleteStatement deleteStmt = (DeleteStatement) stmt;
                if (deleteStmt.getDataverseName() != null) {
                    dataverse = deleteStmt.getDataverseName().getValue();
                }
                invalidOperation = MetadataConstants.METADATA_DATAVERSE_NAME.equals(dataverse);
                if (invalidOperation) {
                    message = "Delete operation is not permitted in dataverse "
                            + MetadataConstants.METADATA_DATAVERSE_NAME;
                }
                break;

            case NODEGROUP_DROP:
                String nodegroupName = ((NodeGroupDropStatement) stmt).getNodeGroupName().getValue();
                invalidOperation = MetadataConstants.METADATA_DEFAULT_NODEGROUP_NAME.equals(nodegroupName);
                if (invalidOperation) {
                    message = "Cannot drop nodegroup:" + nodegroupName;
                }
                break;

            case DATAVERSE_DROP:
                DataverseDropStatement dvDropStmt = (DataverseDropStatement) stmt;
                invalidOperation = MetadataConstants.METADATA_DATAVERSE_NAME.equals(dvDropStmt.getDataverseName()
                        .getValue());
                if (invalidOperation) {
                    message = "Cannot drop dataverse:" + dvDropStmt.getDataverseName().getValue();
                }
                break;

            case DATASET_DROP:
                DropStatement dropStmt = (DropStatement) stmt;
                if (dropStmt.getDataverseName() != null) {
                    dataverse = dropStmt.getDataverseName().getValue();
                }
                invalidOperation = MetadataConstants.METADATA_DATAVERSE_NAME.equals(dataverse);
                if (invalidOperation) {
                    message = "Cannot drop a dataset belonging to the dataverse:"
                            + MetadataConstants.METADATA_DATAVERSE_NAME;
                }
                break;

        }

        if (invalidOperation) {
            throw new AsterixException("Invalid operation - " + message);
        }
    }
}
