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

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.dialect.AbstractDialect;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.sql.IdentifierProcessing;

import static org.assertj.core.api.Assertions.*;

class AnalyticSqlGeneratorTests {

	JdbcMappingContext context = new JdbcMappingContext();
	AnalyticSqlGenerator sqlGenerator = new AnalyticSqlGenerator(TestDialect.INSTANCE);

	@Test
	void simpleEntity() {

		String sql = sqlGenerator.findAll(getRequiredPersistentEntity(DummyEntity.class));

		assertThat(sql).isEqualTo("SELECT dummy_entity.id, dummy_entity.a_column FROM dummy_entity");

	}

	private RelationalPersistentEntity<?> getRequiredPersistentEntity(Class<DummyEntity> entityClass) {
		return context.getRequiredPersistentEntity(entityClass);
	}

	static class DummyEntity {
		@Id Long id;

		String aColumn;
	}

	static class TestDialect extends AnsiDialect{

		static TestDialect INSTANCE = new TestDialect();
		@Override
		public IdentifierProcessing getIdentifierProcessing() {
			return IdentifierProcessing.NONE;
		}
	}
}
