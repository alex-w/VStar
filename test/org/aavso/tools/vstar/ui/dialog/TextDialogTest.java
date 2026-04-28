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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import junit.framework.TestCase;

/**
 * Pure unit test for {@link TextDialog}.
 *
 * Using {@code show=false} avoids calling {@code showDialog()},
 * which would NPE without a UI set in {@code Mediator}.
 *
 * Part of issue #579 (prong C): GUI code coverage.
 */
public class TextDialogTest extends TestCase {

	@Override
	protected void setUp() {
		Locale.setDefault(Locale.ENGLISH);
	}

	public void testSingleFieldInitialValue() {
		TextField f1 = new TextField("Name", "hello", false, false);
		List<ITextComponent<String>> fields = Arrays.<ITextComponent<String>>asList(f1);
		TextDialog dlg = new TextDialog("Test", fields, false);
		try {
			List<String> values = dlg.getTextStrings();
			assertEquals(1, values.size());
			assertEquals("hello", values.get(0));
		} finally {
			dlg.dispose();
		}
	}

	public void testTwoFieldsSplitPaneLayout() {
		TextField f1 = new TextField("Field1", "aaa", false, false);
		TextField f2 = new TextField("Field2", "bbb", false, false);
		List<ITextComponent<String>> fields = Arrays.<ITextComponent<String>>asList(f1, f2);
		TextDialog dlg = new TextDialog("Test", fields, false);
		try {
			List<String> values = dlg.getTextStrings();
			assertEquals(2, values.size());
			assertEquals("aaa", values.get(0));
			assertEquals("bbb", values.get(1));
		} finally {
			dlg.dispose();
		}
	}

	public void testGetTextFieldsCount() {
		TextField f1 = new TextField("A", "x", false, false);
		TextField f2 = new TextField("B", "y", false, false);
		TextField f3 = new TextField("C", "z", false, false);
		List<ITextComponent<String>> fields = Arrays.<ITextComponent<String>>asList(f1, f2, f3);
		TextDialog dlg = new TextDialog("Test", fields, false);
		try {
			assertEquals(3, dlg.getTextFields().size());
		} finally {
			dlg.dispose();
		}
	}

	public void testIsCancelledByDefault() {
		TextField f1 = new TextField("Name", "val", false, false);
		List<ITextComponent<String>> fields = Arrays.<ITextComponent<String>>asList(f1);
		TextDialog dlg = new TextDialog("Test", fields, false);
		try {
			assertTrue("Dialog should be cancelled by default", dlg.isCancelled());
		} finally {
			dlg.dispose();
		}
	}

	public void testScrollableConstructor() {
		TextField f1 = new TextField("Name", "scrollable", false, false);
		List<ITextComponent<String>> fields = Arrays.<ITextComponent<String>>asList(f1);
		TextDialog dlg = new TextDialog("Test", fields, false, true);
		try {
			List<String> values = dlg.getTextStrings();
			assertEquals(1, values.size());
			assertEquals("scrollable", values.get(0));
		} finally {
			dlg.dispose();
		}
	}
}
