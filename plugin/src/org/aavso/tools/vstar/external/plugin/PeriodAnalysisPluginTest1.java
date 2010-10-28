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
package org.aavso.tools.vstar.external.plugin;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.exception.AlgorithmError;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisComponentFactory;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisDialogBase;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase;
import org.aavso.tools.vstar.ui.NamedComponent;
import org.aavso.tools.vstar.ui.mediator.Mediator;
import org.aavso.tools.vstar.ui.mediator.message.MeanSourceSeriesChangeMessage;
import org.aavso.tools.vstar.ui.mediator.message.NewStarMessage;
import org.aavso.tools.vstar.ui.mediator.message.PeriodAnalysisSelectionMessage;
import org.aavso.tools.vstar.util.notification.Listener;
import org.aavso.tools.vstar.util.period.PeriodAnalysisCoordinateType;

/**
 * VStar period analysis plugin test.
 * 
 * This plugin generates random periods and shows them on a line plot, in a  
 * table, with the selected period displayed in a label component. A new phase 
 * plot can be generated with that period.
 */
public class PeriodAnalysisPluginTest1 extends PeriodAnalysisPluginBase {

	final private int N = 100;

	private double period;

	private double[] domain = new double[N];
	private double[] range = new double[N];

	protected final static String NAME = "Period Analysis Plugin Test 1";

	@Override
	public void executeAlgorithm(List<ValidObservation> obs) throws AlgorithmError {
		// Create a set of random values to be plotted. A real plugin would
		// instead apply some algorithm to the observations to create data
		// arrays (e.g. a pair of domain and range arrays).
		for (int i = 0; i < N; i++) {
			domain[i] = i;
			range[i] = Math.random();
		}
	}

	@Override
	public String getDescription() {
		return "Period Analysis Plugin Test: generates random periods.";
	}

	@Override
	public JDialog getDialog(SeriesType seriesType) {
		return new PeriodAnalysisDialog();
	}

	@Override
	public String getDisplayName() {
		return NAME;
	}

	@Override
	protected void meanSourceSeriesChangeAction(
			MeanSourceSeriesChangeMessage msg) {
		// Nothing to do
	}

	@Override
	protected void newStarAction(NewStarMessage msg) {
		// Nothing to do
	}

	class PeriodAnalysisDialog extends PeriodAnalysisDialogBase {
		PeriodAnalysisDialog() {
			super(NAME);
			prepareDialog();
			this.setNewPhasePlotButtonState(false);
		}

		@Override
		protected Component createContent() {
			// Random plot.
			Component plot = PeriodAnalysisComponentFactory.createLinePlot(
					"Random Periods", "", domain, range,
					PeriodAnalysisCoordinateType.FREQUENCY,
					PeriodAnalysisCoordinateType.POWER);

			// Data table.
			PeriodAnalysisCoordinateType[] columns = {
					PeriodAnalysisCoordinateType.FREQUENCY,
					PeriodAnalysisCoordinateType.AMPLITUDE };

			double[][] dataArrays = {domain, range};
			
			Component table = PeriodAnalysisComponentFactory.createDataTable(
					columns, dataArrays);

			// Random period label component.
			JPanel randomPeriod = new RandomPeriodComponent(this);

			// Return tabbed pane of plot and period display component.
			return PeriodAnalysisComponentFactory.createTabs(
					new NamedComponent("Plot", plot), new NamedComponent(
							"Data", table), new NamedComponent("Random Period",
							randomPeriod));
		}

		// Send a period change message when the new-phase-plot button is
		// clicked.
		@Override
		protected void newPhasePlotButtonAction() {
			sendPeriodChangeMessage(period);
		}
	}

	/**
	 * This class simply shows the currently selected (from plot or table) and
	 * updates the period member to be used when the new-phase-plot button is
	 * clicked. It's not really necessary, just shows a custom GUI component.
	 */
	class RandomPeriodComponent extends JPanel implements
			Listener<PeriodAnalysisSelectionMessage> {

		private JLabel label;
		private PeriodAnalysisDialog dialog;

		public RandomPeriodComponent(PeriodAnalysisDialog dialog) {
			super();
			this.dialog = dialog;
			label = new JLabel("Period: None selected"
					+ String.format("%1.4f", period));
			this.add(label, BorderLayout.CENTER);

			Mediator.getInstance().getPeriodAnalysisSelectionNotifier()
					.addListener(this);
		}

		// Period analysis selection update handler methods.
		public void update(PeriodAnalysisSelectionMessage msg) {
			if (msg.getSource() != this) {
				try {
					period = range[msg.getItem()];
					label.setText("Period: " + String.format("%1.4f", period));
					dialog.setNewPhasePlotButtonState(true);
				} catch (ArrayIndexOutOfBoundsException e) {
				}
			}
		}

		public boolean canBeRemoved() {
			return false;
		}
	}

	@Override
	public void reset() {
	}
}