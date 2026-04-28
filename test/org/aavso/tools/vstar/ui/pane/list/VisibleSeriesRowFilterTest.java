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
package org.aavso.tools.vstar.ui.pane.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.RowFilter;

import org.aavso.tools.vstar.data.IOrderedObservationSource;
import org.aavso.tools.vstar.data.Magnitude;
import org.aavso.tools.vstar.data.MagnitudeModifier;
import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.ui.mediator.AnalysisType;
import org.aavso.tools.vstar.ui.model.list.SimpleFormatRawDataColumnInfoSource;
import org.aavso.tools.vstar.ui.model.list.ValidObservationTableModel;

import junit.framework.TestCase;

/**
 * Pure unit test for {@link VisibleSeriesRowFilter}.
 *
 * Tests the {@code include()} method that decides whether a table row
 * is visible based on the observation's series, band, and flags.
 *
 * Part of issue #579 (prong C): GUI code coverage.
 */
public class VisibleSeriesRowFilterTest extends TestCase {

	@Override
	protected void setUp() {
		Locale.setDefault(Locale.ENGLISH);
	}

	private ValidObservation makeOb(SeriesType band) {
		ValidObservation ob = new ValidObservation();
		ob.setJD(2451545.0);
		ob.setMagnitude(new Magnitude(5.5, 0.01));
		ob.setBand(band);
		return ob;
	}

	private RowFilter.Entry<IOrderedObservationSource, Integer> makeEntry(
			final ValidObservationTableModel tableModel, final int row) {
		return new RowFilter.Entry<IOrderedObservationSource, Integer>() {
			@Override
			public IOrderedObservationSource getModel() {
				return tableModel;
			}

			@Override
			public int getValueCount() {
				return tableModel.getColumnCount();
			}

			@Override
			public Object getValue(int index) {
				return tableModel.getValueAt(row, index);
			}

			@Override
			public Integer getIdentifier() {
				return row;
			}
		};
	}

	private ValidObservationTableModel buildModel(ValidObservation ob, SeriesType band) {
		List<ValidObservation> obs = new ArrayList<ValidObservation>(Arrays.asList(ob));
		Map<SeriesType, List<ValidObservation>> map = new HashMap<SeriesType, List<ValidObservation>>();
		map.put(band, obs);
		return new ValidObservationTableModel(map, obs, new SimpleFormatRawDataColumnInfoSource());
	}

	public void testVisualBandVisible() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>(Arrays.asList(SeriesType.Visual));
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertTrue(filter.include(makeEntry(model, 0)));
	}

	public void testVisualBandNotVisibleWhenSeriesHidden() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>(Arrays.asList(SeriesType.Johnson_V));
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertFalse(filter.include(makeEntry(model, 0)));
	}

	public void testDiscrepantObservationRoutedToDiscrepantSeries() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ob.setDiscrepant(true);
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>(Arrays.asList(SeriesType.DISCREPANT));
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertTrue("Discrepant observation should be visible when DISCREPANT series is visible",
				filter.include(makeEntry(model, 0)));
	}

	public void testDiscrepantObservationHiddenWhenDiscrepantSeriesNotVisible() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ob.setDiscrepant(true);
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>(Arrays.asList(SeriesType.Visual));
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertFalse("Discrepant observation should be hidden when only Visual is visible",
				filter.include(makeEntry(model, 0)));
	}

	public void testExcludedObservationRoutedToExcludedSeries() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ob.setExcluded(true);
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>(Arrays.asList(SeriesType.Excluded));
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertTrue("Excluded observation should be visible when Excluded series is visible",
				filter.include(makeEntry(model, 0)));
	}

	public void testFainterThanObservationRoutedToFainterThanSeries() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ob.setMagnitude(new Magnitude(5.5, MagnitudeModifier.FAINTER_THAN, false, 0.01));
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>(Arrays.asList(SeriesType.FAINTER_THAN));
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertTrue("Fainter-than observation should be visible when FAINTER_THAN series is visible",
				filter.include(makeEntry(model, 0)));
	}

	public void testEmptyVisibleSetHidesAll() {
		ValidObservation ob = makeOb(SeriesType.Visual);
		ValidObservationTableModel model = buildModel(ob, SeriesType.Visual);

		Set<SeriesType> visible = new HashSet<SeriesType>();
		VisibleSeriesRowFilter filter = new VisibleSeriesRowFilter(model, visible, AnalysisType.RAW_DATA);

		assertFalse(filter.include(makeEntry(model, 0)));
	}
}
