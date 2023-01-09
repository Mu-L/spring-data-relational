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

import static org.assertj.core.api.Assertions.*;

import org.assertj.core.api.AbstractAssert;

/**
 * Assertions for {@link AnalyticStructureBuilder}.
 * 
 * @param <T>
 * @param <C>
 */
public class AnalyticStructureBuilderAssert<T, C>
		extends AbstractAssert<AnalyticStructureBuilderAssert<T, C>, AnalyticStructureBuilder<T, C>> {

	private final AnalyticStructureBuilderSelectAssert selectAssert;

	AnalyticStructureBuilderAssert(AnalyticStructureBuilder<T, C> actual) {

		super(actual, AnalyticStructureBuilderAssert.class);

		selectAssert = new AnalyticStructureBuilderSelectAssert(actual.getSelect());
	}

	AnalyticStructureBuilderAssert<T, C> hasExactColumns(Object... expected) {

		selectAssert.hasExactColumns(expected);
		return this;
	}

	AnalyticStructureBuilderAssert<T, C> hasId(C name) {

		selectAssert.hasId(name);
		return this;
	}

	AnalyticStructureBuilderAssert<T, C> hasStructure(StructurePattern pattern) {

		selectAssert.hasStructure(pattern);
		return this;
	}

}
