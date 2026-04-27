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
 * Pure unit tests for {@link MessageBase} and a couple of representative
 * subclasses (no Swing infra needed). Demonstrates that the
 * {@code ui.mediator.message} package is exercise-able without a display.
 */
public class MessageBaseTest extends TestCase {

	public void testSourceIsRetrievable() {
		Object source = new Object();
		MessageBase msg = new MessageBase(source);

		assertSame(source, msg.getSource());
	}

	public void testTagAccessorRoundTrips() {
		MessageBase msg = new MessageBase(this);
		assertNull("Tag should be null until set", msg.getTag());

		msg.setTag("alpha");
		assertEquals("alpha", msg.getTag());

		msg.setTag(null);
		assertNull(msg.getTag());
	}

	public void testPanRequestMessageRetainsPanType() {
		PanRequestMessage msg = new PanRequestMessage(this, PanType.LEFT);

		assertSame(this, msg.getSource());
		assertEquals(PanType.LEFT, msg.getPanType());
	}

	public void testZoomRequestMessageRetainsZoomType() {
		ZoomRequestMessage msg =
				new ZoomRequestMessage(this, ZoomType.ZOOM_IN);

		assertSame(this, msg.getSource());
		assertEquals(ZoomType.ZOOM_IN, msg.getZoomType());
	}

	public void testZoomTypeEnumeratesAllValues() {
		ZoomType[] expected = {
				ZoomType.ZOOM_IN, ZoomType.ZOOM_OUT, ZoomType.ZOOM_TO_FIT
		};
		assertEquals(expected.length, ZoomType.values().length);

		for (ZoomType zt : expected) {
			assertSame(zt, ZoomType.valueOf(zt.name()));
		}
	}
}
