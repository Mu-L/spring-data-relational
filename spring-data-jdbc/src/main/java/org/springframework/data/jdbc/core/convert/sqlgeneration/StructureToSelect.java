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

import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class StructureToSelect {
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

	private SelectBuilder.SelectFromAndJoinCondition createJoin(AnalyticStructureBuilder.AnalyticJoin analyticJoin) {

		AnalyticStructureBuilder.Select parent = analyticJoin.getParent();
		SelectBuilder.BuildSelect parentSelect = createSelect(parent);
//		InlineQuery parentQuery = InlineQuery.create(parentSelect.build(), "parent");

		AnalyticStructureBuilder.Select child = analyticJoin.getChild();
		SelectBuilder.BuildSelect childSelect = createSelect(child);
		InlineQuery childQuery = InlineQuery.create(childSelect.build(), "child");

		Condition condition = createJoinCondition(analyticJoin);
		return ((SelectBuilder.SelectFromAndJoin)parentSelect).join(childQuery).on(condition);
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
		Table table = null;
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
			table = Table.create(tableName);
			Column column = table.column(columnName);
			tableColumns.add(column);
			System.out.println("column " + column);

		}

		SelectBuilder.SelectFromAndJoin from = StatementBuilder.select(tableColumns).from(table);
		return from;
	}
}
