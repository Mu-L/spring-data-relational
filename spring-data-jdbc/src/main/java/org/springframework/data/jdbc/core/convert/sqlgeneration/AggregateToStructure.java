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
import java.util.List;

import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.SimplePropertyHandler;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

class AggregateToStructure {

	private final JdbcMappingContext context;

	AggregateToStructure(JdbcMappingContext context) {
		this.context = context;
	}

	AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select createSelectStructure(
			RelationalPersistentEntity<?> aggregateRoot) {

		AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty> builder = new AnalyticStructureBuilder<>();

		builder.addTable(aggregateRoot, td -> configureTableDefinition(aggregateRoot, td));
		addReferencedEntities(builder, aggregateRoot);

		return builder.getSelect();
	}

	private void addReferencedEntities(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty> builder,
			RelationalPersistentEntity<?> currentEntity) {

		currentEntity.doWithProperties((PropertyHandler<RelationalPersistentProperty>) p -> {
			if (p.isEntity()) {
				RelationalPersistentEntity<?> entity = context.getRequiredPersistentEntity(p.getActualType());
				builder.addChildTo(p.getOwner(), entity, td2 -> configureTableDefinition(entity, td2));
			}
		});
	}

	private AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition configureTableDefinition(
			RelationalPersistentEntity<?> aggregateRoot,
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition td) {

		List<RelationalPersistentProperty> persistentProperties = new ArrayList<>();
		aggregateRoot
				.doWithProperties((SimplePropertyHandler) p -> persistentProperties.add((RelationalPersistentProperty) p));
		for (RelationalPersistentProperty p : persistentProperties) {
			if (p.isIdProperty()) {
				td = td.withId(p);
			} else {
				if (!p.isEntity()) {
					td = td.withColumns(p);
				}
			}
		}
		return td;
	}
}
