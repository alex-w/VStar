/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2016  AAVSO (http://www.aavso.org/)
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
package org.aavso.tools.vstar.input.database;

import junit.framework.TestCase;

import org.aavso.tools.vstar.ui.mediator.StarInfo;

/**
 * VSX web service unit tests.
 */
public class VSXWebServiceStarInfoSourceTest extends TestCase {

	private VSXWebServiceStarInfoSource source;
	
	public VSXWebServiceStarInfoSourceTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		source = new VSXWebServiceStarInfoSource();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	// By AUID
	
	public void testGetStarByAUIDRCar() throws Exception {
		StarInfo info = source.getStarByAUID("000-BBQ-500");
		assertEquals("R Car", info.getDesignation());
	}

	public void testGetStarByAUIDEpsAur() throws Exception {
		StarInfo info = source.getStarByAUID("000-BCT-905");
		assertEquals("eps Aur", info.getDesignation());
	}

	// By name
	
//	public void testGetStarByNameRCar() throws Exception {
//		StarInfo info = source.getStarByName("R Car");
//		assertEquals("000-BBQ-500", info.getAuid());
//	}
	
	public void testGetStarByNameEpsAur() throws Exception {
		StarInfo info = source.getStarByName("eps Aur");
		assertEquals("000-BCT-905", info.getAuid());
	}
	
	public void testGetStarByNameMasterOT() throws Exception {
		StarInfo info = source.getStarByName("MASTER OT J131320.24+692649.1");
		assertEquals("000-BKZ-417", info.getAuid());
	}
}
