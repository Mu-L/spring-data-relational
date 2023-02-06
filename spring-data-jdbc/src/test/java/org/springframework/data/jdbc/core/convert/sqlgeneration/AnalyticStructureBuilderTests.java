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

import static org.springframework.data.jdbc.core.convert.sqlgeneration.AnalyticAssertions.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.AnalyticJoinPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.AnalyticViewPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.ConditionPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.ForeignKeyPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.LiteralPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.MaxPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.RowNumberPattern.*;
import static org.springframework.data.jdbc.core.convert.sqlgeneration.TableDefinitionPattern.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class AnalyticStructureBuilderTests {

	/**
	 * A simple table should result in a simple select. Columns are represented by
	 * {@link org.springframework.data.jdbc.core.convert.sqlgeneration.AnalyticStructureBuilder.BaseColumn} since they are
	 * directly referenced.
	 */
	@Test
	void simpleTableWithColumns() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>().addTable("person",
				td -> td.withId("person_id").withColumns("value", "lastname"));

		assertThat(builder).hasExactColumns("person_id", "value", "lastname") //
				.hasId("person_id") //
				.hasStructure(td("person"));

	}

	@Test
	void simpleTableWithColumnsAddedInMultipleSteps() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>().addTable("person",
				td -> td.withId("person_id").withColumns("value").withColumns("lastname"));

		assertThat(builder).hasExactColumns("person_id", "value", "lastname") //
				.hasId("person_id") //
				.hasStructure(td("person"));

	}

	@Test
	void tableWithSingleChild() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
				.addTable("parent", td -> td.withId("parentId").withColumns("parent-value", "parent-lastname"))
				.addChildTo("parent", "child", td -> td.withColumns("child-value", "child-lastname"));

		assertThat(builder).hasExactColumns( //
				"parentId", "parent-value", "parent-lastname", //
				"child-value", "child-lastname", //
				fk("child", "parentId"), //
				max("parentId", fk("child", "parentId")), //
				rn(fk("child", "parentId")), //
				max(lit(1), rn(fk("child", "parentId"))) //
		).hasId("parentId") //
				.hasStructure(aj(td("parent"), av(td("child")), //
						eq("parentId", fk("child", "parentId")), // <-- should fail due to wrong column value
						eq(lit(1), rn(fk("child", "parentId"))) //
				));
	}

	@Test
	void tableWithSingleChildWithKey() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
				.addTable("parent", td -> td.withId("parentId").withColumns("parentName", "parentLastname"))
				.addChildTo("parent", "child", td -> td.withColumns("childName", "childLastname").withKeyColumn("childKey"));

		assertThat(builder) //
				.hasExactColumns("parentId", "parentName", "parentLastname", //
						fk("child", "parentId"), //
						max("parentId", fk("child", "parentId")), //
						rn(fk("child", "parentId")), //
						max(lit(1), rn(fk("child", "parentId"))), //
						"childKey", "childName", "childLastname") //
				.hasId("parentId") //
				.hasStructure( //
						aj( //
								td("parent"), //
								av(td("child")), //
								eq("parentId", fk("child", "parentId")), //
								eq(lit(1), rn(fk("child", "parentId"))) //
						) //
				); //
	}

	@Test
	void tableWithMultipleChildren() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
				.addTable("parent", td -> td.withId("parentId").withColumns("parentName", "parentLastname"))
				.addChildTo("parent", "child1", td -> td.withColumns("childName", "childLastname"))
				.addChildTo("parent", "child2", td -> td.withColumns("siblingName", "siblingLastName"));

		AnalyticStructureBuilder<String, String>.Select select = builder.getSelect();

		MaxPattern<LiteralPattern> rnChild1 = max(lit(1), rn(fk("child1", "parentId")));

		assertThat(builder) //
				.hasExactColumns("parentId", "parentName", "parentLastname", //
						fk("child1", "parentId"), //
						max("parentId", fk("child1", "parentId")), //
						rn(fk("child1", "parentId")), //
						rnChild1, //
						"childName", "childLastname", //

						fk("child2", "parentId"), //
						max("parentId", fk("child2", "parentId")), //
						rn(fk("child2", "parentId")), //
						max(rnChild1, rn(fk("child2", "parentId"))), //
						"siblingName", //
						"siblingLastName")
				.hasId("parentId") //
				.hasStructure( //
						aj( //
								aj(td("parent"), av(td("child1")), //
										eq("parentId", fk("child1", "parentId")), //
										eq(lit(1), rn(fk("child1", "parentId"))) //
								), //
								av(td("child2")), //
								eq("parentId", fk("child2", "parentId")), //
								eq(rnChild1, rn(fk("child2", "parentId"))) //
						));
	}

	@Nested
	class TableWithChainOfChildren {

		@Test
		void middleChildHasId() {

			AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
					.addTable("granny", td -> td.withId("grannyId").withColumns("grannyName"))
					.addChildTo("granny", "parent", td -> td.withId("parentId").withColumns("parentName"))
					.addChildTo("parent", "child", td -> td.withColumns("childName"));

			AnalyticStructureBuilder<String, String>.Select select = builder.getSelect();

			assertThat(builder) //
//					.hasExactColumns( //
//							"grannyId", "grannyName", //
//							fk("parent", "grannyId"), //
//							max("grannyId", fk("parent", "grannyId")), //
//							rn(fk("parent", "grannyId")), //
//							max(lit(1), rn(fk("parent", "grannyId"))), //
//							"parentId", "parentName", //
//							fk("child", "parentId"), //
//							max("parentId", fk("child", "parentId")), //
//							rn(fk("child", "parentId")), // <-- not found
//							max(lit(1), rn(fk("child", "parentId"))), // <-- not found
//							"childName" //
//					) //
//					.hasId("grannyId") //
					.hasStructure( //
							aj( //
									td("granny"), //
									aj( //
											td("parent"), //
											av(td("child")), //
											eq("parentId", fk("child", "parentId")), //
											eq(lit(1), rn(fk("child", "parentId"))) //
									), //
									eq("grannyId", fk("parent", "grannyId")), //
									eq(lit(1), rn(fk("parent", "grannyId"))) // TODO: should be: eq(lit(1), max(lit(1),rn(fk("parent", "grannyId")))
							) //
					);
		}

		/**
		 * middle children that don't have an id, nor a key can't referenced by further children
		 */
		@Test
		void middleChildHasNoId() {

			// assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
			AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
					.addTable("granny", td -> td.withId("grannyId").withColumns("grannyName"))
					.addChildTo("granny", "parent", td -> td.withColumns("parentName"))
					.addChildTo("parent", "child", td -> td.withColumns("childName"));

			builder.getSelect();
			// }); // TODO: this should throw an exception.
		}

		@Test
		void middleChildWithKeyHasId() {

			AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
					.addTable("granny", td -> td.withId("grannyId").withColumns("grannyName"))
					.addChildTo("granny", "parent",
							td -> td.withId("parentId").withKeyColumn("parentKey").withColumns("parentName"))
					.addChildTo("parent", "child", td -> td.withColumns("childName"));

			AnalyticStructureBuilder<String, String>.Select select = builder.getSelect();

			assertThat(builder).hasExactColumns( //
					"grannyId", "grannyName", //
					fk("parent", "grannyId"), //
					max("grannyId", fk("parent", "grannyId")), //
					rn(fk("parent", "grannyId")), //
					max(lit(1), rn(fk("parent", "grannyId"))), //
					"parentId", "parentKey", "parentName", //
					fk("child", "parentId"), //
					max("parentId", fk("child", "parentId")), //
					rn(fk("child", "parentId")), //
					max(lit(1), rn(fk("child", "parentId"))), //
					"childName" //
			) //
					.hasId("grannyId") //
					.hasStructure( //
							aj( //
									td("granny"), //
									aj( //
											td("parent"), //
											av(td("child")), //
											eq("parentId", fk("child", "parentId")), //
											eq(lit(1), rn(fk("child", "parentId"))) //
									), //
									eq("grannyId", fk("parent", "grannyId")), //
									eq(lit(1), rn(fk("parent", "grannyId"))) //
							) //
					);
		}

		@Test
		void middleChildWithKeyHasNoId() {

			AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
					.addTable("granny", td -> td.withId("grannyId").withColumns("grannyName"))
					.addChildTo("granny", "parent", td -> td.withColumns("parentName").withKeyColumn("parentKey"))
					.addChildTo("parent", "child", td -> td.withColumns("childName"));

			AnalyticStructureBuilder<String, String>.Select select = builder.getSelect();

			assertThat(builder).hasExactColumns( //
					"grannyId", "grannyName", //
					fk("parent", "grannyId"), //
					max("grannyId", fk("parent", "grannyId")), //
					rn(fk("parent", "grannyId")), //
					max(lit(1), rn(fk("parent", "grannyId"))), //
					"parentKey", "parentName", //

					fk("child", fk("parent", "grannyId")), //
					max(fk("parent", "grannyId"), fk("child", fk("parent", "grannyId"))), //
					fk("child", "parentKey"), //
					max("parentKey", fk("child", "parentKey")), //
					rn(fk("child", fk("parent", "grannyId")), fk("child", "parentKey")), //
					max(lit(1), rn(fk("child", fk("parent", "grannyId")), fk("child", "parentKey"))), //
					"childName" //
			) //
					.hasId("grannyId") //
					.hasStructure( //
							aj( //
									td("granny"), //
									aj( //
											td("parent"), //
											av(td("child")), //
											eq(fk("parent", "grannyId"), fk("child", fk("parent", "grannyId"))), //
											eq("parentKey", fk("child", "parentKey")), //
											eq(lit(1), rn(fk("child", fk("parent", "grannyId")), fk("child", "parentKey"))) //
									), //
									eq("grannyId", fk("parent", "grannyId")), //
									eq(lit(1), rn(fk("parent", "grannyId"))) //
							) //
					);
		}

		@Test
		void middleSingleChildHasId() {

			AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
					.addTable("granny", td -> td.withId("grannyId").withColumns("grannyName"))
					.addSingleChildTo("granny", "parent", td -> td.withId("parentId").withColumns("parentName"))
					.addChildTo("parent", "child", td -> td.withColumns("childName"));

			AnalyticStructureBuilder<String, String>.Select select = builder.getSelect();

			assertThat(builder).hasExactColumns( //
					"grannyId", "grannyName", //
					fk("parent", "grannyId"), //
					max("grannyId", fk("parent", "grannyId")), //
					rn(fk("parent", "grannyId")), //
					max(lit(1), rn(fk("parent", "grannyId"))), //
					"parentId", "parentName", //
					fk("child", "parentId"), //
					max("parentId", fk("child", "parentId")), //
					rn(fk("child", "parentId")), //
					max(lit(1), rn(fk("child", "parentId"))), //
					"childName" //
			) //
					.hasId("grannyId") //
					.hasStructure( //
							aj( //
									td("granny"), //
									aj( //
											td("parent"), //
											av(td("child")), //
											eq("parentId", fk("child", "parentId")), //
											eq(lit(1), rn(fk("child", "parentId"))) //
									), //
									eq("grannyId", fk("parent", "grannyId")), //
									eq(lit(1), rn(fk("parent", "grannyId"))) //
							) //
					);
		}

		@Test
		void middleSingleChildHasNoId() {

			AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
					.addTable("granny", td -> td.withId("grannyId").withColumns("grannyName"))
					.addSingleChildTo("granny", "parent", td -> td.withColumns("parentName"))
					.addChildTo("parent", "child", td -> td.withColumns("childName"));

			AnalyticStructureBuilder.Select select = builder.getSelect();

			assertThat(builder).hasExactColumns( //
					"grannyId", "grannyName", //
					fk("parent", "grannyId"), //
					max("grannyId", fk("parent", "grannyId")), // TODO: max is superfluous for single
					rn(fk("parent", "grannyId")), //
					max(lit(1), rn(fk("parent", "grannyId"))), //
					"parentName", //
					fk("child", fk("parent", "grannyId")), //
					max(fk("parent", "grannyId"), fk("child", fk("parent", "grannyId"))), //
					rn(fk("child", fk("parent", "grannyId"))), //
					max(lit(1), rn(fk("child", fk("parent", "grannyId")))), //
					"childName") //
					.hasId("grannyId") //
					.hasStructure( //
							aj( //
									td("granny"), //
									aj( //
											td("parent"), //
											av(td("child")), //
											eq(fk("parent", "grannyId"), fk("child", fk("parent", "grannyId"))), //
											eq(lit(1), rn(fk("child", fk("parent", "grannyId")))) //
									), //
									eq("grannyId", fk("parent", "grannyId")), //
									eq(lit(1), rn(fk("parent", "grannyId"))) //
							) //
					);
		}

	}

	@Test
	void mediumComplexHierarchy() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
				.addTable("customer", td -> td.withId("customerId").withColumns("customerName"));
		builder.addChildTo("customer", "address", td -> td.withId("addressId").withColumns("addressName"));
		builder.addChildTo("address", "city", td -> td.withColumns("cityName"));
		builder.addChildTo("customer", "order", td -> td.withId("orderId").withColumns("orderName"));
		builder.addChildTo("address", "type", td -> td.withColumns("typeName"));

		MaxPattern<LiteralPattern> rnOrder = max(lit(1), rn(fk("address", "customerId")));
		MaxPattern<LiteralPattern> rnCity = max(lit(1), rn(fk("city", "addressId")));
		assertThat(builder).hasExactColumns( //
				"customerId", "customerName", //
				fk("address", "customerId"), //
				max("customerId", fk("address", "customerId")), //
				rn(fk("address", "customerId")), //
				rnOrder, //
				"addressId", "addressName", // <--

				fk("city", "addressId"), //
				max("addressId", fk("city", "addressId")), //
				rn(fk("city", "addressId")), //
				rnCity, //
				"cityName", //

				fk("order", "customerId"), //
				max("customerId", fk("order", "customerId")), //
				rn(fk("order", "customerId")), //
				max(rnOrder, rn(fk("order", "customerId"))), //
				"orderId", "orderName", //

				fk("type", "addressId"), //
				max("addressId", fk("type", "addressId")), //
				rn(fk("type", "addressId")), //
				max(rnCity, rn(fk("type", "addressId"))), //
				"typeName"//
		).hasId("customerId") //
				.hasStructure( //
						aj( //
								aj( //
										td("customer"), //
										aj( //
												aj( //
														td("address"), //
														av(td("city")), //
														eq("addressId", fk("city", "addressId")), //
														eq(lit(1), rn(fk("city", "addressId"))) //
												), //
												av(td("type")), //
												eq("addressId", fk("type", "addressId")), //
												eq(rnCity, rn(fk("type", "addressId"))) //
										), //
										eq("customerId", fk("address", "customerId")), //
										eq(lit(1), rn(fk("address", "customerId"))) //
								), //
								av(td("order")), //
								eq("customerId", fk("order", "customerId")), //
								eq(rnOrder, rn(fk("order", "customerId"))) //
						) //
				);

	}

	@Test
	void mediumComplexHierarchy2() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
				.addTable("customer", td -> td.withId("customerId").withColumns("customerName"));
		builder.addChildTo("customer", "keyAccount", td -> td.withId("keyAccountId").withColumns("keyAccountName"));
		builder.addChildTo("keyAccount", "assistant", td -> td.withColumns("assistantName"));
		builder.addChildTo("customer", "order", td -> td.withId("orderId").withColumns("orderName"));
		builder.addChildTo("keyAccount", "office", td -> td.withColumns("officeName"));
		builder.addChildTo("order", "item", td -> td.withId("itemId").withColumns("itemName"));

		assertThat(builder).hasStructure( //
				aj( //
						aj( //
								td("customer"), //
								aj( //
										aj( //
												td("keyAccount"), //
												av(td("assistant")), //
												eq("keyAccountId", fk("assistant", "keyAccountId")), //
												eq(lit(1), rn(fk("assistant", "keyAccountId"))) //
										), //
										av(td("office")), //
										eq("keyAccountId", fk("office", "keyAccountId")), //
										eq(max(lit(1), rn(fk("assistant", "keyAccountId"))), rn(fk("office", "keyAccountId"))) //
								), //
								eq("customerId", fk("keyAccount", "customerId")), //
								eq(lit(1), rn(fk("keyAccount", "customerId"))) //
						), //
						aj( //
								td("order"), //
								av(td("item")), //
								eq("orderId", fk("item", "orderId")), //
								eq(lit(1), rn(fk("item", "orderId"))) //
						), //
						eq("customerId", fk("order", "customerId")), //
						eq(max(lit(1), rn(fk("keyAccount", "customerId"))), rn(fk("order", "customerId"))) //
				) //
		);

	}

	@Test
	void complexHierarchy() {

		AnalyticStructureBuilder<String, String> builder = new AnalyticStructureBuilder<String, String>()
				.addTable("customer", td -> td.withId("customerId").withColumns("customerName"));
		builder.addChildTo("customer", "keyAccount", td -> td.withId("keyAccountId").withColumns("keyAccountName"));
		builder.addChildTo("keyAccount", "assistant", td -> td.withColumns("assistantName"));
		builder.addChildTo("customer", "order", td -> td.withId("orderId").withColumns("orderName"));
		builder.addChildTo("keyAccount", "office", td -> td.withId("officeId").withColumns("officeName"));
		builder.addChildTo("order", "item", td -> td.withColumns("itemName"));
		builder.addChildTo("order", "shipment", td -> td.withColumns("shipmentName"));
		builder.addChildTo("office", "room", td -> td.withColumns("roomNumber"));

		MaxPattern<LiteralPattern> rnKeyAccount = max(lit(1), rn(fk("keyAccount", "customerId")));
		MaxPattern<LiteralPattern> rnAssistant = max(lit(1), rn(fk("assistant", "keyAccountId")));

		MaxPattern<LiteralPattern> rnItem = max(lit(1), rn(fk("item", "orderId")));
		assertThat(builder).hasExactColumns( //
				"customerId", "customerName", //
				fk("keyAccount", "customerId"), //
				max("customerId", fk("keyAccount", "customerId")), //
				rn(fk("keyAccount", "customerId")), //
				rnKeyAccount, //
				"keyAccountId", "keyAccountName", //

				fk("assistant", "keyAccountId"), //
				max("keyAccountId", fk("assistant", "keyAccountId")), //
				rn(fk("assistant", "keyAccountId")), //
				rnAssistant, //
				"assistantName", //

				fk("office", "keyAccountId"), //
				max("keyAccountId", fk("office", "keyAccountId")), //
				rn(fk("office", "keyAccountId")), //
				max(rnAssistant, rn(fk("office", "keyAccountId"))), //
				"officeName", //

				fk("order", "customerId"), //
				max("customerId", fk("order", "customerId")), //
				rn(fk("order", "customerId")), //
				max(rnKeyAccount, rn(fk("order", "customerId"))), //
				"orderId", "orderName", //

				fk("item", "orderId"), //
				max("orderId", fk("item", "orderId")), //
				rn(fk("item", "orderId")), //
				rnItem, //
				"itemName", //

				fk("shipment", "orderId"), //
				max("orderId", fk("shipment", "orderId")), //
				rn(fk("shipment", "orderId")), //
				max(rnItem, rn(fk("shipment", "orderId"))), //
				"shipmentName", "officeId", //

				fk("room", "officeId"), //
				max("officeId", fk("room", "officeId")), //
				rn(fk("room", "officeId")), //
				max(lit(1), rn(fk("room", "officeId"))), //
				"roomNumber" //
		).hasId("customerId") //
				.hasStructure( //
						aj( //
								aj( //
										td("customer"), //
										aj( //
												aj( //
														td("keyAccount"), //
														av(td("assistant")), //
														eq("keyAccountId", fk("assistant", "keyAccountId")), //
														eq(lit(1), rn(fk("assistant", "keyAccountId"))) //
												), //
												aj( //
														td("office"), //
														av(td("room")), //
														eq("officeId", fk("room", "officeId")), //
														eq(lit(1), rn(fk("room", "officeId"))) //
												), //
												eq("keyAccountId", fk("office", "keyAccountId")), //
												eq(rnAssistant, rn(fk("office", "keyAccountId"))) //
										), //
										eq("customerId", fk("keyAccount", "customerId")), //
										eq(lit(1), rn(fk("keyAccount", "customerId"))) //
								), //
								aj( //
										aj( //
												td("order"), //
												av(td("item")), //
												eq("orderId", fk("item", "orderId")), //
												eq(lit(1), rn(fk("item", "orderId"))) //
										), //
										av(td("shipment")), //
										eq("orderId", fk("shipment", "orderId")), //
										eq(rnItem, rn(fk("shipment", "orderId"))) //
								), //
								eq("customerId", fk("order", "customerId")), //
								eq(rnKeyAccount, rn(fk("order", "customerId"))) //
						) //
				);

	}

	// TODO: Joins must contain the fields to join on:
	// - rownumber

	private Set<AnalyticStructureBuilder<String, Integer>.TableDefinition> collectFroms(
			AnalyticStructureBuilder<String, Integer>.Select select) {

		Set<AnalyticStructureBuilder<String, Integer>.TableDefinition> froms = new HashSet<>();
		select.getFroms().forEach(s -> {
			if (s instanceof AnalyticStructureBuilder.AnalyticJoin) {
				froms.addAll(collectFroms(s));
			} else if (s instanceof AnalyticStructureBuilder.AnalyticView) {

				froms.add((AnalyticStructureBuilder.TableDefinition) s.getFroms().get(0));
			} else {
				froms.add((AnalyticStructureBuilder.TableDefinition) s);
			}
		});

		return froms;
	}

}
