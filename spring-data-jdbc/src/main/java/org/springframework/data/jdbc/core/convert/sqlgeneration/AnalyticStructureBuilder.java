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

import static java.util.Arrays.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.AnalyticStructureBuilder.Multiplicity.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Builds the structure of an analytic query. The structure contains arbitrary objects for tables and columns. There are
 * two kinds of parent child relationship:
 * <ol>
 * <li>there is the relationship on aggregate level: the purchase order is the parent of the line item. This
 * relationship is denoted simply by "parent" and "child".</li>
 * <li>there is the parent child relationship inside the analytic query structure, that gets build by this builder.
 * Where a join combines two nodes. In this relationship the join is parent to the two nodes, where one node might
 * represent the purchase order and the other the line item. This kind or relationship shall be prefixed by "node". The
 * join {@literal node} is the {@literal nodeParent} of purchase order and line item.</li>
 * </ol>
 */
class AnalyticStructureBuilder<T, C> {

	/** The select that is getting build */
	private Select nodeRoot;
	private final Map<Object, Select> nodeParentLookUp = new HashMap<>();

	AnalyticStructureBuilder<T, C> addTable(T table,
			Function<TableDefinition, TableDefinition> tableDefinitionConfiguration) {

		this.nodeRoot = createTable(table, tableDefinitionConfiguration);

		return this;
	}

	AnalyticStructureBuilder<T, C> addChildTo(T parent, T child,
			Function<TableDefinition, TableDefinition> tableDefinitionConfiguration) {

		Select nodeParent = findUltimateNodeParent(parent);

		List<Select> nodeParentChain = collectNodeParents(nodeParent);

		AnalyticJoin newNode = new AnalyticJoin(nodeParent, createTable(child, tableDefinitionConfiguration));

		if (nodeParentChain.isEmpty()) {
			nodeRoot = newNode;
		} else {
			Select oldNode = nodeParentChain.get(0);
			if (oldNode instanceof AnalyticJoin aj) {
				aj.setChild(newNode);
			}

		}
		return this;
	}

	AnalyticStructureBuilder<T, C> addSingleChildTo(T parent, T child,
			Function<TableDefinition, TableDefinition> tableDefinitionConfiguration) {

		Select nodeParent = findUltimateNodeParent(parent);

		List<Select> nodeParentChain = collectNodeParents(nodeParent);

		AnalyticJoin newNode = new AnalyticJoin(nodeParent, createTable(child, tableDefinitionConfiguration), SINGLE);

		if (nodeParentChain.isEmpty()) {
			nodeRoot = newNode;
		} else {

			Select oldNode = nodeParentChain.get(0);
			if (oldNode instanceof AnalyticJoin aj) {
				aj.setChild(newNode);
			}
		}

		return this;
	}

	/**
	 * collects a list of nodes starting with the direct node parent of the node passed as an argument, going all the way
	 * up to the root.
	 */
	private List<Select> collectNodeParents(Select node) {

		List<Select> result = new ArrayList<>();
		Select nodeParent = nodeParentLookUp.get(node);
		while (nodeParent != null) {
			result.add(nodeParent);
			nodeParent = nodeParentLookUp.get(nodeParent);
		}
		return result;
	}

	List<AnalyticColumn> getId() {
		return nodeRoot.getId();
	}

	/**
	 * Returns the node closest to the root of which the chain build by following the `parent` <i>(Note: not the node
	 * parent)</i> relationship leads to the node passed as an argument. When this node is a join it represents the join
	 * that joins all child elements to this node.
	 */
	private Select findUltimateNodeParent(T parent) {

		Select nodeParent = nodeParentLookUp.get(parent);

		Assert.state(nodeParent != null, "There must be a node parent");
		Assert.state(nodeParent.getParent().equals(parent), "The object in question must be the parent of the node parent");

		return findUltimateNodeParent(nodeParent);
	}

	private Select findUltimateNodeParent(Select node) {

		Select nodeParent = nodeParentLookUp.get(node);

		if (nodeParent == null) {
			return node;
		} else if (!nodeParent.getParent().equals(node)) { // getParent is NOT looking for the node parent, but the parent
																												// in the entity relationship
			return node;
		} else {
			return findUltimateNodeParent(nodeParent);
		}
	}

	private TableDefinition createTable(T table,
			Function<TableDefinition, TableDefinition> tableDefinitionConfiguration) {
		return tableDefinitionConfiguration.apply(new TableDefinition(table));
	}

	Select getSelect() {
		return nodeRoot;
	}

	abstract class Select {

		abstract List<? extends AnalyticColumn> getColumns();

		@Nullable
		abstract List<AnalyticColumn> getId();

		abstract List<Select> getFroms();

		abstract Object getParent();

		protected AnalyticColumn getRowNumber() {
			return new Literal(1);
		}
	}

	abstract class SingleTableSelect extends Select {

		abstract List<ForeignKey> getForeignKey();
	}

	class TableDefinition extends SingleTableSelect {

		private final T table;
		private AnalyticColumn id;
		private List<AnalyticColumn> columns;
		private List<ForeignKey> foreignKey = new ArrayList<>();
		private BaseColumn keyColumn;

