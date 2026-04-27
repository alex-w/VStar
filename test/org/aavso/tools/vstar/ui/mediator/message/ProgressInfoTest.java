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
package org.aavso.tools.vstar.ui.mediator.message;

import junit.framework.TestCase;

/**
 * Pure unit tests for {@link ProgressInfo} (no Swing infra needed).
 *
 * Part of the GUI-coverage spike (issue #579, prong B): demonstrates that
 * many "GUI-package" classes are actually plain data carriers that can be
 * verified without a display.
 */
public class ProgressInfoTest extends TestCase {

	public void testTwoArgConstructorCapturesTypeAndNum() {
		ProgressInfo info =
				new ProgressInfo(ProgressType.INCREMENT_PROGRESS, 7);

		assertEquals(ProgressType.INCREMENT_PROGRESS, info.getType());
		assertEquals(7, info.getNum());
	}

	public void testSingleArgConstructorDefaultsNumToZero() {
		ProgressInfo info = new ProgressInfo(ProgressType.START_PROGRESS);

		assertEquals(ProgressType.START_PROGRESS, info.getType());
		assertEquals(0, info.getNum());
	}

	public void testCanonicalSingletonsExposeMatchingTypes() {
		assertEquals(ProgressType.MIN_PROGRESS,
				ProgressInfo.MIN_PROGRESS.getType());
		assertEquals(ProgressType.MAX_PROGRESS,
				ProgressInfo.MAX_PROGRESS.getType());
		assertEquals(ProgressType.START_PROGRESS,
				ProgressInfo.START_PROGRESS.getType());
		assertEquals(ProgressType.COMPLETE_PROGRESS,
				ProgressInfo.COMPLETE_PROGRESS.getType());
		assertEquals(ProgressType.CLEAR_PROGRESS,
				ProgressInfo.CLEAR_PROGRESS.getType());
		assertEquals(ProgressType.BUSY_PROGRESS,
				ProgressInfo.BUSY_PROGRESS.getType());
	}

	public void testIncrementSingletonAdvancesByOne() {
		assertEquals(ProgressType.INCREMENT_PROGRESS,
				ProgressInfo.INCREMENT_PROGRESS.getType());
		assertEquals(1, ProgressInfo.INCREMENT_PROGRESS.getNum());
	}
}
