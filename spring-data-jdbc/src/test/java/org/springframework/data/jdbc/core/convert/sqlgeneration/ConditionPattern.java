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

class ConditionPattern {

	private final Pattern left;
	private final Pattern right;

	static ConditionPattern eq(Object left, Object right) {

		Pattern leftPattern = left instanceof Pattern lp ? lp : new BasePattern(left);
		Pattern rightPattern = right instanceof Pattern rp ? rp : new BasePattern(right);

		return new ConditionPattern(leftPattern, rightPattern);
	}

	private ConditionPattern(Pattern left, Pattern right) {

		this.left = left;
		this.right = right;
	}

	boolean matches(AnalyticStructureBuilder<?, ?>.Select select, AnalyticStructureBuilder.AnalyticColumn actualLeft,
					AnalyticStructureBuilder.AnalyticColumn actualRight) {

		return left.matches(select, actualLeft) && right.matches(select, actualRight);
	}

	@Override
	public String toString() {
		return "eq(" + left + ", " + right + ")";
	}
}
