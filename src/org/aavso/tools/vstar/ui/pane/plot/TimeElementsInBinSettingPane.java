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
package org.aavso.tools.vstar.ui.pane.plot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.ui.dialog.MessageBox;
import org.aavso.tools.vstar.ui.mediator.Mediator;
import org.aavso.tools.vstar.ui.mediator.message.MeanSourceSeriesChangeMessage;
import org.aavso.tools.vstar.ui.model.plot.ITimeElementEntity;
import org.aavso.tools.vstar.util.locale.LocaleProps;
import org.aavso.tools.vstar.util.notification.Listener;
import org.aavso.tools.vstar.util.prefs.NumericPrecisionPrefs;

/**
 * This component permits the time-elements-in-bin value to be changed which in
 * turn can be used to modify the means series in an observations and means
 * plot.
 */
@SuppressWarnings("serial")
public class TimeElementsInBinSettingPane extends JPanel {

	private ObservationAndMeanPlotPane plotPane;
	private ITimeElementEntity timeElementEntity;

	private JSpinner timeElementsInBinSpinner;
	private SpinnerNumberModel timeElementsInBinSpinnerModel;

	/**
	 * Constructor.
	 * 
	 * @param spinnerTitle
	 *            A title for the spinner.
	 * @param plotPane
	 *            An observation and mean plot pane.
	 * @param timeElementEntity
	 *            A time element source for observations.
	 */
	public TimeElementsInBinSettingPane(String spinnerTitle,
			ObservationAndMeanPlotPane plotPane,
			ITimeElementEntity timeElementEntity) {

		super();

		this.setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		this.plotPane = plotPane;
		this.timeElementEntity = timeElementEntity;

		this.setBorder(BorderFactory.createTitledBorder(spinnerTitle));

		// Spinner for time-elements-in-bin.

		this.add(Box.createHorizontalGlue());

		// Given the source-series of the means series, determine the
		// maximum day/phase range for the time-elements-in-bin spinner.
		// We arbitrarily choose one model.
		List<ValidObservation> meanSrcObsList = plotPane.getObsModel()
				.getSeriesNumToObSrcListMap().get(
						plotPane.getObsModel().getMeanSourceSeriesNum());

		double max = timeElementEntity.getMaxTimeIncrements(meanSrcObsList);

		// Spinner for time-elements-in-bin with the specified current, min, and
		// max values, and step size. If the "current time elements in bin"
		// value is larger than the calculated max value, correct that.
		// We arbitrarily choose one model.
		double currTimeElementsInBin = plotPane.getObsModel()
				.getTimeElementsInBin();
		currTimeElementsInBin = currTimeElementsInBin <= max ? currTimeElementsInBin
				: max;
		plotPane.getObsModel().setTimeElementsInBin(currTimeElementsInBin);

		timeElementsInBinSpinnerModel = new SpinnerNumberModel(
				currTimeElementsInBin, 0, max, timeElementEntity
						.getDefaultTimeIncrements());
		timeElementsInBinSpinner = new JSpinner(timeElementsInBinSpinnerModel);
		timeElementsInBinSpinner.setEditor(new JSpinner.NumberEditor(
				timeElementsInBinSpinner, timeElementEntity.getNumberFormat()));

		this.add(timeElementsInBinSpinner);

		// The spinner model needs to be updated with the max value
		// when the mean source series changes!
		Mediator.getInstance().getMeanSourceSeriesChangeNotifier().addListener(
				createMeanSourceSeriesChangeListener());

		this.add(Box.createHorizontalGlue());

		// Update button for time-elements-in-bin.
		JButton updateButton = new JButton(LocaleProps.get("APPLY_BUTTON"));
		updateButton.addActionListener(createUpdateMeansButtonListener());
		this.add(updateButton);

		this.add(Box.createHorizontalGlue());
	}

	// Return a listener for mean source series change events so we can update
	// the spinner model.
	private Listener<MeanSourceSeriesChangeMessage> createMeanSourceSeriesChangeListener() {
		return new Listener<MeanSourceSeriesChangeMessage>() {
			@Override
			// We arbitrarily choose one model.
			public void update(MeanSourceSeriesChangeMessage msg) {
				List<ValidObservation> meanSrcObsList = plotPane.getObsModel()
						.getSeriesNumToObSrcListMap()
						.get(plotPane.getObsModel().getMeanSourceSeriesNum());

				double max = timeElementEntity
						.getMaxTimeIncrements(meanSrcObsList);

				// Set the new maximum and if the value currently in the spinner
				// is greater than this, change it to be that maximum value.
				timeElementsInBinSpinnerModel.setMaximum(max);
				if ((Double) timeElementsInBinSpinner.getValue() > max) {
					timeElementsInBinSpinner.setValue(max);
				}
			}

			@Override
			public boolean canBeRemoved() {
				return true;
			}
		};
	}

	// Return a listener for the "update means" (Apply) button.
	private ActionListener createUpdateMeansButtonListener() {
		final JPanel self = this;
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Get the value and change the means series.
				double timeElementsInBin = timeElementsInBinSpinnerModel
						.getNumber().doubleValue();
				if (timeElementsInBin > 0) {
					plotPane.changeMeansSeries(timeElementsInBin);
				} else {
					MessageBox.showErrorDialog(self,
							"Mean Series Update Request", String.format(
									"The bin size value (%s)"
											+ " must be greater than zero.",
									NumericPrecisionPrefs
											.formatTime(timeElementsInBin)));
				}
			}
		};
	}
}
