/*
 * Copyright 2022-2025 the original author or authors.
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
package org.springframework.data.jdbc.core.convert;

import java.sql.SQLType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.jdbc.support.JdbcUtil;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PersistentPropertyPathAccessor;
import org.springframework.data.relational.core.conversion.IdValueSource;
import org.springframework.data.relational.core.mapping.AggregatePath;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.mapping.RelationalPredicates;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.lang.Nullable;

/**
 * Creates the {@link SqlIdentifierParameterSource} for various SQL operations, dialect identifier processing rules and
 * applicable converters.
 *
 * @author Jens Schauder
 * @author Chirag Tailor
 * @author Mikhail Polivakha
 * @author Mark Paluch
 * @since 2.4
 */
public class SqlParametersFactory {

	private final RelationalMappingContext context;
	private final JdbcConverter converter;

	/**
	 * @since 3.1
	 */
	public SqlParametersFactory(RelationalMappingContext context, JdbcConverter converter) {
		this.context = context;
		this.converter = converter;
	}

	/**
	 * Creates the parameters for a SQL insert operation.
	 *
	 * @param instance the entity to be inserted. Must not be {@code null}.
	 * @param domainType the type of the instance. Must not be {@code null}.
	 * @param identifier information about data that needs to be considered for the insert but which is not part of the
	 *          entity. Namely references back to a parent entity and key/index columns for entities that are stored in a
	 *          {@link Map} or {@link List}.
	 * @param idValueSource the {@link IdValueSource} for the insert.
	 * @return the {@link SqlIdentifierParameterSource} for the insert. Guaranteed to not be {@code null}.
	 * @since 2.4
	 */
	<T> SqlIdentifierParameterSource forInsert(T instance, Class<T> domainType, Identifier identifier,
			IdValueSource idValueSource) {

		RelationalPersistentEntity<T> persistentEntity = getRequiredPersistentEntity(domainType);
		SqlIdentifierParameterSource parameterSource = getParameterSource(instance, persistentEntity, "",
				PersistentProperty::isIdProperty);

		identifier.forEach((name, value, type) -> addConvertedPropertyValue(parameterSource, name, value, type));

		if (IdValueSource.PROVIDED.equals(idValueSource)) {

			PersistentPropertyPathAccessor<T> propertyPathAccessor = persistentEntity.getPropertyPathAccessor(instance);

			AggregatePath.ColumnInfos columnInfos = context.getAggregatePath(persistentEntity).getTableInfo().idColumnInfos();

			//  fullPath: because we use the result with a PropertyPathAccessor
			columnInfos.forEach((ap, __) -> {
				Object idValue = propertyPathAccessor.getProperty(ap.getRequiredPersistentPropertyPath());
				RelationalPersistentProperty idProperty = ap.getRequiredLeafProperty();
				addConvertedPropertyValue(parameterSource, idProperty, idValue, idProperty.getColumnName());
			});

		}
		return parameterSource;
	}

	/**
	 * Creates the parameters for a SQL update operation.
	 *
	 * @param instance the entity to be updated. Must not be {@code null}.
	 * @param domainType the type of the instance. Must not be {@code null}.
	 * @return the {@link SqlIdentifierParameterSource} for the update. Guaranteed to not be {@code null}.
	 * @since 2.4
	 */
	<T> SqlIdentifierParameterSource forUpdate(T instance, Class<T> domainType) {

		return getParameterSource(instance, getRequiredPersistentEntity(domainType), "",
				RelationalPersistentProperty::isInsertOnly);
	}

	/**
	 * Creates the parameters for a SQL query by id.
	 *
	 * @param id the entity id. Must not be {@code null}.
	 * @param domainType the type of the instance. Must not be {@code null}.
	 * @return the {@link SqlIdentifierParameterSource} for the query. Guaranteed to not be {@code null}.
	 * @since 2.4
	 */
	<T> SqlIdentifierParameterSource forQueryById(Object id, Class<T> domainType) {

		return doWithIdentifiers(domainType, (columns, idProperty, complexId) -> {

			SqlIdentifierParameterSource parameterSource = new SqlIdentifierParameterSource();
			BiFunction<Object, AggregatePath, Object> valueExtractor = getIdMapper(complexId);

			columns.forEach((ap, ci) -> addConvertedPropertyValue( //
					parameterSource, //
					ap.getRequiredLeafProperty(), //
					valueExtractor.apply(id, ap), //
					ci.name() //
			));

			return parameterSource;
		});
	}

	/**
	 * Creates the parameters for a SQL query by ids.
	 *
	 * @param ids the entity ids. Must not be {@code null}.
	 * @param domainType the type of the instance. Must not be {@code null}.
	 * @return the {@link SqlIdentifierParameterSource} for the query. Guaranteed to not be {@code null}.
	 * @since 2.4
	 */
	<T> SqlIdentifierParameterSource forQueryByIds(Iterable<?> ids, Class<T> domainType) {

		return doWithIdentifiers(domainType, (columns, idProperty, complexId) -> {

			SqlIdentifierParameterSource parameterSource = new SqlIdentifierParameterSource();

			BiFunction<Object, AggregatePath, Object> valueExtractor = getIdMapper(complexId);

			List<Object[]> parameterValues = new ArrayList<>(ids instanceof Collection<?> c ? c.size() : 16);
			for (Object id : ids) {

				Object[] tupleList = new Object[columns.size()];

				int i = 0;
				for (AggregatePath path : columns.paths()) {
					tupleList[i++] = valueExtractor.apply(id, path);
				}

				parameterValues.add(tupleList);
			}

			parameterSource.addValue(SqlGenerator.IDS_SQL_PARAMETER, parameterValues);
			return parameterSource;
		});
	}

