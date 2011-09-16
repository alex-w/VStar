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

import java.util.List;

import javax.swing.JDialog;

import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.exception.AlgorithmError;
import org.aavso.tools.vstar.exception.CancellationException;
import org.aavso.tools.vstar.ui.dialog.MultiNumberEntryDialog;
import org.aavso.tools.vstar.ui.dialog.NumberField;
import org.aavso.tools.vstar.ui.dialog.period.wwz.WeightedWaveletZTransformResultDialog;
import org.aavso.tools.vstar.ui.mediator.message.NewStarMessage;
import org.aavso.tools.vstar.util.period.wwz.WWZCoordinateType;
import org.aavso.tools.vstar.util.period.wwz.WeightedWaveletZTransform;

/**
 * Weighted Wavelet Z Transform (frequency range) plugin.
 */
public class WeightedWaveletZTransformWithFrequencyRangePlugin extends
		WeightedWaveletZTransformPluginBase {

	private Double currMinFreq;
	private Double currMaxFreq;
	private Double currDeltaFreq;

	/**
	 * Constructor
	 */
	public WeightedWaveletZTransformWithFrequencyRangePlugin() {
		super();
		currMinFreq = null;
		currMaxFreq = null;
		currDeltaFreq = null;
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#executeAlgorithm(java.util.List)
	 */
	@Override
	public void executeAlgorithm(List<ValidObservation> obs)
			throws AlgorithmError, CancellationException {

		NumberField minFreqField = new NumberField("Minimum Frequency", 0.0,
				null, currMinFreq);

		NumberField maxFreqField = new NumberField("Maximum Frequency", 0.0,
				null, currMaxFreq);

		NumberField deltaFreqField = new NumberField("Frequency Step", null,
				null, currDeltaFreq);

		List<NumberField> fields = createNumberFields(minFreqField,
				maxFreqField, deltaFreqField);

		MultiNumberEntryDialog paramDialog = new MultiNumberEntryDialog(
				"WWZ Parameters", fields);

		if (!paramDialog.isCancelled()) {
			double minFreq, maxFreq, deltaFreq, decay, timeDivisions;

			currMinFreq = minFreq = minFreqField.getValue();
			currMaxFreq = maxFreq = maxFreqField.getValue();
			currDeltaFreq = deltaFreq = deltaFreqField.getValue();
			currDecay = decay = decayField.getValue();
			currTimeDivisions = timeDivisions = timeDivisionsField.getValue();

			// TODO: ask about number of frequencies > 1000 via dialog?

			wwt = new WeightedWaveletZTransform(obs, decay, timeDivisions);
			wwt.make_freqs_from_freq_range(Math.min(minFreq, maxFreq), Math
					.max(minFreq, maxFreq), deltaFreq);
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
		return "Weighted Wavelet Z-Transform time-frequency analysis";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return "WWZ with Frequency Range";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#getDialog(org.aavso.tools.vstar.data.SeriesType)
	 */
	@Override
	public JDialog getDialog(SeriesType sourceSeriesType) {
		return new WeightedWaveletZTransformResultDialog(getDisplayName(),
				"WWZ (series: " + sourceSeriesType.toString() + ")", wwt,
				WWZCoordinateType.FREQUENCY);
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#newStarAction(org.aavso.tools.vstar.ui.mediator.message.NewStarMessage)
	 */
	@Override
	protected void newStarAction(NewStarMessage message) {
		reset();
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase#reset()
	 */
	@Override
	public void reset() {
		super.reset();
		currMinFreq = null;
		currMaxFreq = null;
		currDeltaFreq = null;
	}
}
