/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.lang.sqlpp.rewrites.visitor;

import org.apache.asterix.common.exceptions.CompilationException;
import org.apache.asterix.common.exceptions.ErrorCode;
import org.apache.asterix.lang.common.base.AbstractClause;
import org.apache.asterix.lang.common.base.Expression;
import org.apache.asterix.lang.common.base.ILangExpression;
import org.apache.asterix.lang.common.clause.GroupbyClause;
import org.apache.asterix.lang.common.clause.LetClause;
import org.apache.asterix.lang.common.clause.LimitClause;
import org.apache.asterix.lang.common.clause.OrderbyClause;
import org.apache.asterix.lang.common.expression.GbyVariableExpressionPair;
import org.apache.asterix.lang.common.expression.VariableExpr;
import org.apache.asterix.lang.common.rewrites.LangRewritingContext;
import org.apache.asterix.lang.common.struct.Identifier;
import org.apache.asterix.lang.sqlpp.clause.FromClause;
import org.apache.asterix.lang.sqlpp.clause.SelectBlock;
import org.apache.asterix.lang.sqlpp.clause.SelectClause;
import org.apache.asterix.lang.sqlpp.expression.SelectExpression;
import org.apache.asterix.lang.sqlpp.util.SqlppAstPrintUtil;
import org.apache.asterix.lang.sqlpp.util.SqlppRewriteUtil;
import org.apache.asterix.lang.sqlpp.util.SqlppVariableUtil;
import org.apache.asterix.lang.sqlpp.visitor.base.AbstractSqlppExpressionScopingVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An AST pre-processor to rewrite group-by sugar queries, which does the following transformations:
 * 1. Rewrite the argument expression of an aggregation function into a subquery
 * 2. Turn a SQL-92 aggregate function into a SQL++ core aggregate function when performing 1.
 * <p>
 * <p>
 * For example, this visitor turns the following query:
 * <pre>
 * FROM Employee e
 * JOIN Incentive i ON e.job_category = i.job_category
 * JOIN SuperStars s ON e.id = s.id
 * GROUP BY e.department_id AS deptId
 * GROUP AS eis(e AS e, i AS i, s AS s)
 * SELECT deptId as deptId, SUM(e.salary + i.bonus) AS star_cost;
 * </pre>
 * into the following core-version query:
 * <pre>
 * FROM Employee e
 * JOIN Incentive i ON e.job_category = i.job_category
 * JOIN SuperStars s ON e.id = s.id
 * GROUP BY e.department_id AS deptId
 * GROUP AS eis(e AS e, i AS i, s AS s)
 * SELECT ELEMENT {
 *          'deptId': deptId,
 *          'star_cost': array_sum( (FROM eis AS p SELECT ELEMENT p.e.salary + p.i.bonus) )
 * };
 * </pre>
 * where <code>SUM(e.salary + i.bonus)</code>
 * is turned into <code>array_sum( (FROM eis AS p SELECT ELEMENT p.e.salary + p.i.bonus) )</code>
 */
public class SqlppGroupByAggregationSugarVisitor extends AbstractSqlppExpressionScopingVisitor {

    public SqlppGroupByAggregationSugarVisitor(LangRewritingContext context) {
        super(context);
    }

