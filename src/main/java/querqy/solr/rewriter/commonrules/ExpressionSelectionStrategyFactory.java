package querqy.solr.rewriter.commonrules;

import querqy.rewriter.commonrules.select.SelectionStrategyFactory;
import querqy.rewriter.commonrules.select.SelectionStrategy;
import querqy.rewriter.commonrules.select.ExpressionCriteriaSelectionStrategyFactory;
import querqy.solr.FactoryAdapter;

import java.util.Map;

public class ExpressionSelectionStrategyFactory implements FactoryAdapter<SelectionStrategyFactory> {

    @Override
    public SelectionStrategyFactory createFactory(final String strategyId, final Map<String, Object> args) {
        // TODO: pass strategyId
        return new ExpressionCriteriaSelectionStrategyFactory();
    }

    @Override
    public Class<?> getCreatedClass() {
        return SelectionStrategy.class;
    }
}