		TableDefinition(T table, @Nullable AnalyticColumn id, List<? extends AnalyticColumn> columns, ForeignKey foreignKey,
				BaseColumn keyColumn) {

			this.table = table;
			this.id = id;
			if (foreignKey != null) {
				this.foreignKey.add(foreignKey);
			}
			this.columns = Collections.unmodifiableList(columns);
			this.keyColumn = keyColumn;

			nodeParentLookUp.put(table, this);
		}

		TableDefinition(T table) {
			this(table, null, Collections.emptyList(), null, null);
		}

		TableDefinition withId(C id) {

			this.id = new BaseColumn(id);
			return this;
		}

		TableDefinition withColumns(C... columns) {

			this.columns = new ArrayList<>(this.columns);
			Arrays.stream(columns).map(BaseColumn::new).forEach(bc -> this.columns.add(bc));
			return this;
		}

		TableDefinition withForeignKey(ForeignKey foreignKey) {

			foreignKey.setOwner(this);
			this.foreignKey.add(foreignKey);
			return this;
		}

		TableDefinition withKeyColumn(C key) {
			this.keyColumn = new BaseColumn(key);
			return this;
		}

		@Override
		List<ForeignKey> getForeignKey() {
			return foreignKey;
		}

		@Override
		public List<? extends AnalyticColumn> getColumns() {

			List<AnalyticColumn> allColumns = new ArrayList<>(columns);
			if (id != null) {
				allColumns.add(id);
			}
			allColumns.addAll(foreignKey);
			if (keyColumn != null) {
				allColumns.add(keyColumn);
			}

			return allColumns;
		}

		@Override
		public List<AnalyticColumn> getId() {
			if (id == null) {
				List<AnalyticColumn> derivedKeys = new ArrayList<>();
				derivedKeys.addAll(foreignKey);
				if (keyColumn != null) {
					derivedKeys.add(keyColumn);
				}
				return derivedKeys;
			}
			return Collections.singletonList(id);
		}

		@Override
		List<Select> getFroms() {
			return Collections.emptyList();
		}

		@Override
		Object getParent() {
			return table;
		}

		T getTable() {
			return table;
		}

		@Override
		public String toString() {
			return "TD{" + table + '}';
		}

	}

	class AnalyticJoin extends Select {

		private final Select parent;
		private Max rowNumber;
		private Select child;
		private final List<AnalyticColumn> columnsFromJoin = new ArrayList();
		private final Multiplicity multiplicity;

		private final List<JoinCondition> conditions = new ArrayList<>();

		AnalyticJoin(Select parent, Select child, Multiplicity multiplicity) {

			this.parent = unwrapParent(parent);

			TableDefinition td = extractTableDefinition(child);
			if (td != null) {

				if (parent.getId().isEmpty()) {
					throw new IllegalStateException("a child element without key or id can't have further children");
				} else {

					List<AnalyticColumn> idColumns = parent.getId();
					List<ForeignKey> foreignKeys = new ArrayList<>();
					for (AnalyticColumn id : idColumns) {

						Assert.notNull(id, "id must not be null");
						ForeignKey foreignKey = new ForeignKey(id);
						foreignKeys.add(foreignKey);
						conditions.add(new JoinCondition(id, foreignKey));
						td.withForeignKey(foreignKey);

						Max maxId = new Max(id, foreignKey);
						columnsFromJoin.add(maxId);
					}
					AnalyticColumn parentRowNumber = parent.getRowNumber();
					RowNumber rowNumber = new RowNumber(foreignKeys);
					columnsFromJoin.add(rowNumber);
					conditions.add(new JoinCondition(parentRowNumber, rowNumber));

					Max maxRowNumber = new Max(parentRowNumber, rowNumber);
					this.rowNumber = maxRowNumber;
					columnsFromJoin.add(maxRowNumber);
				}
			}

			this.child = wrapChildInView(child);

			this.multiplicity = multiplicity;

			nodeParentLookUp.put(this.parent, this);
			nodeParentLookUp.put(this.child, this);

		}

		AnalyticJoin(Select parent, Select child) {
			this(parent, child, MULTIPLE);
		}

		private Select unwrapParent(Select node) {

			if (node instanceof AnalyticView) {
				return (Select) node.getParent();
			}
			return node;
		}

		@Nullable
		private TableDefinition extractTableDefinition(Select select) {

			if (select instanceof TableDefinition td) {
				return td;
			}

			if (select instanceof AnalyticView av) {
				return (TableDefinition) av.getFroms().get(0);
			}

			return null;
		}

		private Select wrapChildInView(Select node) {

			if (node instanceof TableDefinition td) {
				return new AnalyticView(td);
			}
			return node;
		}

		@Override
		public List<? extends AnalyticColumn> getColumns() {

			List<AnalyticColumn> result = new ArrayList<>();
			parent.getColumns().forEach(c -> result.add(new DerivedColumn(c)));
			child.getColumns().forEach(c -> result.add(new DerivedColumn(c)));
			columnsFromJoin.forEach(c -> result.add(new DerivedColumn(c)));

			return result;
		}

