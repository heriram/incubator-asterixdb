package edu.uci.ics.asterix.optimizer.rules.am;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.asterix.om.constants.AsterixConstantValue;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.optimizer.rules.am.InvertedIndexAccessMethod.SearchModifierType;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IAlgebricksConstantValue;

public class InvertedIndexJobGenParams extends AccessMethodJobGenParams {
    
    protected SearchModifierType searchModifierType;
    protected IAlgebricksConstantValue similarityThreshold;
    protected ATypeTag searchKeyType;
    protected List<LogicalVariable> keyVarList;
    
    public InvertedIndexJobGenParams() {
    }
    
    public InvertedIndexJobGenParams(String indexName, String indexType, String datasetName, boolean retainInput, boolean requiresBroadcast) {
        super(indexName, indexType, datasetName, retainInput, requiresBroadcast);
    }
    
    public void setSearchModifierType(SearchModifierType searchModifierType) {
        this.searchModifierType = searchModifierType;
    }
        
    public void setSimilarityThreshold(IAlgebricksConstantValue similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
    
    public void setSearchKeyType(ATypeTag searchKeyType) {
        this.searchKeyType = searchKeyType;
    }
    
    public void setKeyVarList(List<LogicalVariable> keyVarList) {
        this.keyVarList = keyVarList;
    }
    
    public void writeToFuncArgs(List<Mutable<ILogicalExpression>> funcArgs) {
        super.writeToFuncArgs(funcArgs);
        // Write search modifier type.
        funcArgs.add(new MutableObject<ILogicalExpression>(AccessMethodUtils.createInt32Constant(searchModifierType.ordinal())));
        // Write similarity threshold.
        funcArgs.add(new MutableObject<ILogicalExpression>(new ConstantExpression(similarityThreshold)));
        // Write search key type.
        funcArgs.add(new MutableObject<ILogicalExpression>(AccessMethodUtils.createInt32Constant(searchKeyType.ordinal())));
        // Write key var list.
        writeVarList(keyVarList, funcArgs);
    }
    
    public void readFromFuncArgs(List<Mutable<ILogicalExpression>> funcArgs) {
        super.readFromFuncArgs(funcArgs);
        // Read search modifier type.
        int searchModifierOrdinal = AccessMethodUtils.getInt32Constant(funcArgs.get(5));
        searchModifierType = SearchModifierType.values()[searchModifierOrdinal];
        // Read similarity threshold. Concrete type depends on search modifier.
        similarityThreshold = ((AsterixConstantValue) ((ConstantExpression) funcArgs.get(6).getValue()).getValue());
        // Read type of search key.
        int typeTagOrdinal = AccessMethodUtils.getInt32Constant(funcArgs.get(7));
        searchKeyType = ATypeTag.values()[typeTagOrdinal];
        // Read key var list.
        keyVarList = new ArrayList<LogicalVariable>();
        readVarList(funcArgs, 8, keyVarList);
    }
    
    public SearchModifierType getSearchModifierType() {
        return searchModifierType;
    }
    
    public IAlgebricksConstantValue getSimilarityThreshold() {
        return similarityThreshold;
    }
    
    public ATypeTag getSearchKeyType() {
        return searchKeyType;
    }
    
    public List<LogicalVariable> getKeyVarList() {
        return keyVarList;
    }
}