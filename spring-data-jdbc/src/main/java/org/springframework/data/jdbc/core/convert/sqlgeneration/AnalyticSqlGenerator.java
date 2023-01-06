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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.mapping.SimplePropertyHandler;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.RenderContextFactory;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.relational.core.sql.StatementBuilder;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.core.sql.render.RenderContext;
import org.springframework.data.relational.core.sql.render.SqlRenderer;

public class AnalyticSqlGenerator {

	private final AnsiDialect dialect;

	public AnalyticSqlGenerator(AnsiDialect dialect) {
		this.dialect = dialect;
	}


	public String findAll(RelationalPersistentEntity<?> aggregateRoot) {
		return render(createSelectStructure(aggregateRoot));
	}

	private AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select createSelectStructure(
			RelationalPersistentEntity<?> aggregateRoot) {

		AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty> builder = new AnalyticStructureBuilder<>();

		builder.addTable(aggregateRoot, td -> {
			List<RelationalPersistentProperty> persistentProperties = new ArrayList<>();
			aggregateRoot
					.doWithProperties((SimplePropertyHandler) p -> persistentProperties.add((RelationalPersistentProperty) p));
			for (RelationalPersistentProperty p : persistentProperties) {
				td = td.withColumns(p);
			}
			return td;
		});

		return builder.getSelect();
	}

	private String render(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select queryStructure) {

		SelectBuilder.SelectFromAndJoin from = createSelect(queryStructure);

		Select select = from.build();

		SqlRenderer sqlRenderer = getSqlRenderer();
		return sqlRenderer.render(select);
	}

	private SqlRenderer getSqlRenderer() {

		RenderContext renderContext = new RenderContextFactory(dialect).createRenderContext();
		return SqlRenderer.create(renderContext);
	}

	private static SelectBuilder.SelectFromAndJoin createSelect(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select queryStructure) {
		List<? extends AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn> analyticColumns = queryStructure
				.getColumns();

		Collection<Expression> tableColumns = new ArrayList<>();
		Table table = null;
		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn analyticColumn : analyticColumns) {

			RelationalPersistentProperty property = analyticColumn.getColumn();
			SqlIdentifier tableName = property.getOwner().getTableName();
			SqlIdentifier columnName = property.getColumnName();
			table = Table.create(tableName);
			tableColumns.add(table.column(columnName));
		}

		SelectBuilder.SelectFromAndJoin from = StatementBuilder.select(tableColumns).from(table);
		return from;
	}
}
