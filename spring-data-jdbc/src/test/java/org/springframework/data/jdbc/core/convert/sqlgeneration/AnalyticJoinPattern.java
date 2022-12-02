package org.springframework.data.jdbc.core.convert.sqlgeneration;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.util.Strings;

public record AnalyticJoinPattern(StructurePattern left, StructurePattern right,
		ConditionPattern[] conditions) implements StructurePattern {

	static AnalyticJoinPattern aj(StructurePattern left, StructurePattern right, ConditionPattern... conditions) {
		return new AnalyticJoinPattern(left, right, conditions);
	}

	@Override
	public boolean matches(AnalyticStructureBuilder<?, ?>.Select select) {

		if (select instanceof AnalyticStructureBuilder.AnalyticJoin join) {
			return tablesMatch(join) && conditionsMatch(select, join);
		}
		return false;
	}

	private boolean tablesMatch(AnalyticStructureBuilder.AnalyticJoin join) {
		return left().matches(join.getParent()) && right().matches(join.getChild());
	}

	private boolean conditionsMatch(AnalyticStructureBuilder<?, ?>.Select select, AnalyticStructureBuilder.AnalyticJoin join) {

		List<AnalyticStructureBuilder.JoinCondition> actualConditions = new ArrayList<>(join.getConditions());
		List<ConditionPattern> expectedConditions = new ArrayList<>(Arrays.stream(conditions).toList());
		for (ConditionPattern expected : conditions) {
			for (AnalyticStructureBuilder.JoinCondition actual : actualConditions) {
				if (expected.matches(select, actual.getLeft(), actual.getRight())) {
					expectedConditions.remove(expected);
					actualConditions.remove(actual);
					break;
				}
			}
		}

		return actualConditions.isEmpty() && expectedConditions.isEmpty();
	}

	@Override
	public String toString() {
		String prefix = "AJ{" + left + ", " + right + ", ";

		String condition = Strings.join(Arrays.stream(conditions).map(cp -> cp.toString()).toList()).with(", ");
		condition = condition.isEmpty() ? "<no-condition>" : condition;
		return prefix + condition + '}';
	}

}
