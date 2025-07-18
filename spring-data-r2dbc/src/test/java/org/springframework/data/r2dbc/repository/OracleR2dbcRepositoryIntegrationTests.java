/*
 * Copyright 2019-2025 the original author or authors.
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
package org.springframework.data.r2dbc.repository;

import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.r2dbc.testing.EnabledOnClass;
import org.springframework.data.r2dbc.testing.ExternalDatabase;
import org.springframework.data.r2dbc.testing.OracleTestSupport;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Integration tests for {@link LegoSetRepository} using {@link R2dbcRepositoryFactory} against Oracle.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnabledOnClass("oracle.r2dbc.impl.OracleConnectionFactoryProviderImpl")
@DisabledOnOs(architectures = "aarch64")
public class OracleR2dbcRepositoryIntegrationTests extends AbstractR2dbcRepositoryIntegrationTests {

	@RegisterExtension public static final ExternalDatabase database = OracleTestSupport.database();

	@Configuration
	@EnableR2dbcRepositories(considerNestedRepositories = true,
			includeFilters = @Filter(classes = OracleLegoSetRepository.class, type = FilterType.ASSIGNABLE_TYPE))
	static class IntegrationTestConfiguration extends AbstractR2dbcConfiguration {

		@Bean
		@Override
		public ConnectionFactory connectionFactory() {
			return OracleTestSupport.createConnectionFactory(database);
		}

		@Override
		public R2dbcMappingContext r2dbcMappingContext(Optional<NamingStrategy> namingStrategy,
				R2dbcCustomConversions r2dbcCustomConversions, RelationalManagedTypes r2dbcManagedTypes) {
			R2dbcMappingContext mappingContext = super.r2dbcMappingContext(namingStrategy, r2dbcCustomConversions,
					r2dbcManagedTypes);
			mappingContext.setForceQuote(false);
			return mappingContext;
		}
	}

	@Override
	protected DataSource createDataSource() {
		return OracleTestSupport.createDataSource(database);
	}

	@Override
	protected ConnectionFactory createConnectionFactory() {
		return OracleTestSupport.createConnectionFactory(database);
	}

	@Override
	protected String getCreateTableStatement() {
		return OracleTestSupport.CREATE_TABLE_LEGOSET_WITH_ID_GENERATION;
	}

	@Override
	protected Class<? extends LegoSetRepository> getRepositoryInterfaceType() {
		return OracleLegoSetRepository.class;
	}

	interface OracleLegoSetRepository extends LegoSetRepository {

		@Override
		@Query("SELECT name FROM legoset")
		Flux<Named> findAsProjection();

		@Override
		@Query("SELECT * FROM legoset WHERE manual = :manual")
		Mono<LegoSet> findByManual(int manual);

		@Override
		@Query("SELECT id FROM legoset")
		Flux<Integer> findAllIds();
	}
}
