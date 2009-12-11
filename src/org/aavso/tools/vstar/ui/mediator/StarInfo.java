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
package org.aavso.tools.vstar.ui.mediator;

/**
 * This class contains information about a star.
 * 
 * Some or all of its members may be absent, so each is null by default.
 */
public class StarInfo {

	String designation = null;
	String auid = null;
	Double period = null;
	Double epoch = null;

	/**
	 * Constructor.
	 * 
	 * @param designation
	 *            A name or designation for the star.
	 * @param auid
	 *            The star's unique identifier (AAVSO unique ID).
	 * @param period
	 *            The star's period, if known.
	 * @param epoch
	 *            The star's (corresponding to period for phase plot purposes)
	 *            epoch, if known.
	 */
	public StarInfo(String designation, String auid, Double period, Double epoch) {
		this.designation = designation;
		this.auid = auid;
		this.period = period;
		this.epoch = epoch;
	}

	/**
	 * Constructor.
	 * 
	 * The AUID, period and epoch are null.
	 * 
	 * @param designation
	 *            A name or designation for the star.
	 * @param auid
	 *            The star's unique identifier (AAVSO unique ID).
	 */
	public StarInfo(String designation, String auid) {
		this(designation, auid, null, null);
	}
	
	/**
	 * Constructor.
	 * 
	 * The AUID, period and epoch are null.
	 * 
	 * @param designation
	 *            A name or designation for the star.
	 */
	public StarInfo(String designation) {
		this(designation, null, null, null);
	}

	/**
	 * @return the designation
	 */
	public String getDesignation() {
		return designation;
	}

	/**
	 * @return the auid
	 */
	public String getAuid() {
		return auid;
	}

	/**
	 * @return the period
	 */
	public Double getPeriod() {
		return period;
	}

	/**
	 * @return the epoch
	 */
	public Double getEpoch() {
		return epoch;
	}
}