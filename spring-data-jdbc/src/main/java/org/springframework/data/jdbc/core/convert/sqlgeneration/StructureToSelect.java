/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.jdbc.core.convert.sqlgeneration;

import org.springframework.data.jdbc.core.convert.Identifier;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

class StructureToSelect {

	AliasFactory aliasFactory = new AliasFactory();

	SelectBuilder.BuildSelect createSelect(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select queryStructure) {

		if (queryStructure instanceof AnalyticStructureBuilder.TableDefinition tableDefinition) {
			System.out.println("table " + tableDefinition.getTable());
			return createSimpleSelect(tableDefinition);
		}

		if (queryStructure instanceof AnalyticStructureBuilder.AnalyticJoin analyticJoin) {
			System.out.println("join");
			SelectBuilder.SelectFromAndJoinCondition join = createJoin(analyticJoin);
			return join;
		}

		if (queryStructure instanceof AnalyticStructureBuilder.AnalyticView analyticView) {
			System.out.println("view");
			return createView(analyticView);
		}

		throw new UnsupportedOperationException("Can't convert " + queryStructure);
	}

	private SelectBuilder.BuildSelect createView(AnalyticStructureBuilder.AnalyticView analyticView) {
		// TODO: I'm not completely sure we need the views???
		return createSelect((AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select) analyticView.getFroms().get(0));
	}

	private SelectBuilder.SelectFromAndJoinCondition createJoin(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticJoin analyticJoin) {

		AnalyticStructureBuilder.Select child = analyticJoin.getChild();
		SelectBuilder.BuildSelect childSelect = createSelect(child);
		InlineQuery childQuery = InlineQuery.create(childSelect.build(), getAliasFor(child));

		Collection columns = getSelectList(child, childQuery);


		AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select parent = analyticJoin.getParent();
		TableLike parentTable = tableFor(parent);
		columns.addAll(getSelectList(parent, parentTable));

		SelectBuilder.SelectFromAndJoin selectAndParent = StatementBuilder.select(columns).from(parentTable);

		Condition condition = createJoinCondition(analyticJoin);
		return selectAndParent.join(childQuery).on(condition);
	}

	private String getAliasFor(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select select) {
		return aliasFactory.getAliasFor(select);
	}

	private static Collection<Expression> getSelectList(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select parent, TableLike parentTable) {

		Collection<Expression> tableColumns = new ArrayList<>();

		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn analyticColumn : parent.getColumns()) {

			if (analyticColumn instanceof AnalyticStructureBuilder.ForeignKey) {
				continue;
			}
			RelationalPersistentProperty property = analyticColumn.getColumn();
			if (property == null) {
				// TODO: handle all the special join management columns.
				continue;
			}

			SqlIdentifier columnName = property.getColumnName();

			Column column = parentTable.column(columnName);
			tableColumns.add(column);
			System.out.println("column " + column);

		}
		return tableColumns;
	}

	private TableLike tableFor(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select parent) {

		if (parent instanceof AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition tableDefinition) {
			return Table.create(tableDefinition.getTable().getTableName()).as(getAliasFor(parent));
		}

		throw new UnsupportedOperationException("can only create table names for TableDefinitions right now") ;
	}

	private Condition createJoinCondition(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticJoin analyticJoin) {
		
		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.JoinCondition joinCondition : analyticJoin.getConditions()) {
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn left = joinCondition.getLeft();
			joinCondition.getRight();

			return  Conditions.isEqual(Expressions.just("1"), Expressions.just("2"));
		}
		return null;
	}





	private  SelectBuilder.SelectFromAndJoin createSimpleSelect(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition tableDefinition) {

		List<? extends AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn> analyticColumns = tableDefinition
				.getColumns();

		Collection<Expression> tableColumns = new ArrayList<>();
		TableLike table = tableFor(tableDefinition);
		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn analyticColumn : analyticColumns) {

			if (analyticColumn instanceof AnalyticStructureBuilder.ForeignKey) {
				continue;
			}
			RelationalPersistentProperty property = analyticColumn.getColumn();
			if (property == null) {
				// TODO: handle all the special join management columns.
				continue;
			}
			SqlIdentifier tableName = property.getOwner().getTableName();
			SqlIdentifier columnName = property.getColumnName();
			Column column = table.column(columnName);
			tableColumns.add(column);
			System.out.println("column " + column);

		}

		SelectBuilder.SelectFromAndJoin from = StatementBuilder.select(tableColumns).from(table);
		return from;
	}
}
