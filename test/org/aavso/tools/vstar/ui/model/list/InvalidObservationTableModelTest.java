/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2009  AAVSO (http://www.aavso.org/)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.aavso.tools.vstar.ui.model.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aavso.tools.vstar.data.InvalidObservation;

import junit.framework.TestCase;

/**
 * Pure unit test for {@link InvalidObservationTableModel}.
 *
 * Although it lives in {@code ui.model.list}, the model is a plain
 * {@code TableModel}: nothing here actually requires a display, so
 * we exercise it as a regular JUnit 3 test (no AssertJ Swing needed).
 */
public class InvalidObservationTableModelTest extends TestCase {

	private List<InvalidObservation> sample;
	private InvalidObservationTableModel model;

	@Override
	protected void setUp() {
		InvalidObservation a =
				new InvalidObservation("12.3,obs", "bad magnitude");
		a.setRecordNumber(1);
		InvalidObservation b = new InvalidObservation(
				"-,xyz", "missing field", true /* isWarning */);
		b.setRecordNumber(2);

		sample = new ArrayList<InvalidObservation>(Arrays.asList(a, b));
		model = new InvalidObservationTableModel(sample);
	}

	public void testColumnCountIsThree() {
		assertEquals(3, model.getColumnCount());
	}

	public void testColumnNamesAreRecordObservationError() {
		assertEquals("Record", model.getColumnName(0));
		assertEquals("Observation", model.getColumnName(1));
		assertEquals("Error", model.getColumnName(2));
	}

	public void testColumnNameForOutOfRangeIndexIsNull() {
		assertNull(model.getColumnName(99));
	}

	public void testColumnClassesAreIntegerStringString() {
		assertEquals(Integer.class, model.getColumnClass(0));
		assertEquals(String.class, model.getColumnClass(1));
		assertEquals(String.class, model.getColumnClass(2));
	}

	public void testRowCountReflectsBackingList() {
		assertEquals(sample.size(), model.getRowCount());
	}

	public void testValueAtReturnsRecordInputLineAndError() {
		assertEquals(Integer.valueOf(1), model.getValueAt(0, 0));
		assertEquals("12.3,obs", model.getValueAt(0, 1));
		assertEquals("bad magnitude", model.getValueAt(0, 2));

		assertEquals(Integer.valueOf(2), model.getValueAt(1, 0));
		assertEquals("-,xyz", model.getValueAt(1, 1));
		assertEquals("missing field", model.getValueAt(1, 2));
	}

	public void testEmptyListYieldsZeroRows() {
		InvalidObservationTableModel empty =
				new InvalidObservationTableModel(
						new ArrayList<InvalidObservation>());
		assertEquals(0, empty.getRowCount());
	}
}
