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
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class SqlAssert extends AbstractAssert<SqlAssert, Statement> {

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

		List<String> actualColumns = new ArrayList<>();
		for (SelectItem selectItem : getSelect().getSelectItems()) {
			selectItem.accept(new SelectItemVisitorAdapter() {
				@Override
				public void visit(SelectExpressionItem item) {
					final Alias alias = item.getAlias();
					actualColumns.add(item.getExpression().toString());
				}
			});
		}

		Assertions.assertThat(actualColumns).containsExactlyInAnyOrder(columns);

		return this;
	}

	public SqlAssert selectsFrom(String tableName) {

		Assertions.assertThat(getSelect().getFromItem().toString()) //
				.isEqualTo(tableName);

		return this;
	}

	private PlainSelect getSelect() {
		return (PlainSelect) ((Select) actual).getSelectBody();
	}
}
