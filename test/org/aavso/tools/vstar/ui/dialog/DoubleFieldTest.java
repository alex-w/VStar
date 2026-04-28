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
package org.aavso.tools.vstar.ui.dialog;

import java.util.Locale;

import javax.swing.JTextField;

import junit.framework.TestCase;

/**
 * Pure unit test for {@link DoubleField}.
 *
 * {@code DoubleField} extends {@code NumberFieldBase<Double>} and
 * imports {@code javax.swing} (GUI bucket). We test value parsing,
 * range validation, and the {@code setValue()} / {@code getValue()}
 * round-trip.
 *
 * Part of issue #579 (prong C): GUI code coverage.
 */
public class DoubleFieldTest extends TestCase {

	@Override
	protected void setUp() {
		Locale.setDefault(Locale.ENGLISH);
	}

	public void testGetInitialValue() {
		DoubleField field = new DoubleField("Test", null, null, 3.14);
		Double value = field.getValue();
		assertNotNull(value);
		assertEquals(3.14, value, 0.001);
	}

	public void testSetValueAndGetValue() {
		DoubleField field = new DoubleField("Test", null, null, 0.0);
		field.setValue(2.718);
		Double value = field.getValue();
		assertNotNull(value);
		assertEquals(2.718, value, 0.001);
	}

	public void testValueBelowMinReturnsNull() {
		DoubleField field = new DoubleField("Test", 0.0, 100.0, 50.0);
		((JTextField) field.getUIComponent()).setText("-1.0");
		assertNull("Value below min should return null", field.getValue());
	}

	public void testValueAboveMaxReturnsNull() {
		DoubleField field = new DoubleField("Test", 0.0, 100.0, 50.0);
		((JTextField) field.getUIComponent()).setText("101.0");
		assertNull("Value above max should return null", field.getValue());
	}

	public void testValueAtMinBoundary() {
		DoubleField field = new DoubleField("Test", 0.0, 100.0, 50.0);
		((JTextField) field.getUIComponent()).setText("0.0");
		Double value = field.getValue();
		assertNotNull(value);
		assertEquals(0.0, value, 0.001);
	}

	public void testValueAtMaxBoundary() {
		DoubleField field = new DoubleField("Test", 0.0, 100.0, 50.0);
		((JTextField) field.getUIComponent()).setText("100.0");
		Double value = field.getValue();
		assertNotNull(value);
		assertEquals(100.0, value, 0.001);
	}

	public void testNonNumericTextReturnsNull() {
		DoubleField field = new DoubleField("Test", null, null, 0.0);
		((JTextField) field.getUIComponent()).setText("abc");
		assertNull("Non-numeric text should return null", field.getValue());
	}

	public void testEmptyTextReturnsNull() {
		DoubleField field = new DoubleField("Test", null, null, 0.0);
		((JTextField) field.getUIComponent()).setText("");
		assertNull("Empty text should return null", field.getValue());
	}

	public void testNullMinMaxAllowsAnyValue() {
		DoubleField field = new DoubleField("Test", null, null, 0.0);
		((JTextField) field.getUIComponent()).setText("999999.99");
		Double value = field.getValue();
		assertNotNull(value);
		assertEquals(999999.99, value, 0.01);
	}

	public void testSetValueNull() {
		DoubleField field = new DoubleField("Test", null, null, 5.0);
		field.setValue(null);
		assertEquals("", ((JTextField) field.getUIComponent()).getText());
	}

	public void testGetName() {
		DoubleField field = new DoubleField("MyField", null, null, 0.0);
		assertEquals("MyField", field.getName());
	}

	public void testGetMinMax() {
		DoubleField field = new DoubleField("Test", 1.5, 9.5, 5.0);
		assertEquals(1.5, field.getMin(), 0.001);
		assertEquals(9.5, field.getMax(), 0.001);
	}
}
