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
package org.aavso.tools.vstar.data;

import org.aavso.tools.vstar.util.Tolerance;

import junit.framework.TestCase;

/**
 * Property class unit tests.
 */
public class PropertyTest extends TestCase {

	public void testIntProp() {
		Property prop = new Property(42);
		assertEquals(Property.propType.INTEGER, prop.getType());
		assertEquals(42, prop.getIntVal());
		assertEquals("42", prop.toString());
		assertEquals(Integer.class, prop.getClazz());
	}

	public void testRealProp() {
		Property prop = new Property(42.0);
		assertEquals(Property.propType.REAL, prop.getType());
		Tolerance.areClose(42.0, prop.getRealVal(), 1e-6, true);
		assertEquals("42.0", prop.toString());
		assertEquals(Double.class, prop.getClazz());
	}
	
	public void testBooleanProp() {
		Property prop = new Property(true);
		assertEquals(Property.propType.BOOLEAN, prop.getType());
		assertEquals(true, prop.getBoolVal());
		assertEquals("true", prop.toString());
		assertEquals(Boolean.class, prop.getClazz());
	}
	
	public void testStringProp() {
		Property prop = new Property("42");
		assertEquals(Property.propType.STRING, prop.getType());
		assertEquals("42", prop.getStrVal());
		assertEquals("42", prop.toString());
		assertEquals(String.class, prop.getClazz());
	}

	public void testNoProp() {
		Property prop = new Property();
		assertEquals(Property.propType.NONE, prop.getType());
		assertEquals(Void.class, prop.getClazz());
	}
	
	public void testCompareInteger() {
		Property prop1 = new Property(42);
		Property prop2 = new Property(42);
		assert prop1.compareTo(prop2) == 0;

		Property prop3 = new Property(21);
		assert prop1.compareTo(prop3) > 0;
		assert prop3.compareTo(prop1) < 0;
	}

	public void testCompareReal() {
		Property prop1 = new Property(42.0);
		Property prop2 = new Property(42.0);
		assert prop1.compareTo(prop2) == 0;

		Property prop3 = new Property(21.0);
		assert prop1.compareTo(prop3) > 0;
		assert prop3.compareTo(prop1) < 0;
	}

	public void testCompareBoolean() {
		Property prop1 = new Property(true);
		Property prop2 = new Property(true);
		assert prop1.compareTo(prop2) == 0;

		Property prop3 = new Property(false);
		assert prop1.compareTo(prop3) > 0;
		assert prop3.compareTo(prop1) < 0;
	}

	public void testCompareString() {
		Property prop1 = new Property("xyz");
		Property prop2 = new Property("xyz");
		assert prop1.compareTo(prop2) == 0;

		Property prop3 = new Property("abc");		
		assert prop1.compareTo(prop3) > 0;
		assert prop3.compareTo(prop1) < 0;
	}
}
