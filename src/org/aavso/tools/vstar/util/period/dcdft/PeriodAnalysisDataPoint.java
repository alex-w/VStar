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
package org.aavso.tools.vstar.util.period.dcdft;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.aavso.tools.vstar.util.period.IPeriodAnalysisDatum;
import org.aavso.tools.vstar.util.period.PeriodAnalysisCoordinateType;

/**
 * This class represents a period analysis data point.
 */
public class PeriodAnalysisDataPoint implements IPeriodAnalysisDatum {

	public final static PeriodAnalysisCoordinateType[] DCDFT_COORD_TYPES = new PeriodAnalysisCoordinateType[] {
			PeriodAnalysisCoordinateType.FREQUENCY, PeriodAnalysisCoordinateType.PERIOD,
			PeriodAnalysisCoordinateType.POWER, PeriodAnalysisCoordinateType.SEMI_AMPLITUDE };

	private PeriodAnalysisCoordinateType[] coordTypes;
	private Map<PeriodAnalysisCoordinateType, Double> coords;

	/**
	 * Constructor
	 * 
	 * @param coordTypes The array of coordinate types for this data point.
	 * @param coordVals  ... Coordinate values corresponding to each coordinate in
	 *                   sequence.
	 */
	public PeriodAnalysisDataPoint(PeriodAnalysisCoordinateType[] coordTypes, double... coordVals) {
		this.coordTypes = coordTypes;
		coords = new LinkedHashMap<PeriodAnalysisCoordinateType, Double>();

		int i = 0;
		for (Double val : coordVals) {
			assert (i < coordTypes.length);
			coords.put(coordTypes[i++], val);
		}
	}

	public PeriodAnalysisDataPoint(double... coordVals) {

		this(DCDFT_COORD_TYPES, coordVals);
	}


	public PeriodAnalysisCoordinateType[] getCoordTypes() {
		return coordTypes;
	}

	public Map<PeriodAnalysisCoordinateType, Double> getCoords() {
		return coords;
	}

	/**
	 * @return the frequency
	 */
	public double getFrequency() {
		return coords.get(PeriodAnalysisCoordinateType.FREQUENCY);
	}

	/**
	 * @return the period
	 */
	public double getPeriod() {
		return coords.get(PeriodAnalysisCoordinateType.PERIOD);
	}

	/**
	 * @return the power
	 */
	public double getPower() {
		return coords.get(PeriodAnalysisCoordinateType.POWER);
	}

	/**
	 * @return the semi-amplitude
	 */
	public double getSemiAmplitude() {
		return coords.get(PeriodAnalysisCoordinateType.SEMI_AMPLITUDE);
	}

	/**
	 * Retrieve a value by coordinate type.
	 * 
	 * @param type The coordinate type.
	 * @return The value or NaN if the coordinate type is not present.
	 */
	public double getValue(PeriodAnalysisCoordinateType type) {
		double value = Double.NaN;

		if (coords.containsKey(type)) {
			value = coords.get(type);
		}

		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(coordTypes);
		result = prime * result + ((coords == null) ? 0 : coords.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PeriodAnalysisDataPoint other = (PeriodAnalysisDataPoint) obj;
		if (!Arrays.equals(coordTypes, other.coordTypes))
			return false;
		if (coords == null) {
			if (other.coords != null)
				return false;
		} else if (!coords.equals(other.coords))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();

		for (PeriodAnalysisCoordinateType type : coords.keySet()) {
			buf.append(String.format("%s: %10.4f ", type, coords.get(type)));
		}

		return buf.toString();
	}
}
