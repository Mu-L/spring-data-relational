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

import java.util.HashMap;

import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

public class AliasFactory {

	public static final int MAX_NAME_HINT_LENGTH = 20;
	HashMap<Object, String> cache = new HashMap<>();
	private int tableIndex = 0;
	private int viewIndex = 0;
	private int columnIndex = 0;

	String getAliasFor(Object key) {

		String values = cache.computeIfAbsent(key, k -> createAlias(k));

		return values;
	}

	private String createAlias(Object key) {

		if (key instanceof AnalyticStructureBuilder.TableDefinition tableDefinition) {
			return createAlias(tableDefinition.getTable());
		}

		if (key instanceof RelationalPersistentEntity rpe) {

			String baseTableName = rpe.getTableName().toString();
			return "T%04d_".formatted(++tableIndex) + sanitize(baseTableName);
		}

		if (key instanceof RelationalPersistentProperty rpp) {

			String baseColumnName = rpp.getName();
			return "C%04d_".formatted(++columnIndex) + sanitize(baseColumnName);
		}

		if (key instanceof AnalyticStructureBuilder.AnalyticView) {
			return "V%04d".formatted(++viewIndex);
		}

		throw new UnsupportedOperationException("can't generate alias for " + key);
	}

	private String sanitize(String baseTableName) {

		String lettersOnly = baseTableName.replaceAll("[^A-Za-z]+", "");
		return lettersOnly.toUpperCase().substring(0, Math.min(MAX_NAME_HINT_LENGTH, lettersOnly.length()));
	}

}
