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

import javax.swing.JPanel;

import junit.framework.TestCase;

/**
 * Pure unit test for {@link AbstractOkCancelDialog}.
 *
 * We create a minimal concrete subclass to test the abstract class's
 * behaviour: default cancelled state, setCancelled(), firstUse flag,
 * and button pane creation.
 *
 * Part of issue #579 (prong C): GUI code coverage.
 */
public class AbstractOkCancelDialogTest extends TestCase {

	@SuppressWarnings("serial")
	private static class ConcreteDialog extends AbstractOkCancelDialog {
		boolean okCalled = false;
		boolean cancelCalled = false;

		ConcreteDialog(String title) {
			super(title);
		}

		ConcreteDialog(String title, boolean isModal) {
			super(title, isModal);
		}

		@Override
		protected void okAction() {
			okCalled = true;
		}

		@Override
		protected void cancelAction() {
			cancelCalled = true;
		}
	}

	private ConcreteDialog dialog;

	@Override
	protected void setUp() {
		Locale.setDefault(Locale.ENGLISH);
		dialog = new ConcreteDialog("Test Dialog");
	}

	@Override
	protected void tearDown() {
		if (dialog != null) {
			dialog.dispose();
		}
	}

	public void testIsCancelledByDefault() {
		assertTrue("Dialog should default to cancelled", dialog.isCancelled());
	}

	public void testSetCancelledFalse() {
		dialog.setCancelled(false);
		assertFalse(dialog.isCancelled());
	}

	public void testSetCancelledTrue() {
		dialog.setCancelled(false);
		dialog.setCancelled(true);
		assertTrue(dialog.isCancelled());
	}

	public void testFirstUseIsTrue() {
		assertTrue("firstUse should be true after construction", dialog.firstUse);
	}

	public void testTitle() {
		assertEquals("Test Dialog", dialog.getTitle());
	}

	public void testModalByDefault() {
		assertTrue("Dialog should be modal by default", dialog.isModal());
	}

	public void testNonModalConstruction() {
		ConcreteDialog nonModal = new ConcreteDialog("Non-Modal", false);
		try {
			assertFalse("Dialog should be non-modal", nonModal.isModal());
		} finally {
			nonModal.dispose();
		}
	}

	public void testCreateButtonPaneReturnsPanel() {
		JPanel panel = dialog.createButtonPane();
		assertNotNull("Button pane should not be null", panel);
	}

	public void testOkButtonIsSet() {
		dialog.createButtonPane();
		assertNotNull("okButton should be set after createButtonPane()", dialog.okButton);
	}

	public void testCreateButtonPane2ReturnsPanel() {
		JPanel panel = dialog.createButtonPane2();
		assertNotNull("Button pane 2 should not be null", panel);
	}
}
