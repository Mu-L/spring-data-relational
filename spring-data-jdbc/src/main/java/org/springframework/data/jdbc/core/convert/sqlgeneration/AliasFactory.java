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

import java.util.HashMap;

public class AliasFactory {

	HashMap<Object, String> cache = new HashMap<>();
	private int tableIndex = 0;
	private int viewIndex = 0;

	String getAliasFor(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select select) {

		return cache.computeIfAbsent(select, k -> createAlias(select));
	}

	private String createAlias(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select select) {

		if (select instanceof AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition tableDefinition) {

			String baseTableName = tableDefinition.getTable().getTableName().toString();
			return "T%04d_".formatted(++tableIndex) + sanitize(baseTableName);
		}

		if (select instanceof AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticView view) {
			return "V%04d".formatted(++viewIndex) ;
		}

		throw new UnsupportedOperationException("can't generate alias for " + select);
	}

	private String sanitize(String baseTableName) {

		String lettersOnly = baseTableName.replaceAll("[^A-Za-z]+", "");
		return lettersOnly.toUpperCase().substring(0, 10);
	}

}
