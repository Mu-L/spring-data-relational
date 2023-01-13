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
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.InlineQuery;
import org.springframework.data.relational.core.sql.SelectBuilder;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.relational.core.sql.StatementBuilder;
import org.springframework.data.relational.core.sql.Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class StructureToSelect {
	SelectBuilder.SelectFromAndJoin createSelect(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select queryStructure) {

		if (queryStructure instanceof AnalyticStructureBuilder.TableDefinition tableDefinition) {
			System.out.println("table");
			return createSimpleSelect(tableDefinition);
		}

		if (queryStructure instanceof AnalyticStructureBuilder.AnalyticJoin analyticJoin) {
			System.out.println("join");
			return createJoin(analyticJoin);
		}

		if (queryStructure instanceof AnalyticStructureBuilder.AnalyticView analyticView) {
			System.out.println("view");
			return createView(analyticView);
		}

		throw new UnsupportedOperationException("Can't convert " + queryStructure);
	}

	private SelectBuilder.SelectFromAndJoin createView(AnalyticStructureBuilder.AnalyticView analyticView) {
		// TODO: I'm not completely sure we neeed the views???
		return createSelect((AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select) analyticView.getFroms().get(0));
	}

	private SelectBuilder.SelectFromAndJoin createJoin(AnalyticStructureBuilder.AnalyticJoin analyticJoin) {

		AnalyticStructureBuilder.Select parent = analyticJoin.getParent();
		SelectBuilder.SelectFromAndJoin parentSelect = createSelect(parent);
		InlineQuery parentQuery = InlineQuery.create(parentSelect.build(), "parent");

		AnalyticStructureBuilder.Select child = analyticJoin.getChild();
		SelectBuilder.SelectFromAndJoin childSelect = createSelect(child);
		InlineQuery childQuery = InlineQuery.create(childSelect.build(), "child");

		// TODO: we need access to the columns ...

		StatementBuilder.select();

		return null;
	}

	private  SelectBuilder.SelectFromAndJoin createSimpleSelect(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition tableDefinition) {

		List<? extends AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn> analyticColumns = tableDefinition
				.getColumns();

		Collection<Expression> tableColumns = new ArrayList<>();
		Table table = null;
		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn analyticColumn : analyticColumns) {

			RelationalPersistentProperty property = analyticColumn.getColumn();
			if (property == null) {
				// TODO: handle all the special join management columns.
				continue;
			}
			SqlIdentifier tableName = property.getOwner().getTableName();
			SqlIdentifier columnName = property.getColumnName();
			table = Table.create(tableName);
			tableColumns.add(table.column(columnName));
		}

		SelectBuilder.SelectFromAndJoin from = StatementBuilder.select(tableColumns).from(table);
		return from;
	}
}
