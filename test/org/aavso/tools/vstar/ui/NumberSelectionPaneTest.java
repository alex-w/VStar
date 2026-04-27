/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2010  AAVSO (http://www.aavso.org/)
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
package org.aavso.tools.vstar.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.swing.JFrame;
import javax.swing.JSpinner;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JSpinnerFixture;
import org.assertj.swing.junit.runner.GUITestRunner;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * AssertJ Swing demonstrator test for {@link NumberSelectionPane}.
 *
 * Spike for issue #579, prong B: prove that VStar can host real GUI tests
 * (Swing-on-EDT, headlessly under {@code xvfb}) and that they integrate
 * cleanly with the existing Ant + JaCoCo + JUnit pipeline.
 *
 * If the framework or display prerequisites are not met, this test fails
 * (rather than silently skipping) so the CI loudly surfaces regressions in
 * the GUI-test plumbing itself.
 */
@RunWith(GUITestRunner.class)
public class NumberSelectionPaneTest extends AssertJSwingJUnitTestCase {

	private FrameFixture window;
	private NumberSelectionPane pane;

	@Override
	protected void onSetUp() {
		JFrame frame = GuiActionRunner.execute(() -> {
			JFrame f = new JFrame("NumberSelectionPaneTest");
			pane = new NumberSelectionPane(
					"Value", 0.0, 10.0, 0.5, 2.5, "0.00");
			f.add(pane);
			f.pack();
			return f;
		});
		window = new FrameFixture(robot(), frame);
		window.show();
	}

	@Test
	public void initialValueReportedByGetValue() {
		assertEquals(2.5, pane.getValue(), 0.0);
	}

	@Test
	public void incrementAdvancesValueByConfiguredStep() {
		spinnerFixture().increment();

		assertEquals(3.0, pane.getValue(), 0.0);
	}

	@Test
	public void decrementRetreatsValueByConfiguredStep() {
		spinnerFixture().decrement();

		assertEquals(2.0, pane.getValue(), 0.0);
	}

	@Test
	public void invertedRangeIsRejected() {
		try {
			GuiActionRunner.execute(() ->
					new NumberSelectionPane(
							"X", 5.0, 1.0, 0.1, 3.0, "0.0"));
			fail("expected IllegalArgumentException for max < min");
		} catch (RuntimeException expected) {
			assertTrue(
					"Underlying cause should be IllegalArgumentException, was "
							+ rootCauseOf(expected),
					rootCauseOf(expected) instanceof IllegalArgumentException);
		}
	}

	@Test
	public void initialOutsideRangeIsRejected() {
		try {
			GuiActionRunner.execute(() ->
					new NumberSelectionPane(
							"X", 0.0, 10.0, 1.0, 50.0, "0.0"));
			fail("expected IllegalArgumentException for initial outside range");
		} catch (RuntimeException expected) {
			assertTrue(
					"Underlying cause should be IllegalArgumentException, was "
							+ rootCauseOf(expected),
					rootCauseOf(expected) instanceof IllegalArgumentException);
		}
	}

	private JSpinnerFixture spinnerFixture() {
		return window.spinner(new GenericTypeMatcher<JSpinner>(JSpinner.class) {
			@Override
			protected boolean isMatching(JSpinner s) {
				return true;
			}
		});
	}

	private static Throwable rootCauseOf(Throwable t) {
		Throwable current = t;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}
}
