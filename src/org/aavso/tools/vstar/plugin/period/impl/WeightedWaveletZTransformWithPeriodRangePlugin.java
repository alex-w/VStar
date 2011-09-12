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
package org.aavso.tools.vstar.plugin.period.impl;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;

import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.exception.AlgorithmError;
import org.aavso.tools.vstar.exception.CancellationException;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase;
import org.aavso.tools.vstar.ui.dialog.MultiNumberEntryDialog;
import org.aavso.tools.vstar.ui.dialog.NumberField;
import org.aavso.tools.vstar.ui.dialog.period.wwz.WeightedWaveletZTransformResultDialog;
import org.aavso.tools.vstar.ui.mediator.message.NewStarMessage;
import org.aavso.tools.vstar.util.period.wwz.WWZCoordinateType;
import org.aavso.tools.vstar.util.period.wwz.WeightedWaveletZTransform;

/**
 * Weighted Wavelet Z Transform (period range) plugin.
 */
public class WeightedWaveletZTransformWithPeriodRangePlugin extends
		PeriodAnalysisPluginBase {

	private WeightedWaveletZTransform wwt;

	private Double currMinPeriod;
	private Double currMaxPeriod;
	private Double currDeltaPeriod;
	private Double currDecay;

	/**
	 * Constructor
	 */
	public WeightedWaveletZTransformWithPeriodRangePlugin() {
		super();
		wwt = null;

		currMinPeriod = null;
		currMaxPeriod = null;
		currDeltaPeriod = null;
		currDecay = null;
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#executeAlgorithm(java.util.List)
	 */
	@Override
	public void executeAlgorithm(List<ValidObservation> obs)
			throws AlgorithmError, CancellationException {

		List<NumberField> fields = new ArrayList<NumberField>();

		NumberField minPeriodField = new NumberField("Minimum Period", 0.0,
				null, currMinPeriod);
		fields.add(minPeriodField);

		NumberField maxPeriodField = new NumberField("Maximum Period", 0.0,
				null, currMaxPeriod);
		fields.add(maxPeriodField);

		NumberField deltaPeriodField = new NumberField("Period Step", null,
				null, currDeltaPeriod);
		fields.add(deltaPeriodField);

		NumberField decayField = new NumberField("Decay", null, null, currDecay);
		fields.add(decayField);

		MultiNumberEntryDialog paramDialog = new MultiNumberEntryDialog(
				"WWZ Parameters", fields);

		if (!paramDialog.isCancelled()) {
			double minPeriod, maxPeriod, deltaPeriod, decay;

			currMinPeriod = minPeriod = minPeriodField.getValue();
			currMaxPeriod = maxPeriod = maxPeriodField.getValue();
			currDeltaPeriod = deltaPeriod = deltaPeriodField.getValue();
			currDecay = decay = decayField.getValue();

			// TODO: ask about number of periods > 1000, with dialog?

			double freq1 = 1.0 / minPeriod;
			double freq2 = 1.0 / maxPeriod;

			wwt = new WeightedWaveletZTransform(obs, Math.min(freq1, freq2),
					Math.max(freq1, freq2), deltaPeriod, decay);

			wwt.execute();
		} else {
			throw new CancellationException("WWZ cancelled");
		}
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#getDescription()
	 */
	@Override
	public String getDescription() {
		return "Weighted Wavelet Z-Transform time-period analysis";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return "WWZ with Period Range";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.PluginBase#getGroup()
	 */
	@Override
	public String getGroup() {
		return "WWZ";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#getDialog(org.aavso.tools.vstar.data.SeriesType)
	 */
	@Override
	public JDialog getDialog(SeriesType sourceSeriesType) {
		return new WeightedWaveletZTransformResultDialog(getDisplayName(),
				"WWZ (series: " + sourceSeriesType.toString() + ")", wwt,
				WWZCoordinateType.PERIOD);
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#newStarAction(org.aavso.tools.vstar.ui.mediator.message.NewStarMessage)
	 */
	@Override
	protected void newStarAction(NewStarMessage message) {
		currMinPeriod = null;
		currMaxPeriod = null;
		currDeltaPeriod = null;
		currDecay = null;
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#reset()
	 */
	@Override
	public void reset() {
		// Nothing to do yet.
	}
}