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

import junit.framework.TestCase;

/**
 * Pure unit test for {@link DateToJdDialog}.
 *
 * The dialog constructor builds UI fields but does not call
 * {@code setVisible(true)}, so it is safe to construct headlessly.
 * We test the {@code setJD()} / {@code getJD()} round-trip and
 * initial state.
 *
 * Part of issue #579 (prong C): GUI code coverage.
 */
public class DateToJdDialogTest extends TestCase {

	private DateToJdDialog dialog;

	@Override
	protected void setUp() {
		Locale.setDefault(Locale.ENGLISH);
		dialog = new DateToJdDialog("Test");
	}

	@Override
	protected void tearDown() {
		if (dialog != null) {
			dialog.dispose();
		}
	}

	public void testGetJdReturnsNonNullOnFreshDialog() {
		Double jd = dialog.getJD();
		assertNotNull("Freshly constructed dialog should have a JD (current time)", jd);
	}

	public void testRoundTripJ2000Epoch() {
		double expected = 2451545.0;
		dialog.setJD(expected);
		Double actual = dialog.getJD();
		assertNotNull(actual);
		assertEquals(expected, actual, 0.001);
	}

	public void testRoundTripWithFractionalDay() {
		double expected = 2451545.75;
		dialog.setJD(expected);
		Double actual = dialog.getJD();
		assertNotNull(actual);
		assertEquals(expected, actual, 0.001);
	}

	public void testRoundTripOlderDate() {
		double expected = 2436000.5;
		dialog.setJD(expected);
		Double actual = dialog.getJD();
		assertNotNull(actual);
		assertEquals(expected, actual, 0.001);
	}

	public void testIsCancelledByDefault() {
		assertTrue("Dialog should be cancelled by default", dialog.isCancelled());
	}
}
