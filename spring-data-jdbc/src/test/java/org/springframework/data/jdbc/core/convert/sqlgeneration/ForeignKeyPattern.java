/*
 * Copyright 2022 the original author or authors.
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

import org.springframework.lang.Nullable;

record ForeignKeyPattern<T, C> (T table, C name) implements Pattern {

	public static <T, C> ForeignKeyPattern<T, C> fk(T table, C column) {
		return new ForeignKeyPattern<>(table, column);
	}

	@Override
	public boolean matches(AnalyticStructureBuilder<?, ?>.Select select,
			AnalyticStructureBuilder<?, ?>.AnalyticColumn actualColumn) {

		AnalyticStructureBuilder<?, ?>.ForeignKey foreignKey = extractForeignKey(actualColumn);
		if (foreignKey == null) {
			return false;
		}

		Object column = foreignKey.getColumn();
		if (name instanceof Pattern  pattern) {
			if( !pattern.matches(select, foreignKey.getForeignKeyColumn())) {
				return false;
			}
		} else {
			if (!name.equals(column)) {
				return false;
			}
		}

		if (table == null) {
			return true;
		}

		AnalyticStructureBuilder<?, ?>.TableDefinition fkOwner = foreignKey.getOwner();

		return table.equals(fkOwner.getTable());
	}

	@Override
	public String render() {
		return toString();
	}

	@Nullable
	static AnalyticStructureBuilder<?, ?>.ForeignKey extractForeignKey(
			AnalyticStructureBuilder<?, ?>.AnalyticColumn column) {
		return AnalyticStructureBuilderSelectAssert.extract(AnalyticStructureBuilder.ForeignKey.class, column);
	}

	@Override
	public String toString() {

		return "FK(" + table + ", " + name + ")";

	}
}
