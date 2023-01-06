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

import java.util.Arrays;
import java.util.List;

/**
 * @author Jens Schauder
 */
class RowNumberPattern implements Pattern {
	private final Pattern[] partitionBy;

	public RowNumberPattern(Pattern ... partitionBy) {
		this.partitionBy = partitionBy;
	}

	static RowNumberPattern rn(Pattern ... partitionBy) {
		return new RowNumberPattern(partitionBy);
	}

	@Override
	public boolean matches(AnalyticStructureBuilder<?, ?>.Select select, AnalyticStructureBuilder<?, ?>.AnalyticColumn actualColumn) {

		AnalyticStructureBuilder.RowNumber rn = extractRn(actualColumn);
		if (rn == null) {
			return false;
		}
		List<? extends AnalyticStructureBuilder.AnalyticColumn> actualPartitionBy = rn.getPartitionBy();

		if (partitionBy.length != actualPartitionBy.size())

		for (int i = 0; i < partitionBy.length; i++) {
			if (!(partitionBy[i].matches(select, actualPartitionBy.get(i)))) {
				return false;
			}
		}

		return true;
	}

	@Nullable
	private AnalyticStructureBuilder.RowNumber extractRn(AnalyticStructureBuilder<?, ?>.AnalyticColumn other) {
		return AnalyticStructureBuilderSelectAssert.extract(AnalyticStructureBuilder.RowNumber.class, other);
	}

	@Override
	public String render() {
		return "RN(" + Arrays.toString(partitionBy) + ")";
	}

	@Override
	public String toString() {
		return render();
	}
}
