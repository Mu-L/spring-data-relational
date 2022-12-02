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

record MaxPattern<C> (Pattern left, Pattern right) implements Pattern {

	public static <C> MaxPattern<C> max(C left, Pattern right) {
		return new MaxPattern(left instanceof Pattern leftPattern ? leftPattern : new BasePattern(left), right);
	}

	@Override
	public boolean matches(AnalyticStructureBuilder<?, ?>.Select select,
			AnalyticStructureBuilder<?, ?>.AnalyticColumn actualColumn) {

		AnalyticStructureBuilder<?, ?>.Max max = extractMax(actualColumn);
		return max != null && left.matches(select, max.left) && right.matches(select, max.right);
	}

	@Override
	public String render() {
		return "MAX(%s, %s)".formatted(left.render(), right.render());
	}

	@Nullable
	private AnalyticStructureBuilder.Max extractMax(AnalyticStructureBuilder<?, ?>.AnalyticColumn other) {
		return AnalyticStructureBuilderAssert.extract(AnalyticStructureBuilder.Max.class, other);
	}

	@Override
	public String toString() {
		return "Max(" + left + ", " + right + ')';
	}
}
