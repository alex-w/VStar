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

/**
 * This message should be sent when a period change has occurred.
 */
public class PeriodChangeMessage extends MessageBase {

	private double period;

	/**
	 * Constructor.
	 * 
	 * @param source
	 *            The source of the message.
	 * @param period
	 *            The new period. Note that it may not actually be new. That is
	 *            up to the receiver to decide.
	 */
	public PeriodChangeMessage(Object source, double period) {
		super(source);
		this.period = period;
	}

	/**
	 * @return the period
	 */
	public double getPeriod() {
		return period;
	}
}
