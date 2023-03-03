/*
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.pubsub.utils;

import java.util.function.IntSupplier;

public final class IntegerOrMax {

	private static final String MAX_STR = "max";
	public static final IntegerOrMax MAX = new IntegerOrMax(0, true);

	public static IntegerOrMax valueOf(String str) {
		if (str == null) {
			return null;
		}

		if (MAX_STR.equals(str)) {
			return MAX;
		}
		return new IntegerOrMax(Integer.parseInt(str), false);
	}

	private final int value;
	private final boolean isMax;

	private IntegerOrMax(int value, boolean isMax) {
		this.value = value;
		this.isMax = isMax;
	}

	public IntegerOrMax(int value) {
		this.value = value;
		this.isMax = false;
	}

	public int getValue() {
		if (isMax) {
			throw new NumberFormatException("It is maximal value which cannot be represented as integer.");
		}
		return value;
	}

	public boolean isMax() {
		return isMax;
	}

	public int getOrMax(int valueForMax) {
		if (isMax) {
			return valueForMax;
		}
		return value;
	}

	public int getOrMaxGet(IntSupplier valueForMaxSupplier) {
		if (isMax) {
			return valueForMaxSupplier.getAsInt();
		}
		return value;
	}

	public Integer getOrNull() {
		if (isMax) {
			return null;
		}
		return value;
	}
	
	public String toString() {
		if (isMax) {
			return "max";
		}
		return String.valueOf(value);
	}
}