	private <T> T doWithIdentifiers(Class<?> domainType, IdentifierCallback<T> callback) {

		RelationalPersistentEntity<?> entity = context.getRequiredPersistentEntity(domainType);
		RelationalPersistentProperty idProperty = entity.getRequiredIdProperty();
		RelationalPersistentEntity<?> complexId = context.getPersistentEntity(idProperty);
		AggregatePath.ColumnInfos columns = context.getAggregatePath(entity).getTableInfo().idColumnInfos();

		return callback.doWithIdentifiers(columns, idProperty, complexId);
	}

	interface IdentifierCallback<T> {

		T doWithIdentifiers(AggregatePath.ColumnInfos columns, RelationalPersistentProperty idProperty,
				RelationalPersistentEntity<?> complexId);
	}

	/**
	 * Creates the parameters for a SQL query of related entities.
	 *
	 * @param identifier the identifier describing the relation. Must not be {@code null}.
	 * @return the {@link SqlIdentifierParameterSource} for the query. Guaranteed to not be {@code null}.
	 * @since 2.4
	 */
	SqlIdentifierParameterSource forQueryByIdentifier(Identifier identifier) {

		SqlIdentifierParameterSource parameterSource = new SqlIdentifierParameterSource();

		identifier.toMap()
				.forEach((name, value) -> addConvertedPropertyValue(parameterSource, name, value, value.getClass()));

		return parameterSource;
	}

	private BiFunction<Object, AggregatePath, Object> getIdMapper(@Nullable RelationalPersistentEntity<?> complexId) {

		if (complexId == null) {
			return (id, aggregatePath) -> id;
		}

		return (id, aggregatePath) -> {

			PersistentPropertyAccessor<Object> accessor = complexId.getPropertyAccessor(id);
			return accessor.getProperty(aggregatePath.getRequiredLeafProperty());
		};
	}

	private void addConvertedPropertyValue(SqlIdentifierParameterSource parameterSource,
			RelationalPersistentProperty property, @Nullable Object value, SqlIdentifier name) {

		addConvertedValue(parameterSource, value, name, converter.getColumnType(property),
				converter.getTargetSqlType(property));
	}

	private void addConvertedPropertyValue(SqlIdentifierParameterSource parameterSource, SqlIdentifier name, Object value,
			Class<?> javaType) {

		addConvertedValue(parameterSource, value, name, javaType, JdbcUtil.targetSqlTypeFor(javaType));
	}

	private void addConvertedValue(SqlIdentifierParameterSource parameterSource, @Nullable Object value,
			SqlIdentifier paramName, Class<?> javaType, SQLType sqlType) {

		JdbcValue jdbcValue = converter.writeJdbcValue( //
				value, //
				javaType, //
				sqlType //
		);

		parameterSource.addValue( //
				paramName, //
				jdbcValue.getValue(), //
				jdbcValue.getJdbcType().getVendorTypeNumber());
	}

	@SuppressWarnings("unchecked")
	private <S> RelationalPersistentEntity<S> getRequiredPersistentEntity(Class<S> domainType) {
		return (RelationalPersistentEntity<S>) context.getRequiredPersistentEntity(domainType);
	}

	private <S, T> SqlIdentifierParameterSource getParameterSource(@Nullable S instance,
			RelationalPersistentEntity<S> persistentEntity, String prefix,
			Predicate<RelationalPersistentProperty> skipProperty) {

		SqlIdentifierParameterSource parameters = new SqlIdentifierParameterSource();

		PersistentPropertyAccessor<S> propertyAccessor = instance != null ? persistentEntity.getPropertyAccessor(instance)
				: NoValuePropertyAccessor.instance();


		persistentEntity.doWithAll(property -> {

			if (skipProperty.test(property) || !property.isWritable()) {
				return;
			}

			if (RelationalPredicates.isRelation(property)) {
				return;
			}

			if (property.isEmbedded()) {

				Object value = propertyAccessor.getProperty(property);
				RelationalPersistentEntity<?> embeddedEntity = context.getPersistentEntity(property.getTypeInformation());
				SqlIdentifierParameterSource additionalParameters = getParameterSource((T) value,
						(RelationalPersistentEntity<T>) embeddedEntity, prefix + property.getEmbeddedPrefix(), skipProperty);
				parameters.addAll(additionalParameters);
			} else {

				Object value = propertyAccessor.getProperty(property);
				SqlIdentifier paramName = property.getColumnName().transform(prefix::concat);

				addConvertedPropertyValue(parameters, property, value, paramName);
			}
		});

		return parameters;
	}

	/**
	 * A {@link PersistentPropertyAccessor} implementation always returning null
	 *
	 * @param <T>
	 */
	static class NoValuePropertyAccessor<T> implements PersistentPropertyAccessor<T> {

		private static final NoValuePropertyAccessor INSTANCE = new NoValuePropertyAccessor();

		static <T> NoValuePropertyAccessor<T> instance() {
			return INSTANCE;
		}

		@Override
		public void setProperty(PersistentProperty<?> property, @Nullable Object value) {
			throw new UnsupportedOperationException("Cannot set value on 'null' target object");
		}

		@Override
		public Object getProperty(PersistentProperty<?> property) {
			return null;
		}

		@Override
		public T getBean() {
			return null;
		}
	}
}