		@Override
		public List<AnalyticColumn> getId() {
			return parent.getId().stream().map(column -> (AnalyticColumn) new DerivedColumn(column)).toList();
		}

		@Override
		List<Select> getFroms() {
			return asList(parent, child);
		}

		@Override
		Select getParent() {
			return parent;
		}

		Select getChild() {
			return child;
		}

		@Override
		protected AnalyticColumn getRowNumber() {
			return rowNumber != null ? rowNumber : super.getRowNumber();
		}

		@Override
		public String toString() {
			String prefix = "AJ {" + parent + ", " + child + ", ";

			String conditionString = String.join(", ",
					conditions.stream().map(jc -> "eq(" + jc.left + ", " + jc.right + ")").toList());
			conditionString = conditionString.isEmpty() ? "<no-condition>" : conditionString;
			return prefix + conditionString + '}';
		}

		void setChild(AnalyticJoin newChild) {

			nodeParentLookUp.put(newChild, this);
			this.child = newChild;
		}

		public List<JoinCondition> getConditions() {
			return conditions;
		}
	}

	enum Multiplicity {
		SINGLE, MULTIPLE
	}

	class JoinCondition {

		private final AnalyticColumn left;
		private final AnalyticColumn right;

		JoinCondition(AnalyticColumn left, AnalyticColumn right) {

			this.left = left;
			this.right = right;
		}

		AnalyticColumn getLeft() {
			return left;
		}

		AnalyticColumn getRight() {
			return right;
		}
	}

	class AnalyticView extends SingleTableSelect {

		private final TableDefinition table;

		AnalyticView(TableDefinition table) {

			this.table = table;

			nodeParentLookUp.put(table, this);

		}

		@Override
		List<? extends AnalyticColumn> getColumns() {
			return table.getColumns();
		}

		@Override
		List<AnalyticColumn> getId() {
			return table.getId();
		}

		@Override
		List<Select> getFroms() {
			return Collections.singletonList(table);
		}

		@Override
		Object getParent() {
			return table;
		}

		@Override
		public String toString() {
			return "AV{" + table + '}';
		}

		@Override
		List<ForeignKey> getForeignKey() {
			return table.getForeignKey();
		}
	}

	abstract class AnalyticColumn {
		abstract C getColumn();
	}

	class BaseColumn extends AnalyticColumn {

		final C column;

		BaseColumn(C column) {
			this.column = column;
		}

		@Override
		C getColumn() {
			return column;
		}

		@Override
		public String toString() {
			return column.toString();
		}
	}

	class DerivedColumn extends AnalyticColumn {

		final AnalyticColumn column;

		DerivedColumn(AnalyticColumn column) {

			Assert.notNull(column, "Can't create a derived column for null");

			this.column = column;
		}

		@Override
		C getColumn() {
			return column.getColumn();
		}

		AnalyticColumn getBase() {
			return column;
		}

		@Override
		public String toString() {
			return column.toString();
		}
	}

	class RowNumber extends AnalyticColumn {
		private final List<? extends AnalyticColumn> partitionBy;

		RowNumber(List<? extends AnalyticColumn> partitionBy) {

			this.partitionBy = partitionBy;
		}

		RowNumber(AnalyticColumn... partitionBy) {

			this.partitionBy = Arrays.asList(partitionBy);
		}

		@Override
		C getColumn() {
			return null;
		}

		@Override
		public String toString() {
			return "RN(" + partitionBy.stream().map(Object::toString).collect(Collectors.joining(", ")) + ')';
		}

		public List<? extends AnalyticColumn> getPartitionBy() {
			return partitionBy;
		}
	}

	class ForeignKey extends AnalyticColumn {

		final AnalyticColumn column;
		private TableDefinition owner;

		ForeignKey(AnalyticColumn column) {
			this.column = column;
		}

		@Override
		C getColumn() {
			return column.getColumn();
		}

		AnalyticColumn getForeignKeyColumn() {
			return column;
		}

		void setOwner(TableDefinition owner) {
			this.owner = owner;
		}

		TableDefinition getOwner() {
			return owner;
		}

		@Override
		public String toString() {
			return "FK(" + owner.getTable() + ", " + column + ')';
		}
	}

	class Max extends AnalyticColumn {

		final AnalyticColumn left;
		final AnalyticColumn right;

		Max(AnalyticColumn left, AnalyticColumn right) {

			this.left = left;
			this.right = right;
		}

		@Override
		C getColumn() {
			return null;
		}

		AnalyticColumn getLeft() {
			return left;
		}

		AnalyticColumn getRight() {
			return right;
		}

		@Override
		public String toString() {
			return "Max(" + left + ", " + right + ')';
		}
	}

	class Literal extends AnalyticColumn {

		private final Object value;

		Literal(Object value) {
			this.value = value;
		}

		public Object getValue() {
			return value;
		}

		@Override
		public String toString() {
			return "lit(" + value + ')';
		}

		@Override
		C getColumn() {
			return null;
		}
	}
}
