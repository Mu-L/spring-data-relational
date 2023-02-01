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

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

public class SqlAssert extends AbstractAssert<SqlAssert, Statement> {

	private AliasFactory aliasFactory;

	protected SqlAssert(Statement actual, Class<?> selfType) {
		super(actual, selfType);
	}

	static SqlAssert assertThatParsed(String actualSql) {

		try {
			Statement parsed = CCJSqlParserUtil.parse(actualSql);
			return new SqlAssert(parsed, SqlAssert.class);
		} catch (JSQLParserException e) {
			Assertions.fail("Couldn't parse '%s'".formatted(actualSql));
		}

		throw new IllegalStateException("This should be unreachable");
	}

	public SqlAssert hasColumns(String... columns) {

		List<ActualColumn> actualColumns = collectActualColumns();

		List<String> notFound = new ArrayList<>();

		for (String column : columns) {
			for (ActualColumn currentColumn : actualColumns) {
				if (currentColumn.column.equals(column)) {
					actualColumns.remove(currentColumn);
					break;
				}
			}
			notFound.add(column);
		}

		if (actualColumns.isEmpty() && notFound.isEmpty()) {
			return this;
		}

		String failureMessage = "Expected %s to contain columns representing %s %n but ";
		if (!notFound.isEmpty()) {
			failureMessage += "no columns for %s were found";
		}
		if (!notFound.isEmpty() && !actualColumns.isEmpty()) {
			failureMessage += "%n and ";
		}
		if (!actualColumns.isEmpty()) {
			failureMessage += "the columns %s where not expected.";
		}

		if (!notFound.isEmpty() && !actualColumns.isEmpty()) {
			throw failure(failureMessage, getSelect().toString(), columns, notFound, actualColumns);
		}
		if (!notFound.isEmpty()) {
			throw failure(failureMessage, getSelect().toString(), columns, notFound);
		} else {
			throw failure(failureMessage, getSelect().toString(), columns, actualColumns);
		}

	}

	public SqlAssert hasColumns(ColumnsSpec columnsSpec) {

		List<ActualColumn> actualColumns = collectActualColumns();

		List<RelationalPersistentProperty> notFound = new ArrayList<>();

		columnsSpec.foreach((RelationalPersistentProperty property) -> {
			for (ActualColumn currentColumn : actualColumns) {
				String alias = aliasFactory.getAliasFor(property);
				if (currentColumn.alias.equals(alias) || currentColumn.column.equals(alias)) {
					actualColumns.remove(currentColumn);
					return;
				}
			}
			notFound.add(property);
		});

		if (actualColumns.isEmpty() && notFound.isEmpty()) {
			return this;
		}

		String failureMessage = "Expected %s to contain columns representing %s %n but ";
		if (!notFound.isEmpty()) {
			failureMessage += "no columns for %s were found";
		}
		if (!notFound.isEmpty() && !actualColumns.isEmpty()) {
			failureMessage += "%n and ";
		}
		if (!actualColumns.isEmpty()) {
			failureMessage += "the columns %s where not expected.";
		}

		if (!notFound.isEmpty() && !actualColumns.isEmpty()) {
			throw failure(failureMessage, getSelect().toString(), columnsSpec, notFound, actualColumns);
		}
		if (!notFound.isEmpty()) {
			throw failure(failureMessage, getSelect().toString(), columnsSpec, notFound);
		} else {
			throw failure(failureMessage, getSelect().toString(), columnsSpec, actualColumns);
		}

	}

	private List<ActualColumn> collectActualColumns() {

		List<ActualColumn> actualColumns = new ArrayList<>();
		for (SelectItem selectItem : getSelect().getSelectItems()) {
			selectItem.accept(new SelectItemVisitorAdapter() {
				@Override
				public void visit(SelectExpressionItem item) {
					actualColumns.add(new ActualColumn(item.getExpression().toString(), String.valueOf(item.getAlias())));
				}
			});
		}
		return actualColumns;
	}

	public SqlAssert selectsFrom(String tableName) {

		Assertions.assertThat(getSelect().getFromItem().toString()) //
				.isEqualTo(tableName);

		return this;
	}

	private PlainSelect getSelect() {
		return (PlainSelect) ((Select) actual).getSelectBody();
	}

	static ColumnsSpec from(RelationalPersistentEntity<?> entity) {
		return new ColumnsSpec(entity);
	}

	public SqlAssert withAliases(AliasFactory aliasFactory) {
		this.aliasFactory = aliasFactory;
		return this;
	}

	static class ColumnsSpec {
		private final RelationalPersistentEntity<?> currentEntity;
		private final List<RelationalPersistentProperty> properties = new ArrayList<>();

		public ColumnsSpec(RelationalPersistentEntity<?> entity) {
			currentEntity = entity;
		}

		public ColumnsSpec property(String name) {
			properties.add(currentEntity.getRequiredPersistentProperty(name));
			return this;
		}

		public void foreach(Consumer<? super RelationalPersistentProperty> propertyConsumer) {
			properties.forEach(propertyConsumer);
		}
	}

	private record ActualColumn(String expression, String table, String column, String alias) {
		ActualColumn(String expression, String alias) {
			this(expression, table(expression), column(expression), alias);
		}

		static String table(String expression) {

			int index = expression.indexOf(".");
			if (index > 0) {
				return expression.substring(0, index);
			}
			return expression;
		}

		static String column(String expression) {

			int index = expression.indexOf(".");
			if (index > 0) {
				return expression.substring(index + 1);
			}
			return expression;
		}
	}

}