    @Override
    public Expression visit(SelectBlock selectBlock, ILangExpression arg) throws CompilationException {

        Set<VariableExpr> outerScopeVars = scopeChecker.getCurrentScope().getLiveVariables();

        // Traverses the select block in the order of "from", "let/where"s, "group by", "let/having"s and "select".
        FromClause fromClause = selectBlock.getFromClause();
        if (selectBlock.hasFromClause()) {
            fromClause.accept(this, arg);
        }
        if (selectBlock.hasLetWhereClauses()) {
            for (AbstractClause letWhereClause : selectBlock.getLetWhereList()) {
                letWhereClause.accept(this, arg);
            }
        }
        if (selectBlock.hasGroupbyClause()) {
            Set<VariableExpr> visibleVarsPreGroupByScope = scopeChecker.getCurrentScope().getLiveVariables();

            GroupbyClause groupbyClause = selectBlock.getGroupbyClause();
            groupbyClause.accept(this, arg);
            Collection<VariableExpr> visibleVarsInCurrentScope = SqlppVariableUtil.getBindingVariables(groupbyClause);

            VariableExpr groupVar = groupbyClause.getGroupVar();
            Map<Expression, Identifier> groupFieldVars = getGroupFieldVariables(groupbyClause);

            Set<VariableExpr> unmappedVars =
                    getUnmappedVariables(visibleVarsPreGroupByScope, outerScopeVars, groupFieldVars);

            Collection<VariableExpr> freeVariables = new HashSet<>();
            Collection<VariableExpr> freeVariablesInGbyLets = new HashSet<>();
            if (selectBlock.hasLetHavingClausesAfterGroupby()) {
                for (AbstractClause letHavingClause : selectBlock.getLetHavingListAfterGroupby()) {
                    letHavingClause.accept(this, arg);
                    // Rewrites each let/having clause after the group-by.
                    rewriteExpressionUsingGroupVariable(groupVar, groupFieldVars, letHavingClause, outerScopeVars,
                            unmappedVars);
                    switch (letHavingClause.getClauseType()) {
                        case LET_CLAUSE:
                            LetClause letClause = (LetClause) letHavingClause;
                            Collection<VariableExpr> freeVariablesInClause =
                                    SqlppVariableUtil.getFreeVariables(letClause.getBindingExpr());
                            freeVariablesInClause.removeAll(visibleVarsInCurrentScope);
                            freeVariablesInGbyLets.addAll(freeVariablesInClause);
                            visibleVarsInCurrentScope.add(letClause.getVarExpr());
                            break;
                        case HAVING_CLAUSE:
                            freeVariables.addAll(SqlppVariableUtil.getFreeVariables(letHavingClause));
                            break;
                        default:
                            throw new IllegalStateException(String.valueOf(letHavingClause.getClauseType()));
                    }
                }
            }

            SelectExpression parentSelectExpression = (SelectExpression) arg;
            // We cannot rewrite ORDER BY and LIMIT if it's a SET operation query.
            if (!parentSelectExpression.getSelectSetOperation().hasRightInputs()) {
                if (parentSelectExpression.hasOrderby()) {
                    // Rewrites the ORDER BY clause.
                    OrderbyClause orderbyClause = parentSelectExpression.getOrderbyClause();
                    orderbyClause.accept(this, arg);
                    rewriteExpressionUsingGroupVariable(groupVar, groupFieldVars, orderbyClause, outerScopeVars,
                            unmappedVars);
                    freeVariables.addAll(SqlppVariableUtil.getFreeVariables(orderbyClause));
                }
                if (parentSelectExpression.hasLimit()) {
                    // Rewrites the LIMIT clause.
                    LimitClause limitClause = parentSelectExpression.getLimitClause();
                    limitClause.accept(this, arg);
                    rewriteExpressionUsingGroupVariable(groupVar, groupFieldVars, limitClause, outerScopeVars,
                            unmappedVars);
                    freeVariables.addAll(SqlppVariableUtil.getFreeVariables(limitClause));
                }
            }

            // Visits the select clause.
            SelectClause selectClause = selectBlock.getSelectClause();
            selectClause.accept(this, arg);
            // Rewrites the select clause.
            rewriteExpressionUsingGroupVariable(groupVar, groupFieldVars, selectClause, outerScopeVars, unmappedVars);
            freeVariables.addAll(SqlppVariableUtil.getFreeVariables(selectClause));
            freeVariables.removeAll(visibleVarsInCurrentScope);

            // Gets the final free variables.
            freeVariables.addAll(freeVariablesInGbyLets);
            freeVariables.removeIf(SqlppVariableUtil::isExternalVariableReference);

            // Gets outer scope variables.
            Collection<VariableExpr> decorVars = scopeChecker.getCurrentScope().getLiveVariables();
            decorVars.removeAll(visibleVarsInCurrentScope);

            // Only retains used free variables.
            if (!decorVars.containsAll(freeVariables)) {
                throw new CompilationException(ErrorCode.COMPILATION_ILLEGAL_STATE, groupbyClause.getSourceLocation(),
                        decorVars + ":" + freeVariables);
            }
            decorVars.retainAll(freeVariables);

            if (!decorVars.isEmpty()) {
                // Adds necessary decoration variables for the GROUP BY.
                // NOTE: we need to include outer binding variables so as they can be evaluated before
                // the GROUP BY instead of being inlined as part of nested pipepline. The current optimzier
                // is not able to optimize the latter case. The following query is such an example:
                // asterixdb/asterix-app/src/test/resources/runtimets/queries_sqlpp/dapd/q2-11
                List<GbyVariableExpressionPair> decorList = new ArrayList<>();
                if (groupbyClause.hasDecorList()) {
                    decorList.addAll(groupbyClause.getDecorPairList());
                }
                for (VariableExpr var : decorVars) {
                    decorList.add(new GbyVariableExpressionPair((VariableExpr) SqlppRewriteUtil.deepCopy(var),
                            (Expression) SqlppRewriteUtil.deepCopy(var)));
                }
                groupbyClause.setDecorPairList(decorList);
            }
        } else {
            selectBlock.getSelectClause().accept(this, arg);
        }
        return null;
    }

    private Map<Expression, Identifier> getGroupFieldVariables(GroupbyClause groupbyClause) {
        return groupbyClause.hasGroupFieldList()
                ? SqlppVariableUtil.createFieldVariableMap(groupbyClause.getGroupFieldList()) : Collections.emptyMap();
    }

    /**
     * Returns variables of the current SELECT block that were defined before GROUP BY clause but were not mapped by
     * GROUP AS sub-clause. These variables cannot be used by SQL aggregate functions after the GROUP BY
     */
    private Set<VariableExpr> getUnmappedVariables(Set<VariableExpr> preGroupByScopeVariables,
            Set<VariableExpr> outerScopeVariables, Map<Expression, Identifier> groupFieldVariables) {
        Set<VariableExpr> result = new HashSet<>(preGroupByScopeVariables);
        result.removeAll(outerScopeVariables);
        for (Expression expr : groupFieldVariables.keySet()) {
            if (expr.getKind() == Expression.Kind.VARIABLE_EXPRESSION) {
                result.remove(expr);
            }
        }
        return result;
    }

    // Applying sugar rewriting for group-by.
    private void rewriteExpressionUsingGroupVariable(VariableExpr groupVar, Map<Expression, Identifier> fieldVars,
            ILangExpression expr, Set<VariableExpr> outerScopeVariables, Set<VariableExpr> prohibitVars)
            throws CompilationException {
        Sql92AggregateFunctionVisitor visitor =
                new Sql92AggregateFunctionVisitor(context, groupVar, fieldVars, outerScopeVariables, prohibitVars);
        expr.accept(visitor, null);
    }
}
