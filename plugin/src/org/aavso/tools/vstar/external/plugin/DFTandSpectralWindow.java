/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2015  AAVSO (http://www.aavso.org/)
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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;

import org.aavso.tools.vstar.data.DateInfo;
import org.aavso.tools.vstar.data.Magnitude;
import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.exception.AlgorithmError;
import org.aavso.tools.vstar.exception.CancellationException;
import org.aavso.tools.vstar.plugin.PluginComponentFactory;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisComponentFactory;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisDialogBase;
import org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase;
import org.aavso.tools.vstar.ui.NamedComponent;
import org.aavso.tools.vstar.ui.dialog.DoubleField;
import org.aavso.tools.vstar.ui.dialog.ITextComponent;
import org.aavso.tools.vstar.ui.dialog.MessageBox;
import org.aavso.tools.vstar.ui.dialog.MultiEntryComponentDialog;
import org.aavso.tools.vstar.ui.dialog.model.HarmonicInputDialog;
import org.aavso.tools.vstar.ui.dialog.period.PeriodAnalysis2DChartPane;
import org.aavso.tools.vstar.ui.dialog.period.PeriodAnalysisDataTablePane;
import org.aavso.tools.vstar.ui.dialog.period.PeriodAnalysisTopHitsTablePane;
import org.aavso.tools.vstar.ui.mediator.Mediator;
import org.aavso.tools.vstar.ui.mediator.message.NewStarMessage;
import org.aavso.tools.vstar.ui.mediator.message.PeriodAnalysisSelectionMessage;
import org.aavso.tools.vstar.ui.model.list.PeriodAnalysisDataTableModel;
import org.aavso.tools.vstar.ui.model.plot.PeriodAnalysis2DPlotModel;
import org.aavso.tools.vstar.util.locale.LocaleProps;
import org.aavso.tools.vstar.util.model.Harmonic;
import org.aavso.tools.vstar.util.model.PeriodAnalysisDerivedMultiPeriodicModel;
import org.aavso.tools.vstar.util.model.PeriodFitParameters;
import org.aavso.tools.vstar.util.notification.Listener;
import org.aavso.tools.vstar.util.period.IPeriodAnalysisAlgorithm;
import org.aavso.tools.vstar.util.period.PeriodAnalysisCoordinateType;
import org.aavso.tools.vstar.util.period.dcdft.PeriodAnalysisDataPoint;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.apache.commons.math.stat.descriptive.rank.Median;
import org.apache.commons.math.stat.regression.OLSMultipleLinearRegression;

/**
 * 	DFT according to Deeming, T.J., 1975, Ap&SS, 36, 137
	plus DFT for the unit-amplitude signal == Spectral Window
 */
public class DFTandSpectralWindow extends PeriodAnalysisPluginBase {

	private boolean showCalcTime = true;
	private long algStartTime;

	// DCDFT via OLSMultipleLinearRegression: much slower then existing, 
	// no big amplitude damping near 0 freq.!
	// Set to 'true' to enable.
	private boolean showDCDFT = false;
	
	private static int MAX_TOP_HITS = -1; // set to -1 for the unlimited number!
	
	private boolean firstInvocation;
    //I (Max) am not sure if it is required (volatile). However, it is accessed from different threads.
	private volatile boolean interrupted;
	private volatile boolean algorithmCreated;
	private boolean cancelled;
	private boolean resetParams;

	private FtResult ftResult;
	private Double minFrequency, maxFrequency, resolution;
	
	private enum FAnalysisType {
		DFT("DFT (Deeming 1975)"), SPW("Spectral Window"), DCDFT("DC DFT");

		public final String label;

		private FAnalysisType(String label) {
			this.label = label;
		}

	}
	
	private FAnalysisType analysisType;

	private IPeriodAnalysisAlgorithm algorithm;
	
	private List<PeriodAnalysisDialog> resultDialogList;
	
	/**
	 * Constructor
	 */
	public DFTandSpectralWindow() {
		super();
		firstInvocation = true;
		reset();
	}

	@Override
	public String getDescription() {
		return "DFT and Spectral Window Frequency Range";
	}

	@Override
	public String getDisplayName() {
		return "DFT and Spectral Window Frequency Range";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.IPlugin#getDocName()
	 */
	@Override
	public String getDocName() {
		return "DFT and Spectral Window Plug-In.pdf";
	}

	@Override
	public void executeAlgorithm(List<ValidObservation> obs)
			throws AlgorithmError, CancellationException {

		if (firstInvocation) {
			Mediator.getInstance().getNewStarNotifier()
					.addListener(getNewStarListener());

			firstInvocation = false;
		}

		if (resetParams) {
			ftResult = new FtResult(obs);
			minFrequency = 0.0;
			maxFrequency = 0.0;
			resolution = null;
			double interval = ftResult.getMedianTimeInterval();
			if (interval > 0.0) {
				// Trying to estimate the Nyquist frequency from the median interval between observations.
				// Restrict it if it is too high.
				maxFrequency = Math.min(0.5 / interval, 50.0);
				// The peak width in the frequency domain ~ the length of the observation time span.    
				resolution = 0.05 / ftResult.getObservationTimeSpan(); 
			}
			analysisType = FAnalysisType.DFT;
			resetParams = false;
		}
		
		cancelled = !parametersDialog();
		if (cancelled)
			return;
		
		ftResult.setAnalysisType(analysisType);
		
		algorithm = new DFTandSpectralWindowAlgorithm(obs, minFrequency, maxFrequency, resolution, ftResult);
		algorithmCreated = true;
		algStartTime = System.currentTimeMillis();
		algorithm.execute();
	}
	
	@Override
	public JDialog getDialog(SeriesType sourceSeriesType) {
		return interrupted || cancelled ? null : new PeriodAnalysisDialog(sourceSeriesType, analysisType);
	}

	@SuppressWarnings("serial")
	class PeriodAnalysisDialog extends PeriodAnalysisDialogBase implements
			Listener<PeriodAnalysisSelectionMessage> {

		private double period;
		private SeriesType sourceSeriesType;
		//private IPeriodAnalysisDatum selectedDataPoint;

		private PeriodAnalysisDataTablePane resultsTablePane;
		private PeriodAnalysisTopHitsTablePane topHitsTablePane;
		private List<PeriodAnalysis2DChartPane> plotPanes;

		// Keep local analysisType because there can be several instances of this dialog opened simultaneously.
		FAnalysisType analysisType;
		
		public PeriodAnalysisDialog(SeriesType sourceSeriesType, FAnalysisType analysisType) {
			super("", false, true, false);
			
			this.analysisType = analysisType;
			
			String dialogTitle = analysisType.label;
			if (showCalcTime)
				dialogTitle += (" | " + Double.toString((System.currentTimeMillis() - algStartTime) / 1000.0) + 's');
			setTitle(dialogTitle);
			
			this.sourceSeriesType = sourceSeriesType;

			prepareDialog();

			this.setNewPhasePlotButtonState(false);

			startup(); // Note: why does base class not call this in
			// prepareDialog()?
		}

		@Override
		protected Component createContent() {
			String title = analysisType.label;

			plotPanes = new ArrayList<PeriodAnalysis2DChartPane>();
			List<NamedComponent> namedComponents = new ArrayList<NamedComponent>();
			Map<PeriodAnalysis2DPlotModel, String>plotModels = new LinkedHashMap<PeriodAnalysis2DPlotModel, String>();
			
			plotModels.put(new PeriodAnalysis2DPlotModel(
					algorithm.getResultSeries(),
					PeriodAnalysisCoordinateType.FREQUENCY, 
					PeriodAnalysisCoordinateType.POWER, 
					false), "PowerPaneFrequency");

			plotModels.put(new PeriodAnalysis2DPlotModel(
					algorithm.getResultSeries(),
					PeriodAnalysisCoordinateType.FREQUENCY, 
					PeriodAnalysisCoordinateType.SEMI_AMPLITUDE, 
					false), "SemiAmplitudePaneFrequency");

			if (analysisType != FAnalysisType.SPW) {
				plotModels.put(new PeriodAnalysis2DPlotModel(
						algorithm.getResultSeries(),
						PeriodAnalysisCoordinateType.PERIOD, 
						PeriodAnalysisCoordinateType.POWER, 
						false), "PowerPanePeriod");
	
				plotModels.put(new PeriodAnalysis2DPlotModel(
						algorithm.getResultSeries(),
						PeriodAnalysisCoordinateType.PERIOD, 
						PeriodAnalysisCoordinateType.SEMI_AMPLITUDE, 
						false), "SemiAmplitudePanePeriod");
			}
			
			for (PeriodAnalysis2DPlotModel dataPlotModel : plotModels.keySet()) { 
				PeriodAnalysis2DChartPane plotPane = PeriodAnalysisComponentFactory.createLinePlot(
						title,
						sourceSeriesType.getDescription(), 
						dataPlotModel, 
						true);

				PeriodAnalysis2DPlotModel topHitsPlotModel = new PeriodAnalysis2DPlotModel(
						algorithm.getTopHits(),
						dataPlotModel.getDomainType(),
						dataPlotModel.getRangeType(),
						false);
	
				PeriodAnalysis2DChartPane topHitsPlotPane = PeriodAnalysisComponentFactory.createScatterPlot(
						title, 
						sourceSeriesType.getDescription(), 
						topHitsPlotModel,
						true);

				// Add the above line plot's model to the scatter plot.
				// Render the scatter plot last so the "handles" will be
				// the first items selected by the mouse.
				JFreeChart chart = topHitsPlotPane.getChart();
				chart.getXYPlot().setDataset(PeriodAnalysis2DChartPane.DATA_SERIES, dataPlotModel);
				chart.getXYPlot().setDataset(PeriodAnalysis2DChartPane.TOP_HIT_SERIES, topHitsPlotModel);
				chart.getXYPlot().setRenderer(PeriodAnalysis2DChartPane.DATA_SERIES, plotPane.getChart().getXYPlot().getRenderer());
				chart.getXYPlot().setDatasetRenderingOrder(DatasetRenderingOrder.REVERSE);
				
				plotPane = topHitsPlotPane;
				plotPane.setChartPaneID(plotModels.get(dataPlotModel));
				plotPanes.add(plotPane);
				String tabName = dataPlotModel.getRangeType() + " vs " + dataPlotModel.getDomainType();
				namedComponents.add(new NamedComponent(tabName, plotPane));
			}
			
			// Full results table
			PeriodAnalysisCoordinateType[] columns = {
					PeriodAnalysisCoordinateType.FREQUENCY,
					PeriodAnalysisCoordinateType.PERIOD, 
					PeriodAnalysisCoordinateType.POWER, 
					PeriodAnalysisCoordinateType.SEMI_AMPLITUDE };

			PeriodAnalysisDataTableModel dataTableModel = new PeriodAnalysisDataTableModel(columns, algorithm.getResultSeries());
			resultsTablePane = new PeriodAnalysisDataTablePaneMod(dataTableModel, algorithm, analysisType != FAnalysisType.SPW);
			resultsTablePane.setTablePaneID("DataTable");
			namedComponents.add(new NamedComponent(LocaleProps.get("DATA_TAB"), resultsTablePane));


			PeriodAnalysisDataTableModel topHitsModel = new PeriodAnalysisDataTableModel(columns, algorithm.getTopHits());
			topHitsTablePane = new PeriodAnalysisTopHitsTablePaneMod(topHitsModel, dataTableModel, algorithm, analysisType != FAnalysisType.SPW);
			resultsTablePane.setTablePaneID("TopHitsTable");
			namedComponents.add(new NamedComponent(LocaleProps.get("TOP_HITS_TAB"), topHitsTablePane));			

			// Return tabbed pane of plot and period display component.
			return PluginComponentFactory.createTabs(namedComponents);
		}

		// Send a period change message when the new-phase-plot button is
		// clicked.
		@Override
		protected void newPhasePlotButtonAction() {
			sendPeriodChangeMessage(period);
		}

		@Override
		public void startup() {
			Mediator.getInstance().getPeriodAnalysisSelectionNotifier()
					.addListener(this);
			
			if (resultDialogList == null) 
				resultDialogList = new ArrayList<PeriodAnalysisDialog>();
			resultDialogList.add(this);

			resultsTablePane.startup();
			topHitsTablePane.startup();
			for (PeriodAnalysis2DChartPane plotPane : plotPanes) {
				plotPane.startup();
			}
		}

		@Override
		public void cleanup() {
			Mediator.getInstance().getPeriodAnalysisSelectionNotifier()
					.removeListenerIfWilling(this);
			
			if (resultDialogList != null)
				resultDialogList.remove(this);
			
			resultsTablePane.cleanup();
			topHitsTablePane.cleanup();
			for (PeriodAnalysis2DChartPane plotPane : plotPanes) {
				plotPane.cleanup();
			}
		}

		// Next two methods are for Listener<PeriodAnalysisSelectionMessage>

		@Override
		public boolean canBeRemoved() {
			return false;
		}

		@Override
		public void update(PeriodAnalysisSelectionMessage info) {
			// !! We must distinguish different instances of the same dialog here.
			if (this.getName() == info.getTag()) {
				period = info.getDataPoint().getPeriod();
				//selectedDataPoint = info.getDataPoint();
				if (analysisType != FAnalysisType.SPW)
					setNewPhasePlotButtonState(true);
			}
		}

		// ** Modified result and top-hit panes **

		// The uncertainty estimator was created for DC DFT. We need to heck first if it is correct for the simple DFT. 
		class PeriodAnalysisDerivedMultiPeriodicModelMod extends PeriodAnalysisDerivedMultiPeriodicModel {

			private boolean isDCDFT;
			
			public PeriodAnalysisDerivedMultiPeriodicModelMod(PeriodAnalysisDataPoint topDataPoint,
					List<Harmonic> harmonics, IPeriodAnalysisAlgorithm algorithm, boolean isDCDFT) {
				super(topDataPoint, harmonics, algorithm);
				this.isDCDFT = isDCDFT;
			}
			
			@Override
			public String toUncertaintyString() throws AlgorithmError {
				if (isDCDFT)
					return super.toUncertaintyString();
				else
				    return "Not implemented for this type of analysis";
			}
			
		}
		
		// Model button listener.
		protected ActionListener createModelButtonHandlerMod(
				JPanel parentPanel, JTable table, PeriodAnalysisDataTableModel model, Map<Double, List<Harmonic>> freqToHarmonicsMap) {
			
			final JPanel parent = parentPanel;
			
			return new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					
					List<PeriodAnalysisDataPoint> dataPoints = new ArrayList<PeriodAnalysisDataPoint>();
					List<Double> userSelectedFreqs = new ArrayList<Double>();
					int[] selectedTableRowIndices = table.getSelectedRows();
					if (selectedTableRowIndices.length < 1) {
						MessageBox.showMessageDialog(LocaleProps.get("CREATE_MODEL_BUTTON"), "Please select a row");
						return;
					}
					for (int row : selectedTableRowIndices) {
						int modelRow = table.convertRowIndexToModel(row);

						PeriodAnalysisDataPoint dataPoint = model.getDataPointFromRow(modelRow);
						dataPoints.add(dataPoint);
						userSelectedFreqs.add(dataPoint.getFrequency());
					}

					HarmonicInputDialog dialog = new HarmonicInputDialog(parent, userSelectedFreqs, freqToHarmonicsMap);

					if (!dialog.isCancelled()) {
						List<Harmonic> harmonics = dialog.getHarmonics();
						if (!harmonics.isEmpty()) {
							try {
								PeriodAnalysisDerivedMultiPeriodicModel model = new PeriodAnalysisDerivedMultiPeriodicModelMod(
										dataPoints.get(0), harmonics, algorithm, analysisType == FAnalysisType.DCDFT);

								Mediator.getInstance().performModellingOperation(model);
							} catch (Exception ex) {
								MessageBox.showErrorDialog(parent, "Modelling", ex.getLocalizedMessage());
							}
						} else {
							MessageBox.showErrorDialog("Create Model", "Period list error");
						}
					}
				}
			};
		}
		
		// Period analysis pane with modified modelButton handler
		class PeriodAnalysisDataTablePaneMod extends
				PeriodAnalysisDataTablePane {

			public PeriodAnalysisDataTablePaneMod(
					PeriodAnalysisDataTableModel model,
					IPeriodAnalysisAlgorithm algorithm,
					boolean wantModelButton) {
				super(model, algorithm, wantModelButton);
			}

			@Override
			protected JPanel createButtonPanel() {
				JPanel buttonPane = new JPanel();

				modelButton = new JButton(LocaleProps.get("CREATE_MODEL_BUTTON"));
				modelButton.setEnabled(false);
				if (wantModelButton) {
					modelButton.addActionListener(createModelButtonHandlerMod(this, table, model, freqToHarmonicsMap));
				} else {
					modelButton.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							MessageBox.showMessageDialog(LocaleProps.get("CREATE_MODEL_BUTTON"), "Not available");
						}
					} );
				}

				if (!wantModelButton) {
					modelButton.setVisible(false);
				}

				buttonPane.add(modelButton, BorderLayout.LINE_END);

				return buttonPane;
			}

			@Override
			protected void enableButtons() {
				super.enableButtons();
			}
		}
		
		// Top hits pane with modified modelButton handler and without refineButton
		class PeriodAnalysisTopHitsTablePaneMod extends
				PeriodAnalysisTopHitsTablePane {

			public PeriodAnalysisTopHitsTablePaneMod(
					PeriodAnalysisDataTableModel topHitsModel,
					PeriodAnalysisDataTableModel fullDataModel,
					IPeriodAnalysisAlgorithm algorithm,
					boolean wantModelButton) {
				super(topHitsModel, fullDataModel, algorithm);
				if (!wantModelButton) {
				    for(ActionListener al : modelButton.getActionListeners()) {
				    	modelButton.removeActionListener(al);
					}					
					modelButton.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							MessageBox.showMessageDialog(LocaleProps.get("CREATE_MODEL_BUTTON"), "Not available");
						}
					} );
					modelButton.setVisible(false);
				}
			}

			@Override
			protected JPanel createButtonPanel() {
				JPanel buttonPane = new JPanel();

				modelButton = new JButton(LocaleProps.get("CREATE_MODEL_BUTTON"));
				modelButton.setEnabled(false);
				modelButton.addActionListener(createModelButtonHandlerMod(this, table, model, freqToHarmonicsMap));

				if (!wantModelButton) {
					modelButton.setVisible(false);
				}

				buttonPane.add(modelButton, BorderLayout.LINE_END);

				return buttonPane;
			}

			@Override
			protected void enableButtons() {
				modelButton.setEnabled(true);
			}
		}

		@Override
		protected void findHarmonicsButtonAction() {
			// To-do: harmonic search for DFT
		}
	}

	// DFT according to Deeming, T.J., 1975, Ap&SS, 36, 137
	private static class DFTandSpectralWindowAlgorithm implements IPeriodAnalysisAlgorithm {

		private List<Double> frequencies;
		private List<Double> periods;
		private List<Double> powers;
		private List<Double> semiAmplitudes;
		
		private List<ValidObservation> originalObs;
		double minFrequency, maxFrequency, resolution;
		
		private FtResult ftResult;
		
		//I (Max) am not sure if it is required (volatile). However, it is accessed from different threads.
		private volatile boolean interrupted;

		public DFTandSpectralWindowAlgorithm(
				List<ValidObservation> obs,
				double minFrequency, double maxFrequency, double resolution,
				FtResult ftResult) {
			this.originalObs = obs;
			this.minFrequency = minFrequency;
			this.maxFrequency = maxFrequency;
			this.resolution = resolution;
			this.ftResult = ftResult;
			frequencies = new ArrayList<Double>();
			periods = new ArrayList<Double>();
			powers = new ArrayList<Double>();
			semiAmplitudes = new ArrayList<Double>();
		}

		@Override
		public String getRefineByFrequencyName() {
			return "None";
		}

		@Override
		public Map<PeriodAnalysisCoordinateType, List<Double>> getResultSeries() {
			Map<PeriodAnalysisCoordinateType, List<Double>> results = new LinkedHashMap<PeriodAnalysisCoordinateType, List<Double>>();

			results.put(PeriodAnalysisCoordinateType.FREQUENCY, frequencies);
			results.put(PeriodAnalysisCoordinateType.PERIOD, periods);
			results.put(PeriodAnalysisCoordinateType.POWER, powers);
			results.put(PeriodAnalysisCoordinateType.SEMI_AMPLITUDE, semiAmplitudes);

			return results;
		}

		@Override
		public Map<PeriodAnalysisCoordinateType, List<Double>> getTopHits() {

			ArrayList<Double> hitFrequencies = new ArrayList<Double>();
			ArrayList<Double> hitPeriods = new ArrayList<Double>();
			ArrayList<Double> hitPowers = new ArrayList<Double>();
			ArrayList<Double> hitSemiAmplitudes = new ArrayList<Double>();

			// Extracting top hits (local maxima)
			if (frequencies.size() > 1) {
				Map<Integer, Double> hitFrequenciesRaw = new HashMap<Integer, Double>();
				Map<Integer, Double> hitPeriodsRaw = new HashMap<Integer, Double>();
				ArrayList<IntDoublePair> hitPowersRaw = new ArrayList<IntDoublePair>();
				Map<Integer, Double> hitSemiAmplitudesRaw = new HashMap<Integer, Double>();
				
				for (int i = 0; i < frequencies.size(); i++) {
					boolean top = false;
					if (i > 0 && i < frequencies.size() - 1) {
						if (powers.get(i) > powers.get(i - 1) && powers.get(i) > powers.get(i + 1)) {
							top = true;
						}
					} else if (i == 0) {
						if (powers.get(i) > powers.get(i + 1)) {
							top = true;
						}
					} else if (i == frequencies.size() - 1) {
						if (powers.get(i) > powers.get(i - 1)) {
							top = true;
						}
					}
					if (top) {
						hitFrequenciesRaw.put(i, frequencies.get(i));
						hitPeriodsRaw.put(i, periods.get(i));
						hitPowersRaw.add(new IntDoublePair(i, powers.get(i)));
						hitSemiAmplitudesRaw.put(i, semiAmplitudes.get(i));
					}
				}
				
				hitPowersRaw.sort(new IntDoublePairComparator(false));

				// Here we can limit the number of the top hits, however, is it worth to?
				// set maxTopHits to -1 for the unrestricted number
				int count = 0;
				for (IntDoublePair pair : hitPowersRaw) {
					if (MAX_TOP_HITS >= 0 && count >= MAX_TOP_HITS)
						break;
					hitFrequencies.add(hitFrequenciesRaw.get(pair.i));
					hitPeriods.add(hitPeriodsRaw.get(pair.i));
					hitPowers.add(pair.d);
					hitSemiAmplitudes.add(hitSemiAmplitudesRaw.get(pair.i));
					count++;
				}
			}
	
			Map<PeriodAnalysisCoordinateType, List<Double>> topHits = new LinkedHashMap<PeriodAnalysisCoordinateType, List<Double>>();

			topHits.put(PeriodAnalysisCoordinateType.FREQUENCY,	hitFrequencies);
			topHits.put(PeriodAnalysisCoordinateType.PERIOD, hitPeriods);
			topHits.put(PeriodAnalysisCoordinateType.POWER, hitPowers);
			topHits.put(PeriodAnalysisCoordinateType.SEMI_AMPLITUDE, hitSemiAmplitudes);
			
			return topHits;
		}
		
		@Override
		public void multiPeriodicFit(List<Harmonic> harmonics,
				PeriodAnalysisDerivedMultiPeriodicModel model)
				throws AlgorithmError {

			if (harmonics.size() > 100) {
				throw new AlgorithmError("Too many parameters.");
			}
			
			List<ValidObservation> modelObs = model.getFit();
			List<ValidObservation> residualObs = model.getResiduals();
			List<PeriodFitParameters> parameters = model.getParameters();
			
			double timeOffset = Math.round(ftResult.getObservationMeanTime() * 10.0) / 10.0;
			
			int nobs = originalObs.size();
			
			double[] times = new double[nobs];
			for (int i = 0; i < nobs; i++) {
				times[i] = originalObs.get(i).getJD() - timeOffset;
			}
			
			double[] y_data = new double[nobs];			
			for (int r = 0; r < nobs; r++) {
				y_data[r] = originalObs.get(r).getMag();
			}
			
			double[][] x_data = new double[nobs][2 * harmonics.size()];
			
			for (int r = 0; r < nobs; r++) {
				for (int c = 0; c < harmonics.size(); c++) {
					double frequency = harmonics.get(c).getFrequency();
					double a = 2.0 * Math.PI * frequency * times[r];
					double sin = Math.sin(a);
					double cos = Math.cos(a);
					x_data[r][2 * c] = sin;
					x_data[r][2 * c + 1] = cos;
				}
			}

//			double[] y_data = new double[nobs];
//			double[][] x_data = new double[nobs][1];
//			for (int r = 0; r < nobs; r++) {
//				y_data[r] = originalObs.get(r).getMag();
//				x_data[r][0] = times[r];
//			}
			
			OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
			regression.newSampleData(y_data, x_data);
			
			double[] beta = regression.estimateRegressionParameters();
			
//			System.out.println("Intercept: " + beta[0]);
//	        for (int i = 1; i < beta.length; i++) {
//	            System.out.println("Coefficient " + i + ": " + beta[i]);
//	        }

//	        double rSquared = regression.calculateRSquared();
//	        System.out.println("R-squared: " + rSquared);
	        
	        double zeroPoint = timeOffset;
	        for (int i = 0; i < harmonics.size(); i++) {
	        	int idx = 2 * i;
	        	double sin_coef = beta[idx + 1];
	        	double cos_coef = beta[idx + 2];
	        	double amp = Math.sqrt(sin_coef * sin_coef + cos_coef * cos_coef);
				parameters.add(new PeriodFitParameters(
						harmonics.get(i), 
						amp, 
						cos_coef, 
						sin_coef, 
						beta[0], 
						zeroPoint));
	        }
//	        System.out.println(parameters);
//	        for (PeriodFitParameters p : parameters) {
//	        	System.out.println(p.toProsaicString());	    	   
//	        }
	        
	        String modelDescripton = "Trigonometric Polynomial Model";
	        double[] y_predicted = new double[nobs];
	        for (int i = 0; i < nobs; i++) { 
	        	y_predicted[i] = beta[0]; 
	        	for (int j = 0; j < 2 * harmonics.size(); j++) {
	        		y_predicted[i] += beta[j + 1] * x_data[i][j];
	        	}
	        	//System.out.println(times[i] + " " + y_predicted[i]);
	        	
	        	ValidObservation modelOb = new ValidObservation();
				modelOb.setDateInfo(new DateInfo(times[i] + timeOffset));
				modelOb.setMagnitude(new Magnitude(y_predicted[i], 0));
				modelOb.setComments(modelDescripton);
				modelOb.setBand(SeriesType.Model);
				modelObs.add(modelOb);

				ValidObservation residualOb = new ValidObservation();
				residualOb.setDateInfo(new DateInfo(times[i] + timeOffset));
				residualOb.setMagnitude(new Magnitude(y_data[i] - y_predicted[i], 0));
				residualOb.setComments(modelDescripton);
				residualOb.setBand(SeriesType.Residuals);
				residualObs.add(residualOb);
	        }

		}

		@Override
		public List<PeriodAnalysisDataPoint> refineByFrequency(
				List<Double> freqs, List<Double> variablePeriods,
				List<Double> lockedPeriod) throws AlgorithmError {
			return null;
		}

		@Override
		public void execute() throws AlgorithmError {

			interrupted = false;
				
			int n_steps = (int)Math.ceil((maxFrequency - minFrequency) / resolution) + 1;
			double frequency = minFrequency;
				
			for (int i = 0; i < n_steps; i++) {
				if (interrupted)
					break;
					
				frequencies.add(frequency);
				periods.add(fixInf(1/frequency));
					
				ftResult.calculateF(frequency);
					
				powers.add(fixInf(ftResult.getPwr()));
				semiAmplitudes.add(fixInf(ftResult.getAmp()));
				frequency += resolution;
			}
		}

		// replace +-Infinity by NaN
		private double fixInf(double v) {
			if (Double.isInfinite(v))
				return Double.NaN;
			else
				return v;
		}

		@Override
		public void interrupt() {
			interrupted = true;
		}
	}

	// Ask user for frequency min, max, and resolution.
	private boolean parametersDialog() throws AlgorithmError {

		// We should invoke Swing dialogs in EDT.
		RunParametersDialog runParametersDialog = 
				new RunParametersDialog(
					minFrequency, 
					maxFrequency, 
					resolution, 
					analysisType);
		
		try {
			javax.swing.SwingUtilities.invokeAndWait(runParametersDialog);
		}
		catch (Exception e) {
			throw new AlgorithmError(e.getLocalizedMessage());
	    }
		
		if (!runParametersDialog.getDialogCancelled()) {
			minFrequency = runParametersDialog.getMinFrequency();
			maxFrequency = runParametersDialog.getMaxFrequency();
			resolution = runParametersDialog.getResolution();
			analysisType = runParametersDialog.getAnalysisType();
			return true;
		}
		
		return false;
	}

	private class RunParametersDialog implements Runnable {

		private double minFrequency; 
		private double maxFrequency; 
		private double resolution;
		private FAnalysisType analysisType;
		private boolean dialogCancelled;
	
		public RunParametersDialog(
				double minFrequency, 
				double maxFrequency, 
				double resolution, 
				FAnalysisType analysisType) {
			this.minFrequency = minFrequency;
			this.maxFrequency = maxFrequency;
			this.resolution = resolution;
			this.analysisType = analysisType;
		}
		
		public void run() {
			
			List<ITextComponent<?>> fields = new ArrayList<ITextComponent<?>>();

			DoubleField minFrequencyField = new DoubleField("Minimum Frequency", 0.0, null, minFrequency);
			fields.add(minFrequencyField);

			DoubleField maxFrequencyField = new DoubleField("Maximum Frequency", 0.0, null, maxFrequency);
			fields.add(maxFrequencyField);

			DoubleField resolutionField = new DoubleField("Resolution", 0.0, null, resolution);
			fields.add(resolutionField);

			JPanel analysisTypePane = new JPanel();
			analysisTypePane.setLayout(new GridLayout(showDCDFT ? 3 : 2, 1));
			analysisTypePane.setBorder(BorderFactory.createTitledBorder("Analysis Type"));
			ButtonGroup analysisTypeGroup = new ButtonGroup();
			JRadioButton dftRadioButton = new JRadioButton(FAnalysisType.DFT.label);
			analysisTypeGroup.add(dftRadioButton);
			analysisTypePane.add(dftRadioButton);
			JRadioButton spwRadioButton = new JRadioButton(FAnalysisType.SPW.label);
			analysisTypeGroup.add(spwRadioButton);
			analysisTypePane.add(spwRadioButton);
			JRadioButton dcdftRadioButton = new JRadioButton(FAnalysisType.DCDFT.label);			
			if (showDCDFT) {
				analysisTypeGroup.add(dcdftRadioButton);
				analysisTypePane.add(dcdftRadioButton);
			}

			//analysisTypePane.add(Box.createRigidArea(new Dimension(75, 10)));
			switch (analysisType) {
			case DFT:
				dftRadioButton.setSelected(true);
				break;
			case SPW:
				spwRadioButton.setSelected(true);
				break;
			default:
				dcdftRadioButton.setSelected(true);
			}
			
			while (true) {
				boolean legalParams = true;
				
				MultiEntryComponentDialog dlg = 
						new MultiEntryComponentDialog(
								"Parameters",
								getDocName(),						
								fields, 
								Optional.of(analysisTypePane));

				dialogCancelled = dlg.isCancelled();
				if (dialogCancelled)
					return;

				if (dftRadioButton.isSelected())
					analysisType = FAnalysisType.DFT;
				else if (spwRadioButton.isSelected())
					analysisType = FAnalysisType.SPW;
				else
					analysisType = FAnalysisType.DCDFT;
				
				minFrequency = minFrequencyField.getValue();
				maxFrequency = maxFrequencyField.getValue();
				resolution = resolutionField.getValue();
	
				if (minFrequency >= maxFrequency) {
					MessageBox.showErrorDialog("Parameters", 
							"Minimum frequency must be less than or equal to maximum frequency");
					legalParams = false;
				}
	
				if (resolution <= 0.0) {
					MessageBox.showErrorDialog("Parameters",
							"Resolution must be > 0");
					legalParams = false;
				}
				
				if (legalParams)
					break;
			}
			
	    }
		
		public double getMinFrequency() {
			return minFrequency;
		} 
		
		public double getMaxFrequency() {
			return maxFrequency;
		}
		
		public double getResolution() {
			return resolution;
		}
		
		public FAnalysisType getAnalysisType() {
			return analysisType;
		};
		
		public boolean getDialogCancelled() {
			return dialogCancelled;
		}

	}
	
	@Override
	public void interrupt() {
		// Executed in EDT thread.
		interrupted = true;
		if (algorithmCreated) {
			algorithm.interrupt();
		}
	}

	@Override
	protected void newStarAction(NewStarMessage message) {
		reset();
	}

	@Override
	public void reset() {
		cancelled = false;
		interrupted = false;
		algorithmCreated = false;
		resetParams = true;
		minFrequency = 0.0;
		maxFrequency = 0.0;
		resolution = null;
		analysisType = FAnalysisType.DFT;
		ftResult = null;
		if (resultDialogList != null) {
			List<PeriodAnalysisDialog> tempResultDialogList = resultDialogList;
			resultDialogList = null;
			for (PeriodAnalysisDialog dialog : tempResultDialogList) {
				dialog.setVisible(false);
				dialog.cleanup();
				dialog.dispose();
			}
		}
	}
	
	private static class FtResult {
		private double[] times;
		private double[] mags;
		private double maxTime;
		private double minTime;
		private double meanTime;
		private double meanMag;
		private double varpMag;
		private double medianTimeInterval;
		private int count;
		private FAnalysisType analysisType;
		
		OLSMultipleLinearRegression regression; // for DCDFT
		
		private double amp = 0.0;
		private double pwr = 0.0;
		
		public FtResult(List<ValidObservation> obs) {
			setAnalysisType(FAnalysisType.DFT);
			
			count = obs.size();
			times = new double[count];
			mags = new double[count];
			for (int i = 0; i < count; i++) {
				ValidObservation ob = obs.get(i);
				times[i] = ob.getJD();
				mags[i] = ob.getMag();
			}
			
			minTime = 0.0;
			maxTime = 0.0;
			meanTime = 0.0;
			meanMag = 0.0;
			boolean first = true;
			for (int i = 0; i < count; i++) {
				double t = times[i];
				double m = mags[i];
				if (first) {
					minTime = t;
					maxTime = minTime;
					first = false;
				} else {
					if (t < minTime)
						minTime = t;
					if (t > maxTime)
						maxTime = t;
				}
				meanTime += t;
				meanMag += m;
			}
			meanTime /= count;
			meanMag /= count;
			
			varpMag = calcPopVariance(mags);
			
			medianTimeInterval = calcMedianTimeInterval(times);
		}
		
		private double calcPopVariance(double d[]) {
			double mean = 0.0;
			double varp = 0.0;
			int count = d.length;
			for (int i = 0; i < count; i++) {
				mean += d[i];
			}
			mean = mean / count;
			for (int i = 0; i < count; i++) {
				varp += (d[i] - mean) * (d[i] - mean);
			}
			return varp / count;
		}
		
		private Double calcMedianTimeInterval(double[] times) {
			if (times.length < 2)
				return 0.0;
			List<Double> sorted_times = new ArrayList<Double>();
			for (Double t : times) {
				sorted_times.add(t);
			}
			sorted_times.sort(new DoubleComparator());
            double intervals[] = new double[times.length - 1];
			for (int i = 1; i < times.length; i++) {
				intervals[i - 1] = times[i] - times[i - 1];
			}
			Median median = new Median();
			return median.evaluate(intervals);
		}
		
		public void calculateF(double nu) {
	        double reF = 0.0;
            double imF = 0.0;
            double omega = 2 * Math.PI * nu;            
            if (analysisType != FAnalysisType.DCDFT) {
	            boolean typeIsDFT = analysisType != FAnalysisType.SPW;
	            for (int i = 0; i < count; i++) {
	            	double a = omega * times[i];
	            	double b = typeIsDFT ? mags[i] - meanMag : 0.5;
	                //reF += b * Math.cos(a);
	                //imF += b * Math.sin(a);
	            	// Faster than Math.sin, Math.cos
	           		double tanAd2 = Math.tan(a / 2.0);
	            	double tanAd2squared = tanAd2 * tanAd2;
	                reF += b * ((1 - tanAd2squared) / (1 + tanAd2squared));
	                imF += b * (2.0 * tanAd2 / (1 + tanAd2squared));
	            }
	            // Like Period04
	            amp = 2.0 * Math.sqrt(reF * reF + imF * imF) / count;
	            pwr = amp * amp;
            } else {
            	if (omega == 0) {
            		amp = Double.NaN;
            		pwr = Double.NaN;
            		return;
            	}
            	double[] a = new double[times.length];
            	double[][] cos_sin = new double[times.length][2];
            	for (int i = 0; i < times.length; i++) {
            		a[i] = omega * times[i];
            		//cos_sin[i][0] = Math.cos(a[i]);
            		//cos_sin[i][1] = Math.sin(a[i]);
	           		double tanAd2 = Math.tan(a[i] / 2.0);
	            	double tanAd2squared = tanAd2 * tanAd2;
	            	cos_sin[i][0] = (1 - tanAd2squared) / (1 + tanAd2squared);
	            	cos_sin[i][1] = (2.0 * tanAd2 / (1 + tanAd2squared));
            	}

    			regression.newSampleData(mags, cos_sin);
    			
    			double[] beta = regression.estimateRegressionParameters();
    			double b1 = beta[1];
    			double b2 = beta[2];
    			double[] predicted_mags = new double[times.length]; // excluding mag zero level, not needed
    			for (int i = 0; i < times.length; i++) {
    				predicted_mags[i] = b1 * cos_sin[i][0] + b2 * cos_sin[i][1];
    			}
            	amp = Math.sqrt(b1 * b1 + b2 * b2);
            	pwr = calcPopVariance(predicted_mags) * (times.length - 1) / varpMag / 2.0;
            }
		}

		public void setAnalysisType(FAnalysisType value) {
			analysisType = value;
			if (analysisType == FAnalysisType.DCDFT) {
    			regression = new OLSMultipleLinearRegression();
			}
		}
		
		public double getAmp() {
			return amp;
		}
		
		public double getPwr() {
			return pwr;
		}
		
		public double getMedianTimeInterval() {
			return medianTimeInterval;
		}
		
		public double getObservationTimeSpan() {
			return maxTime - minTime;
		}
		
		public double getObservationMeanTime() {
			return meanTime;
		}
		
	}
	
	private static class DoubleComparator implements Comparator<Double> {
	    @Override
	    public int compare(Double a, Double b) {
	        if (a > b) 
	        	return 1;
	        else if (a < b)
	        	return -1;
	        else
	        	return 0;
	    }
	}

	private static class IntDoublePair {
		public int i;
		public double d;
		public IntDoublePair(int i, double d) {
			this.i = i;
			this.d = d;
		}
	}
	
	private static class IntDoublePairComparator implements Comparator<IntDoublePair> {
		
		private boolean direct;
		
	    public IntDoublePairComparator(boolean direct) {
	    	this.direct = direct;
		}

		@Override
	    public int compare(IntDoublePair a, IntDoublePair b) {
	        if (a.d > b.d) 
	        	return direct ? 1 : -1;
	        else if (a.d < b.d)
	        	return direct ? -1 : 1;
	        else
	        	return 0;
	    }
	}
	
//////////////////////////////////////////////////////////////////////////////
// Unit test
//////////////////////////////////////////////////////////////////////////////

	@Override
	public Boolean test() {
		boolean success = true;
		
		setTestMode(true);
		try {
			DftTest test = new DftTest("DFT test");
			test.testDcDft();
	    } catch (Exception e) {
	    	//System.out.println(e.getMessage());
	        success = false;
	    }
		
		setTestMode(false);
		
		return success;
	}
	
	private static class DftTest {

		// part of V965 Cep CV data (PMAK)
		private static double[][] jdAndMagPairs = {
				{2459514.16149812,13.900},
				{2459514.16291815,13.830},
				{2459514.16433819,13.835},
				{2459514.16575822,13.823},
				{2459514.16717825,13.855},
				{2459514.16859829,13.824},
				{2459514.17001832,13.799},
				{2459514.17143835,13.803},
				{2459514.17285839,13.770},
				{2459514.17427842,13.764},
				{2459514.17569846,13.720},
				{2459514.17711849,13.694},
				{2459514.17853852,13.688},
				{2459514.17995856,13.680},
				{2459514.18137859,13.685},
				{2459514.18279862,13.674},
				{2459514.18421866,13.645},
				{2459514.18563869,13.609},
				{2459514.18705872,13.622},
				{2459514.18847876,13.611},
				{2459514.18989879,13.588},
				{2459514.19131882,13.585},
				{2459514.19273886,13.574},
				{2459514.19415889,13.557},
				{2459514.19558893,13.576},
				{2459514.19700896,13.588},
				{2459514.19842899,13.598},
				{2459514.19984903,13.597},
				{2459514.20125906,13.574},
				{2459514.20267909,13.621},
				{2459514.20409913,13.660},
				{2459514.20551916,13.641},
				{2459514.20693919,13.667},
				{2459514.20835923,13.694},
				{2459514.20977926,13.715},
				{2459514.21119929,13.706},
				{2459514.21261933,13.729},
				{2459514.21403936,13.687},
				{2459514.21545940,13.722},
				{2459514.21687943,13.732},
				{2459514.21829946,13.726},
				{2459514.21971950,13.779},
				{2459514.22113953,13.757},
				{2459514.22255956,13.788},
				{2459514.22397960,13.763},
				{2459514.22539963,13.788},
				{2459514.22681966,13.770},
				{2459514.22823970,13.793},
				{2459514.22965973,13.820},
				{2459514.23107976,13.800},
				{2459514.23249980,13.792},
				{2459514.23391983,13.812},
				{2459514.23533986,13.847},
				{2459514.23675990,13.845},
				{2459514.23816993,13.855},
				{2459514.23958996,13.858},
				{2459514.24101000,13.849},
				{2459514.24243003,13.826},
				{2459514.24933019,13.846},
				{2459514.25075023,13.896},
				{2459514.25217026,13.856},
				{2459514.25359029,13.842},
				{2459514.25501033,13.814},
				{2459514.25643036,13.802},
				{2459514.25785040,13.818},
				{2459514.25927043,13.765},
				{2459514.26069046,13.721},
				{2459514.26211050,13.723},
				{2459514.26353053,13.713},
				{2459514.26495056,13.692},
				{2459514.26636060,13.683},
				{2459514.26778063,13.666},
				{2459514.26920066,13.656},
				{2459514.27062070,13.613},
				{2459514.27204073,13.633},
				{2459514.27346076,13.594},
				{2459514.27488080,13.554},
				{2459514.27630083,13.559},
				{2459514.27772086,13.570},
				{2459514.27914090,13.543},
				{2459514.28056093,13.558},
				{2459514.28198096,13.532},
				{2459514.28340100,13.541},
				{2459514.28482103,13.598},
				{2459514.28624106,13.584},
				{2459514.28765110,13.597},
				{2459514.28907113,13.626},
				{2459514.29049117,13.657},
				{2459514.29191120,13.680},
				{2459514.29333123,13.680},
				{2459514.29475127,13.684},
				{2459514.29617130,13.695},
				{2459514.29759133,13.689},
				{2459514.29901137,13.718},
				{2459514.30043140,13.747},
				{2459514.30185143,13.750},
				{2459514.30327147,13.739},
				{2459514.30469150,13.745},
				{2459514.30611153,13.767},
				{2459514.30753157,13.761},
				{2459514.30894160,13.753},
				{2459514.31036163,13.804},
				{2459514.31178167,13.801},
				{2459514.31320170,13.803},
				{2459514.31462173,13.815},
				{2459514.31604177,13.836},
				{2459514.31746180,13.804},
				{2459514.31888183,13.815},
				{2459514.32030187,13.878},
				{2459514.32172190,13.835},
				{2459514.32314193,13.819},
				{2459514.32456197,13.866},
				{2459514.32598200,13.851},
				{2459519.15711839,13.764},
				{2459519.15858842,13.750},
				{2459519.16004845,13.783},
				{2459519.16151848,13.790},
				{2459519.16299851,13.783},
				{2459519.16446854,13.804},
				{2459519.16593857,13.811},
				{2459519.16740861,13.829},
				{2459519.16887864,13.821},
				{2459519.17034867,13.824},
				{2459519.17181870,13.830},
				{2459519.17328873,13.835},
				{2459519.17475876,13.830},
				{2459519.17622879,13.873},
				{2459519.17769882,13.850},
				{2459519.18188890,13.860},
				{2459519.18334893,13.847},
				{2459519.18481896,13.850},
				{2459519.18628899,13.832},
				{2459519.18775902,13.836},
				{2459519.18922905,13.790},
				{2459519.19069908,13.771},
				{2459519.19216911,13.748},
				{2459519.19363914,13.738},
				{2459519.19510917,13.729},
				{2459519.19657920,13.694},
				{2459519.19804923,13.673},
				{2459519.19951926,13.628},
				{2459519.20098929,13.635},
				{2459519.20245932,13.629},
				{2459519.20392935,13.618},
				{2459519.20539938,13.560},
				{2459519.20686941,13.585},
				{2459519.20833944,13.551},
				{2459519.20980947,13.594},
				{2459519.21127950,13.550},
				{2459519.21274953,13.559},
				{2459519.21421956,13.549},
				{2459519.21568959,13.556},
				{2459519.21715962,13.590},
				{2459519.21862965,13.600},
				{2459519.22009968,13.634},
				{2459519.22156971,13.631},
				{2459519.22303974,13.650},
				{2459519.22450977,13.669},
				{2459519.22597980,13.713},
				{2459519.22744983,13.672},
				{2459519.22891986,13.703},
				{2459519.23038989,13.714},
				{2459519.23185992,13.694},
				{2459519.23332995,13.737},
				{2459519.23479998,13.723},
				{2459519.23627001,13.740},
				{2459519.23775004,13.733},
				{2459519.23922007,13.783},
				{2459519.24068010,13.798},
				{2459519.24215013,13.780},
				{2459519.24362016,13.786},
				{2459519.24509019,13.802},
				{2459519.24656022,13.753},
				{2459519.26511060,13.847},
				{2459519.26658063,13.848},
				{2459519.26805066,13.839},
				{2459519.26952069,13.820},
				{2459519.27099072,13.823},
				{2459519.27245075,13.853},
				{2459519.27392078,13.790},
				{2459519.27539081,13.775},
				{2459519.27686084,13.748},
				{2459519.27833087,13.718},
				{2459519.27980090,13.718},
				{2459519.28127093,13.703},
				{2459519.28274096,13.665},
				{2459519.28421099,13.639},
				{2459519.28568102,13.655},
				{2459519.28715105,13.632},
				{2459519.28862108,13.604},
				{2459519.29009111,13.552},
				{2459519.29156114,13.545},
				{2459519.29303117,13.539},
				{2459519.29450120,13.547},
				{2459519.29597123,13.544},
				{2459519.29744126,13.563},
				{2459519.29891129,13.554},
				{2459519.30038132,13.559},
				{2459519.30185135,13.592},
				{2459519.30332138,13.577},
				{2459519.30479141,13.595},
				{2459519.30626144,13.614},
				{2459519.30773147,13.622},
				{2459519.30920150,13.642},
				{2459519.31067153,13.662},
				{2459519.31214156,13.683},
				{2459519.31361159,13.705},
				{2459519.31508162,13.725},
				{2459519.31655165,13.702},
				{2459519.31802168,13.736},
				{2459519.31949171,13.768},
				{2459519.32096174,13.738},
				{2459519.32243177,13.766},
				{2459519.32390180,13.763},
				{2459519.32537183,13.734},
				{2459519.32684186,13.794},
				{2459519.32831189,13.786},
				{2459519.32978192,13.790},
				{2459519.33125195,13.757},
				{2459519.33272198,13.768},
				{2459519.33419201,13.795},
				{2459519.33566204,13.827},
				{2459519.33713207,13.855},
				{2459519.33860210,13.826},
				{2459519.34007213,13.834},
				{2459519.34154216,13.841},
				{2459519.34301219,13.860},
				{2459519.34448222,13.813},
				{2459519.34595225,13.847},
				{2459519.34742228,13.842},
				{2459519.34889231,13.839}
		};
		
		private static double[][] expectedDftResult = {
				{0,Double.NaN,0,0.000000000000004},
				{0.009638750819301,103.74788380228297,0.000000006657359,0.000081592642906},
				{0.019277501638602,51.873941901141485,0.000000026556232,0.000162960830579},
				{0.028916252457903,34.582627934094326,0.000000059428436,0.000243779481415},
				{0.038555003277204,25.936970950570743,0.000000104675152,0.000323535394876},
				{0.048193754096505,20.749576760456595,0.000000161172909,0.000401463459587},
				{0.057832504915806,17.29131396704716,0.000000227064498,0.000476512852328},
				{0.067471255735107,14.821126257468995,0.000000299586442,0.000547344902101},
				{0.077110006554408,12.96848547528537,0.000000374985244,0.000612360387017},
				{0.086748757373709,11.527542644698107,0.000000448567096,0.000669751518364},
				{0.09638750819301,10.374788380228296,0.00000051491054,0.000717572672525},
				{0.106026259012311,9.431625800207541,0.000000568250378,0.00075382383759},
				{0.115665009831612,8.64565698352358,0.000000603016166,0.000776541155702},
				{0.125303760650913,7.980606446329458,0.000000614483183,0.000783889777534},
				{0.134942511470214,7.410563128734498,0.000000599470747,0.000774254962432},
				{0.144581262289515,6.916525586818865,0.000000557005741,0.000746328172625},
				{0.154220013108816,6.484242737642686,0.000000488860694,0.000699185736595},
				{0.163858763928117,6.10281669425194,0.000000399877684,0.000632358824958},
				{0.173497514747418,5.763771322349055,0.000000298002639,0.000545896179552},
				{0.183136265566719,5.460414936962263,0.000000193978749,0.000440430186092},
				{0.19277501638602,5.18739419011415,0.000000100680863,0.000317302478253},
				{0.202413767205321,4.940375419156333,0.00000003211224,0.000179198884609},
				{0.212052518024622,4.715812900103773,0.000000002126742,0.000046116610799},
				{0.221691268843923,4.510777556621001,0.000000022978716,0.000151587322213},
				{0.231330019663224,4.322828491761793,0.000000103835557,0.00032223525174},
				{0.240968770482525,4.149915352091321,0.000000249408763,0.000499408413083},
				{0.250607521301826,3.990303223164732,0.00000045886576,0.000677396309201},
				{0.260246272121127,3.842514214899371,0.000000725173916,0.000851571439366},
				{0.269885022940428,3.705281564367251,0.00000103499986,0.001017349428836},
				{0.279523773759729,3.577513234561484,0.000001369242439,0.00117014633216},
				{0.28916252457903,3.458262793409434,0.000001704219971,0.001305457762997},
				{0.298801275398331,3.346705929105904,0.00000201346584,0.001418966468889},
				{0.308440026217632,3.242121368821345,0.000002270018516,0.001506658062186},
				{0.318078777036933,3.143875266735849,0.00000244902816,0.001564937110625},
				{0.327717527856234,3.051408347125972,0.000002530450095,0.001590738852037},
				{0.337356278675535,2.964225251493801,0.000002501563192,0.001581633077515},
				{0.346995029494836,2.881885661174529,0.000002359039679,0.001535916559862},
				{0.356633780314137,2.803996859521163,0.000002110312348,0.001452691415317},
				{0.366272531133438,2.730207468481133,0.000001774028478,0.00133192660398},
				{0.375911281952739,2.660202148776488,0.00000137945122,0.001174500413034},
				{0.38555003277204,2.593697095057076,0.000000964760556,0.000982222253729},
				{0.395188783591341,2.530436190299587,0.000000574310829,0.000757832982434},
				{0.404827534410642,2.470187709578168,0.000000255011392,0.000504986526647},
				{0.414466285229943,2.412741483774024,0.000000052099584,0.000228253333206},
				{0.424105036049244,2.357906450051888,0.000000004660745,0.000068269649283},
				{0.433743786868545,2.305508528939623,0.00000014130814,0.000375909749231},
				{0.443382537687846,2.255388778310501,0.000000476458115,0.000690259455164},
				{0.453021288507147,2.207401783027299,0.000001007617297,0.001003801423246},
				{0.462660039326448,2.161414245880897,0.000001714037417,0.001309212518002},
				{0.472298790145749,2.117303751067001,0.000002556991926,0.001599059700651},
				{0.48193754096505,2.074957676045661,0.000003481793084,0.001865956345648},
				{0.491576291784351,2.034272231417315,0.000004421510235,0.002102738746188},
				{0.501215042603652,1.995151611582366,0.00000530218053,0.00230264641871},
				{0.510853793422953,1.95750724155251,0.000006049143595,0.002459500679994},
				{0.520492544242254,1.921257107449686,0.000006593988956,0.002567876351321},
				{0.530131295061555,1.88632516004151,0.000006881505781,0.002623262430895},
				{0.539770045880856,1.852640782183626,0.000006875972344,0.002622207532552},
				{0.549408796700157,1.820138312320755,0.000006566131729,0.002562446434461},
				{0.559047547519458,1.788756617280742,0.000005968273004,0.002443004912736},
				{0.568686298338759,1.758438708513272,0.000005126972695,0.002264281938041},
				{0.57832504915806,1.729131396704717,0.000004113237454,0.002028111795214},
				{0.587963799977361,1.700784980365296,0.000003020018246,0.001737819969354},
				{0.597602550796662,1.673352964552952,0.000001955313805,0.001398325357312},
				{0.607241301615963,1.646791806385445,0.000001033326782,0.00101652682293},
				{0.616880052435264,1.621060684410673,0.000000364357587,0.000603620399997},
				{0.626518803254565,1.596121289265893,0.000000044291473,0.000210455394298},
				{0.636157554073866,1.571937633367925,0.000000144638191,0.000380313279775},
				{0.645796304893167,1.548475877646016,0.000000704101702,0.00083910768177},
				{0.655435055712468,1.525704173562986,0.000001722584675,0.001312472732955},
				{0.665073806531769,1.503592518873667,0.000003158368477,0.001777179922623},
				{0.674712557351069,1.482112625746901,0.000004928962878,0.002220126770596},
				{0.68435130817037,1.461237800032156,0.000006915808171,0.002629792419677},
				{0.693990058989671,1.440942830587265,0.000008972663345,0.00299544042589},
				{0.703628809808972,1.421203887702508,0.000010937154167,0.003307136853334},
				{0.713267560628273,1.401998429760582,0.000012644622154,0.003555927748635},
				{0.722906311447574,1.383305117363774,0.000013943138288,0.003734051189787},
				{0.732545062266875,1.365103734240567,0.000014708360922,0.003835148096457},
				{0.742183813086176,1.347375114315364,0.000014856841596,0.003854457367254},
				{0.751822563905477,1.330101074388244,0.000014356433549,0.00378898845991},
				{0.761461314724778,1.313264351927634,0.000013232642392,0.003637669912493},
				{0.771100065544079,1.296848547528538,0.000011570061109,0.003401479253109},
				{0.78073881636338,1.280838071633124,0.000009508436183,0.003083575227453},
				{0.790377567182681,1.265218095149793,0.000007233383642,0.002689495053332},
				{0.800016318001982,1.249974503641964,0.000004962277295,0.002227616954318},
				{0.809655068821283,1.235093854789084,0.000002926314275,0.001710647326303},
				{0.819293819640584,1.220563338850389,0.000001350181926,0.001161973289678},
				{0.828932570459885,1.206370741887012,0.000000431065294,0.000656555629089},
				{0.838571321279186,1.192504411520495,0.000000318900652,0.000564712893494},
				{0.848210072098487,1.178953225025944,0.000001099781962,0.001048704897619},
				{0.857848822917788,1.165706559576214,0.000002784248645,0.001668606797693},
				{0.867487573737089,1.152754264469812,0.000005301832111,0.002302570761398},
				{0.87712632455639,1.140086635189924,0.00000850273779,0.002915945436664},
				{0.886765075375691,1.127694389155251,0.000012166928096,0.003488112397285},
				{0.896403826194992,1.115568643035302,0.000016020196441,0.00400252375895},
				{0.906042577014293,1.10370089151365,0.000019756153285,0.004444789453449},
				{0.915681327833594,1.092082987392453,0.000023062428537,0.004802335737687},
				{0.925320078652895,1.080707122940449,0.000025648910317,0.005064475324906},
				{0.934958829472196,1.069565812394671,0.000027275525272,0.005222597559848},
				{0.944597580291497,1.058651875533501,0.00002777697084,0.005270386213591},
				{0.954236331110798,1.047958422245284,0.000027081945834,0.005204031690295},
				{0.963875081930099,1.037478838022831,0.00002522480114,0.005022429804388},
				{0.9735138327494,1.027206770319634,0.000022348124119,0.004727380259576},
				{0.983152583568701,1.017136115708658,0.000018695527105,0.004323832455735},
				{0.992791334388002,1.007261007789156,0.000014594777155,0.003820311133318},
				{1.002430085207303,0.997575805791183,0.000010432311932,0.003229908966593},
				{1.012068836026604,0.988075083831267,0.000006621032194,0.002573136645049},
				{1.021707586845905,0.978753620776255,0.000003563988331,0.001887852836259},
				{1.031346337665206,0.969606390675543,0.000001617091713,0.001271649209831},
				{1.040985088484507,0.960628553724843,0.000001054234838,0.001026759386459},
				{1.050623839303808,0.951815447727368,0.000002038154389,0.001427639446386},
				{1.060262590123109,0.943162580020755,0.00000460001069,0.002144763551157},
				{1.06990134094241,0.934665619840388,0.000008630000694,0.002937686282461},
				{1.079540091761711,0.926320391091813,0.000013880419178,0.003725643458283},
				{1.089178842581012,0.91812286550693,0.000019981503566,0.004470067512518},
				{1.098817593400313,0.910069156160378,0.000026469231405,0.005144825692382},
				{1.108456344219614,0.902155511324201,0.000032823100096,0.00572914479623},
				{1.118095095038915,0.894378308640371,0.000038510895647,0.006205714757137},
				{1.127733845858216,0.886734049592163,0.000043036674155,0.006560234306435},
				{1.137372596677517,0.879219354256636,0.000045987698368,0.006781423034127},
				{1.147011347496818,0.871830956321706,0.000047075966311,0.006861192776102},
				{1.156650098316119,0.864565698352359,0.000046170266302,0.00679487058761},
				{1.16628884913542,0.857420527291596,0.000043315372958,0.006581441556225},
				{1.175927599954721,0.850392490182648,0.000038736021673,0.006223826931474},
				{1.185566350774022,0.843478730099862,0.000032824607748,0.005729276372103},
				{1.195205101593323,0.836676482276476,0.000026112990009,0.005110087084301},
				{1.204843852412624,0.829983070418265,0.000019230273556,0.004385233580504},
				{1.214482603231925,0.823395903192723,0.000012849815533,0.003584663935825},
				{1.224121354051226,0.816912470884119,0.000007629835114,0.002762215616879},
				{1.233760104870527,0.810530342205336,0.000004152797172,0.002037841301888},
				{1.243398855689828,0.804247161258008,0.00000286907237,0.001693833631107},
				{1.253037606509129,0.798060644632947,0.000004050229701,0.002012518248691},
				{1.26267635732843,0.791968578643382,0.000007756660909,0.002785078259073},
				{1.272315108147731,0.785968816683963,0.000013823118859,0.003717945515927},
				{1.281953858967032,0.780059276708895,0.000021864263388,0.004675923800537},
				{1.291592609786333,0.774237938823008,0.000031300548373,0.005594689300809},
				{1.301231360605634,0.768502842979875,0.00004140293478,0.006434511230827},
				{1.310870111424935,0.762852086781493,0.000051353116447,0.007166108877729},
				{1.320508862244236,0.757283823374329,0.00006031438396,0.007766233576213},
				{1.330147613063537,0.751796259436834,0.000067507067578,0.008216268470406},
				{1.339786363882838,0.746387653253835,0.000072281839801,0.008501872723155},
				{1.349425114702139,0.741056312873451,0.000074184068593,0.008613017391864},
				{1.35906386552144,0.735800594342433,0.000073002977086,0.008544177964313},
				{1.368702616340741,0.730618900016078,0.000068800486546,0.008294605870437},
				{1.378341367160042,0.725509676939042,0.000061916304487,0.007868691408811},
				{1.387980117979343,0.720471415293632,0.000052947851049,0.007276527403158},
				{1.397618868798644,0.715502646912297,0.000042705874029,0.006534973146731},
				{1.407257619617945,0.710601943851254,0.000032148873299,0.005669997645439},
				{1.416896370437246,0.705767917022334,0.000022301516248,0.00472244812016},
				{1.426535121256547,0.700999214880291,0.000014163897698,0.00376349540953},
				{1.436173872075848,0.696294522162974,0.000008619600872,0.002935915678635},
				{1.445812622895149,0.691652558681887,0.000006350928663,0.00252010489128},
				{1.45545137371445,0.687072078160815,0.000007769328745,0.002787351564713},
				{1.465090124533751,0.682551867120283,0.000012967950704,0.003601104095176},
				{1.474728875353052,0.678090743805772,0.000021701494107,0.004658486246344},
				{1.484367626172353,0.673687557157682,0.000033396199384,0.005778944487028},
				{1.494006376991654,0.669341185821181,0.000047190168598,0.00686951006975},
				{1.503645127810955,0.665050537194122,0.000062001434674,0.007874098975349},
				{1.513283878630256,0.660814546511357,0.000076618553339,0.008753202461924},
				{1.522922629449557,0.656632175963817,0.000089806243225,0.00947661559974},
				{1.532561380268858,0.652502413850837,0.000100416941654,0.010020825397819},
				{1.542200131088159,0.648424273764269,0.000107498300727,0.010368138730111},
				{1.55183888190746,0.644396793803,0.000110386634766,0.010506504402791},
				{1.561477632726761,0.640419035816562,0.000108777290272,0.010429635193626},
				{1.571116383546062,0.636490084676583,0.000102764707934,0.010137292929269},
				{1.580755134365363,0.632609047574897,0.000092847455919,0.009635738472941},
				{1.590393885184664,0.62877505334717,0.00007989659,0.008938489245951},
				{1.600032636003965,0.624987251820982,0.000065088963663,0.008067773153911},
				{1.609671386823265,0.621244813187324,0.000049810414914,0.007057649390137},
				{1.619310137642566,0.617546927394542,0.000035536654465,0.005961262824728},
				{1.628948888461867,0.613892803563805,0.000023702001682,0.004868470158316},
				{1.638587639281168,0.610281669425195,0.000015567557035,0.003945574360587},
				{1.648226390100469,0.606712770773585,0.000012100842898,0.003478626582143},
				{1.65786514091977,0.603185370943506,0.00001387828321,0.00372535678966},
				{1.667503891739071,0.599698750302214,0.000021020181224,0.004584777118208},
				{1.677142642558372,0.596252205760248,0.000033165183445,0.005758922073184},
				{1.686781393377673,0.59284505029876,0.000049487834254,0.007034759004685},
				{1.696420144196974,0.589476612512972,0.000068758998227,0.008292104571644},
				{1.706058895016275,0.586146236171091,0.000089444982403,0.009457535746828},
				{1.715697645835576,0.582853279788107,0.000109837539691,0.010480340628585},
				{1.725336396654877,0.579597116213872,0.0001282038254,0.011322712810968},
				{1.734975147474178,0.576377132234906,0.000142943255578,0.01195588790421},
				{1.744613898293479,0.573192728189409,0.000152737133104,0.012358686544459},
				{1.75425264911278,0.570043317594962,0.000156677164263,0.012517074908438},
				{1.763891399932081,0.566928326788432,0.000154360483122,0.012424189435195},
				{1.773530150751382,0.563847194577625,0.000145941479875,0.012080624150875},
				{1.783168901570683,0.560799371904233,0.000132134406457,0.011494973095111},
				{1.792807652389984,0.557784321517651,0.000114165034496,0.010684803905354},
				{1.802446403209285,0.554801517659268,0.00009367426727,0.00967854675402},
				{1.812085154028586,0.551850445756825,0.000072581080287,0.008519453050958},
				{1.821723904847887,0.548930602128482,0.000052916107238,0.00727434582886},
				{1.831362655667188,0.546041493696227,0.00003664020537,0.006053115344225},
				{1.841001406486489,0.543182637708288,0.000025464128937,0.005046199454756},
				{1.85064015730579,0.540353561470224,0.000020685808319,0.004548165379481},
				{1.860278908125091,0.537553802084368,0.000023060590488,0.004802144363529},
				{1.869917658944392,0.534782906197335,0.000032717231952,0.005719897896967},
				{1.879556409763693,0.532040429755298,0.000049128610633,0.007009180453732},
				{1.889195160582994,0.52932593776675,0.000071141371033,0.008434534428921},
				{1.898833911402295,0.526639004072503,0.000097063453484,0.009852078637704},
				{1.908472662221596,0.523979211122642,0.000124803123793,0.011171531846287},
				{1.918111413040897,0.521346149760216,0.000152048247251,0.012330784535087},
				{1.927750163860198,0.518739419011415,0.00017647054144,0.013284221521789},
				{1.937388914679499,0.516158625882005,0.000195936863509,0.013997744943696},
				{1.9470276654988,0.513603385159817,0.000208708428226,0.014446744554593},
				{1.956666416318101,0.511073319223069,0.000213609451339,0.014615384064029},
				{1.966305167137402,0.508568057854329,0.000210149012333,0.014496517248389},
				{1.975943917956703,0.506087238059917,0.00019858376278,0.01409197511991},
				{1.985582668776004,0.503630503894578,0.000179914193759,0.013413209674023},
				{1.995221419595305,0.501197506291223,0.000155813043248,0.012482509493187},
				{2.004860170414606,0.498787902895592,0.000128490609149,0.011335369828493},
				{2.014498921233908,0.496401357905661,0.000100507607789,0.01002534826274},
				{2.024137672053209,0.494037541915634,0.000074551296237,0.008634309250689},
				{2.03377642287251,0.491696131764375,0.00005319434645,0.007293445444392},
				{2.043415173691811,0.489376810388127,0.000038658020331,0.006217557424815},
				{2.053053924511112,0.487079266677385,0.000032601376141,0.005709761478491},
				{2.062692675330414,0.484803195337771,0.000035956379375,0.005996363846149},
				{2.072331426149715,0.482548296754805,0.000048825123421,0.006987497650854},
				{2.081970176969016,0.480314276862421,0.000070450086494,0.008393454979578},
				{2.091608927788317,0.478100847015129,0.000099261938616,0.009963028586531},
				{2.101247678607618,0.475907723863683,0.000133002460449,0.01153266926819},
				{2.11088642942692,0.473734629234169,0.000168913190172,0.012996660731586},
				{2.120525180246221,0.471581290010377,0.000203974196201,0.014281953514878},
				{2.130163931065522,0.46944743801938,0.000235172403427,0.01533533186558},
				{2.139802681884823,0.467332809920193,0.000259775708796,0.016117559021022},
				{2.149441432704124,0.465237147095439,0.000275588020293,0.016600843963288},
				{2.159080183523426,0.463160195545906,0.000281161509596,0.016767871349587},
				{2.168718934342727,0.461101705787924,0.000275945695927,0.016611613284904},
				{2.178357685162028,0.459061432753464,0.000260358300623,0.016135622102153},
				{2.187996435981329,0.457039135692876,0.000235769564288,0.015354789620448},
				{2.197635186800631,0.455034578080188,0.000204399474903,0.014296834436447},
				{2.207273937619932,0.453047527520886,0.00016913526285,0.013005201376747},
				{2.216912688439233,0.451077755662099,0.00013328396863,0.011544867631561},
				{2.226551439258534,0.44912503810512,0.00010028122437,0.010014051346502},
				{2.236190190077835,0.447189154320185,0.000073381738807,0.008566314190306},
				{2.245828940897137,0.445269887563446,0.000055359380712,0.007440388478556},
				{2.255467691716438,0.44336702479608,0.000048244368927,0.00694581664942},
				{2.265106442535739,0.441480356605459,0.000053122386172,0.007288510559215},
				{2.27474519335504,0.439609677128317,0.00007001529338,0.008367514169674},
				{2.284383944174341,0.437754783975877,0.00009785610337,0.009892224389388},
				{2.294022694993643,0.435915478160852,0.000134562562111,0.011600110435293},
				{2.303661445812944,0.434091564026288,0.00017720474365,0.013311827209298},
				{2.313300196632245,0.432282849176178,0.000222253413002,0.014908165983863},
				{2.322938947451546,0.430489144407812,0.000265888220226,0.016306079241363},
				{2.332577698270847,0.428710263645796,0.000304338914882,0.017445312117629},
				{2.342216449090149,0.426946023877707,0.000334229153223,0.018281935160773},
				{2.35185519990945,0.425196245091323,0.000352891598125,0.018785409181732},
				{2.361493950728751,0.423460750213399,0.000358625000048,0.018937396865667},
				{2.371132701548052,0.42173936504993,0.000350868614248,0.018731487240686},
				{2.380771452367354,0.420031918227865,0.000330276354261,0.018173506933471},
				{2.390410203186655,0.418338241138237,0.000298681845611,0.017282414345553},
				{2.400048954005956,0.416658167880653,0.000258955410142,0.01609209154032},
				{2.409687704825257,0.414991535209131,0.000214763766723,0.014654820596749},
				{2.419326455644558,0.413338182479214,0.000170252465488,0.013048082828082},
				{2.42896520646386,0.41169795159636,0.000129678419491,0.011387643280801},
				{2.438603957283161,0.410070686965544,0.000097025093933,0.009850131670868},
				{2.448242708102462,0.408456235442058,0.000075635042727,0.008696840962479},
				{2.457881458921763,0.406854446283461,0.000067893663025,0.008239761102449},
				{2.467520209741064,0.405265171102667,0.000074993980829,0.008659906513844},
				{2.477158960560366,0.403688263822111,0.000096805466928,0.009838976924886},
				{2.486797711379667,0.402123580629002,0.000131860840792,0.011483067568924},
				{2.496436462198968,0.400570979931593,0.000177464308942,0.013321573065609},
				{2.506075213018269,0.399030322316472,0.000229913669983,0.015162904404599},
				{2.515713963837571,0.39750147050683,0.000284818174767,0.016876556958298},
				{2.525352714656872,0.395984289321689,0.000337484964132,0.01837076384183},
				{2.534991465476173,0.394478645636055,0.000383340129679,0.01957907376969},
				{2.544630216295474,0.392984408341979,0.00041834665223,0.02045352420074},
				{2.554268967114775,0.3915014483105,0.000439381062817,0.020961418435233},
				{2.563907717934077,0.390029638354446,0.000444533785018,0.021083969859066},
				{2.573546468753378,0.388568853192069,0.000433304329579,0.020815963335351},
				{2.583185219572679,0.387118969411502,0.000406671747267,0.020166103918886},
				{2.59282397039198,0.385679865435995,0.000367031639887,0.019158069837188},
				{2.602462721211281,0.384251421489935,0.000318003138365,0.017832642495304},
				{2.612101472030583,0.382833519565618,0.00026412114776,0.016251804446262},
				{2.621740222849884,0.381426043390745,0.000210439883527,0.014506546230123},
				{2.631378973669185,0.380028878396639,0.000162082331668,0.012731155943906},
				{2.641017724488486,0.378641911687162,0.000123775730429,0.011125454167297},
				{2.650656475307787,0.3772650320083,0.000099415176643,0.009970715954389},
				{2.660295226127089,0.375898129718415,0.000091695600252,0.009575781965559},
				{2.66993397694639,0.374541096759142,0.000101846814938,0.01009191829821},
				{2.679572727765691,0.373193826626915,0.000129497481828,0.011379696034069},
				{2.689211478584992,0.371856214345098,0.000172682571322,0.013140874069939},
				{2.698850229404294,0.370528156436723,0.000227995961778,0.015099535151073},
				{2.708488980223595,0.369209550897802,0.000290876706496,0.017055107929769},
				{2.718127731042896,0.367900297171214,0.000356005009312,0.018868095010161},
				{2.727766481862197,0.366600296121139,0.00041777363233,0.020439511548231},
				{2.737405232681498,0.365309450008037,0.000470792980329,0.021697764408542},
				{2.7470439835008,0.364027662464149,0.000510384380292,0.022591688301062},
				{2.756682734320101,0.362754838469519,0.000533016415184,0.023087148268776},
				{2.766321485139402,0.36149088432851,0.00053664366768,0.02316557073935},
				{2.775960235958703,0.360235707646814,0.000520915484764,0.02282357300609},
				{2.785598986778004,0.358989217308936,0.000487233723212,0.022073371360352},
				{2.795237737597306,0.357751323456146,0.000438651910341,0.020944018485981},
				{2.804876488416607,0.356521937464888,0.000379622553331,0.019483904981573},
				{2.814515239235908,0.355300971925625,0.000315613362382,0.017765510473442},
				{2.824153990055209,0.354088340622124,0.000252625358471,0.01589419260206},
				{2.83379274087451,0.352883958511165,0.000196655287892,0.014023383610657},
				{2.843431491693812,0.351687741702652,0.000153150551306,0.012375401056377},
				{2.853070242513113,0.350499607440143,0.000126506128361,0.011247494314778},
				{2.862708993332414,0.349319474081759,0.000119649993583,0.010938463949868},
				{2.872347744151715,0.348147261081484,0.000133756055871,0.011565295321378},
				{2.881986494971017,0.346982888970844,0.000168112596551,0.012965824175524},
				{2.891625245790318,0.345826279340941,0.00022016042247,0.014837803829071},
				{2.901263996609619,0.344677354824858,0.000285699654955,0.016902652305335},
				{2.91090274742892,0.343536039080405,0.000359248679755,0.01895385659318},
				{2.920541498248221,0.342402256773209,0.00043452468563,0.020845255710354},
				{2.930180249067523,0.341275933560139,0.000505003683778,0.022472287017071},
				{2.939818999886824,0.340156996073057,0.000564510103531,0.02375942136356},
				{2.949457750706125,0.339045371902883,0.000607782641732,0.02465324809699},
				{2.959096501525426,0.337940989583981,0.000630964511105,0.02511900696893},
				{2.968735252344727,0.336843778578839,0.000631972425168,0.02513906174},
				{2.978374003164029,0.33575366926305,0.000610709065928,0.024712528521535},
				{2.98801275398333,0.334670592910588,0.00056909769829,0.023855768658536},
				{2.997651504802631,0.333594481679364,0.000510933348917,0.022603834827671},
				{3.007290255621932,0.332525268597059,0.000441561825819,0.021013372547479},
				{3.016929006441234,0.331462887547228,0.000367413560476,0.019168034862135},
				{3.026567757260535,0.330407273255676,0.000295432872298,0.017188160817793},
				{3.036206508079836,0.329358361277087,0.000232453341818,0.015246420623142},
				{3.045845258899137,0.328316087981906,0.000184575500567,0.013585856637207},
				{3.055484009718438,0.327280390543477,0.000156603533403,0.012514133346079},
				{3.06512276053774,0.326251206925416,0.000151593037984,0.012312312454777},
				{3.074761511357041,0.325228475869223,0.000170552370135,0.013059570059333},
				{3.084400262176342,0.324212136882132,0.000212326632729,0.014571432075442},
				{3.094039012995643,0.323202130225178,0.000273677071208,0.016543188060575},
				{3.103677763814944,0.322198396901498,0.000349550946206,0.018696281614423},
				{3.113316514634246,0.321200878644837,0.000433519584292,0.020821133117383},
				{3.122955265453547,0.320209517908278,0.000518346615495,0.022767226785328},
				{3.132594016272848,0.319224257853176,0.000596636232303,0.024426138301065},
				{3.142232767092149,0.318245042338289,0.000661503277083,0.025719706006927},
				{3.15187151791145,0.31727181590912,0.000707204452969,0.026593315945338},
				{3.161510268730752,0.316304523787446,0.000729672695899,0.02701245445899},
				{3.171149019550053,0.31534311186104,0.000726904979938,0.026961175418338},
				{3.180787770369354,0.314387526673582,0.000699166675102,0.026441760060588},
				{3.190426521188655,0.31343771541475,0.000648991741287,0.025475316313784},
				{3.200065272007957,0.312493625910489,0.000580976661478,0.024103457459007},
				{3.209704022827258,0.31155520661346,0.000501384564125,0.022391618166747},
				{3.219342773646559,0.310622406593659,0.000417593633824,0.020435107874055},
				{3.22898152446586,0.309695175529201,0.000337438348162,0.018369495043745},
				{3.238620275285161,0.308773463697268,0.000268502454232,0.016386044496203},
				{3.248259026104463,0.307857221965229,0.00021742752027,0.014745423706008},
				{3.257897776923764,0.3069464017819,0.000189300194302,0.013758640714193},
				{3.267536527743065,0.306040955168974,0.000187174755313,0.013681182526114},
				{3.277175278562366,0.305140834712595,0.000211775740581,0.0145525166408},
				{3.286814029381667,0.30424599355508,0.000261409548072,0.016168164647588},
				{3.296452780200969,0.30335638538679,0.000332095047001,0.01822347516257},
				{3.30609153102027,0.30247196443814,0.000417903467888,0.02044268739398},
				{3.315730281839571,0.30159268547175,0.000511478606989,0.02261589279664},
				{3.325369032658872,0.300718503774731,0.000604691671903,0.024590479293876},
				{3.335007783478174,0.299849375151104,0.000689372465002,0.02625590343145},
				{3.344646534297475,0.298985255914358,0.000758050999349,0.027532725970184},
				{3.354285285116776,0.298126102880121,0.000804642166955,0.02836621523847},
				{3.363924035936077,0.297271873358975,0.000825010611131,0.028722997948184},
				{3.373562786755378,0.296422525149377,0.0008173632889,0.028589566084501},
				{3.38320153757468,0.295578016530718,0.000782432567682,0.027971996133317},
				{3.392840288393981,0.294738306256483,0.000723431204113,0.026896676451051},
				{3.402479039213282,0.293903353547541,0.000645781522794,0.025412231755466},
				{3.412117790032583,0.293073118085543,0.000556641388586,0.023593248792527},
				{3.421756540851884,0.292247560006428,0.000464268469806,0.021546890026304},
				{3.431395291671186,0.291426639894051,0.000377279305049,0.019423678978234},
				{3.441034042490487,0.290610318773899,0.00030386976666,0.017431860676929},
				{3.450672793309788,0.289798558106933,0.000251067484674,0.015845109172052},
				{3.460311544129089,0.288991319783515,0.000224084631127,0.014969456607612},
				{3.46995029494839,0.28818856611745,0.00022583065503,0.015027662992949},
				{3.479589045767692,0.287390259840117,0.000256630603236,0.016019694230409},
				{3.489227796586993,0.286596364094702,0.000314176236008,0.017725017235773},
				{3.498866547406294,0.285806842430529,0.000393716125164,0.019842281248984},
				{3.508505298225595,0.285021658797478,0.000488469211648,0.022101339589447},
				{3.518144049044897,0.284240777540499,0.000590225862434,0.024294564462739},
				{3.527782799864198,0.283464163394213,0.000690083044966,0.026269431759474},
				{3.537421550683499,0.282691781477608,0.00077924772528,0.027915008960775},
				{3.5470603015028,0.28192359728881,0.000849835798449,0.02915194330485},
				{3.556699052322101,0.281159576699951,0.00089559393102,0.029926475419274},
				{3.566337803141403,0.280399685952114,0.000912477990427,0.030207250626746},
				{3.575976553960704,0.279643891650356,0.000899034560519,0.029983905024507},
				{3.585615304780005,0.278892160758823,0.000856549525864,0.029266867373598},
				{3.595254055599306,0.27814446059593,0.00078894861478,0.028088229114354},
				{3.604892806418607,0.277400758829631,0.000702457497375,0.026503914755645},
				{3.614531557237909,0.276661023472752,0.000605050714359,0.024597778646834},
				{3.62417030805721,0.27592522287841,0.000505738391364,0.022488628045391},
				{3.633809058876511,0.275193325735496,0.000413754568598,0.020340957907589},
				{3.643447809695812,0.274465301064238,0.000337720409258,0.01837717087199},
				{3.653086560515113,0.273741118211826,0.00028485830526,0.016877745858391},
				{3.662725311334415,0.273020746848111,0.000260328597517,0.01613470165564},
				{3.672364062153716,0.27230415696137,0.000266749986654,0.016332482562502},
				{3.682002812973017,0.271591318854141,0.000303948249132,0.017434111653073},
				{3.691641563792318,0.270882203139117,0.000368957362562,0.019208262872065},
				{3.70128031461162,0.270176780735109,0.000456274244897,0.021360576885857},
				{3.710919065430921,0.26947502286307,0.000558345318676,0.023629331744173},
				{3.720557816250222,0.268776901042181,0.000666241742276,0.025811659037643},
				{3.730196567069523,0.268082387086,0.000770462909708,0.027757213651738},
				{3.739835317888824,0.267391453098665,0.000861795710178,0.029356357236175},
				{3.749474068708126,0.266704071471162,0.000932151710366,0.030531159662979},
				{3.759112819527427,0.266020214877646,0.000975305976866,0.031229889158713},
				{3.768751570346728,0.265339856271821,0.000987469960964,0.031424034765833},
				{3.778390321166029,0.264662968883372,0.000967645534907,0.031107001380837},
				{3.78802907198533,0.263989526214458,0.00091772712445,0.030294011362804},
				{3.797667822804632,0.263319502036249,0.000842341552635,0.029023121000939},
				{3.807306573623933,0.262652870385524,0.00074843904549,0.027357614031373},
				{3.816945324443234,0.261989605561318,0.000644671673102,0.025390385446105},
				{3.826584075262535,0.261329682121617,0.000540614900103,0.02325112685662},
				{3.836222826081837,0.260673074880105,0.000445902358397,0.021116400223446},
				{3.845861576901138,0.260019758902962,0.000369352307977,0.019218540734847},
				{3.855500327720439,0.259369709505705,0.000318165097034,0.017837182990431},
				{3.86513907853974,0.25872290225008,0.000297264869495,0.017241370870527},
				{3.874777829359041,0.258079312941,0.000308845791363,0.01757400897244},
				{3.884416580178343,0.257438917623528,0.000352164782585,0.018766053996123},
				{3.894055330997644,0.256801692579906,0.000423600277864,0.020581551881812},
				{3.903694081816945,0.256167614326622,0.000516972505245,0.022737029384786},
				{3.913332832636246,0.255536659611532,0.00062409690824,0.024981931635488},
				{3.922971583455547,0.254908805411012,0.000735521026641,0.027120490899713},
				{3.932610334274849,0.254284028927162,0.000841378373107,0.029006522940651},
				{3.94224908509415,0.253662307585041,0.000932281935049,0.030533292240588},
				{3.951887835913451,0.253043619029956,0.001000176240833,0.031625563091157},
				{3.961526586732752,0.252427941124773,0.001039070422695,0.032234615286913},
				{3.971165337552053,0.251815251947286,0.001045585374175,0.03233551258562},
				{3.980804088371355,0.251205529787607,0.001019264983261,0.031925929638162},
				{3.990442839190656,0.250598753145608,0.000962622582533,0.031026159648483},
				{4.000081590009957,0.24999490072839,0.000880918027363,0.029680263263041},
				{4.009720340829258,0.249393951447793,0.000781685132389,0.027958632520013},
				{4.019359091648559,0.248795884417942,0.000674052061107,0.025962512611596},
				{4.028997842467859,0.248200678952828,0.000567916106504,0.023830990464187},
				{4.03863659328716,0.247608314563919,0.000473047627643,0.021749658104046},
				{4.048275344106461,0.247018770957814,0.000398204546238,0.019955063172979},
				{4.057914094925762,0.246432028033924,0.000350337762667,0.018717311844041},
				{4.067552845745062,0.245848065882185,0.000333959869976,0.0182745689409},
				{4.077191596564363,0.245266864780808,0.00035073458076,0.018727909140098},
				{4.086830347383664,0.244688405194061,0.0003993243765,0.019983102274182},
				{4.096469098202965,0.244112667770075,0.000475510329485,0.02180619933608},
				{4.106107849022266,0.24353963333869,0.000572573380912,0.023928505613857},
				{4.115746599841566,0.242969282909325,0.000681902489861,0.026113262719564},
				{4.125385350660867,0.242401597668883,0.000793774505396,0.028174004071059},
				{4.135024101480168,0.241836558979678,0.000898234916176,0.029970567498403},
				{4.144662852299469,0.2412741483774,0.000985999487346,0.031400628773094},
				{4.154301603118769,0.2407143475691,0.001049294884894,0.032392821502514},
				{4.16394035393807,0.240157138431208,0.001082561990987,0.03290230981233},
				{4.173579104757371,0.23960250300758,0.001082958052982,0.032908328018633},
				{4.183217855576672,0.239050423507562,0.001050612201792,0.032413148594241},
				{4.192856606395972,0.238500882304097,0.000988611292809,0.031442189694883},
				{4.202495357215273,0.23795386193184,0.000902717602185,0.030045259229786},
				{4.212134108034574,0.237409345085314,0.000800844156826,0.028299190038338},
				{4.221772858853875,0.236867314617082,0.000692335768842,0.026312274110041},
				{4.231411609673176,0.23632775353595,0.000587121088409,0.024230581677058},
				{4.241050360492476,0.235790645005187,0.000494812980714,0.02224439211834},
				{4.250689111311777,0.235255972340776,0.000423838882138,0.020587347622701},
				{4.260327862131078,0.234723719009688,0.000380680081085,0.019511024603682},
				{4.269966612950379,0.234193868628176,0.000369288768077,0.019216887575187},
				{4.279605363769679,0.233666404960095,0.000390735443363,0.019767029199229},
				{4.28924411458898,0.233141311915241,0.000443118212523,0.021050373215765},
				{4.298882865408281,0.232618573547718,0.000521741699472,0.022841665864652},
				{4.308521616227582,0.232098174054322,0.000619548630496,0.024890733827992},
				{4.318160367046882,0.231580097772951,0.000727764396632,0.026977108752267},
				{4.327799117866183,0.231064329181029,0.000836695518425,0.028925689592898},
				{4.337437868685484,0.23055085289396,0.000936609138192,0.030604070614733},
				{4.347076619504785,0.230039653663597,0.001018613524637,0.031915725350316},
				{4.356715370324086,0.22953071637673,0.001075459707912,0.032794202352126},
				{4.366354121143386,0.229024026053603,0.001102191623778,0.033199271434441},
				{4.375992871962687,0.228519567846437,0.001096586575165,0.033114748604882},
				{4.385631622781988,0.228017327037983,0.001059346390459,0.032547601915635},
				{4.395270373601289,0.227517289040093,0.000994023128119,0.031528132328428},
				{4.404909124420589,0.227019439392302,0.000906686897026,0.030111242037249},
				{4.41454787523989,0.226523763760442,0.000805366943085,0.028378987703668},
				{4.424186626059191,0.226030247935255,0.000699317668922,0.026444615121451},
				{4.433825376878492,0.225538877831048,0.000598176736599,0.024457651902809},
				{4.443464127697792,0.225049639484343,0.000511092290939,0.022607350374147},
				{4.453102878517093,0.224562519052559,0.000445898361127,0.021116305574771},
				{4.462741629336394,0.224077502812705,0.000408413155632,0.020209234414802},
				{4.472380380155695,0.223594577160091,0.000401923219565,0.0200480228343},
				{4.482019130974995,0.223113728607059,0.000426899312008,0.020661541859407},
				{4.491657881794296,0.222634943781722,0.000480968626457,0.021930996932573},
				{4.501296632613597,0.222158209426729,0.000559144472314,0.023646235901598},
				{4.510935383432898,0.221683512398039,0.000654291257966,0.025579117615069},
				{4.520574134252199,0.221210839663715,0.000757781263941,0.027527827083542},
				{4.530212885071499,0.220740178302728,0.000860282629101,0.029330574987555},
				{4.5398516358908,0.220271515503784,0.000952606291434,0.030864320686423},
				{4.549490386710101,0.219804838564157,0.001026534863468,0.032039582760516},
				{4.559129137529402,0.219340134888546,0.001075558350447,0.032795706280658},
				{4.568767888348702,0.218877391987937,0.001095450608474,0.033097592185443},
				{4.578406639168003,0.218416597478489,0.001084635549619,0.032933805574495},
				{4.588045389987304,0.217957739080425,0.001044311092702,0.032315802522952},
				{4.597684140806605,0.217500804616944,0.000978321421356,0.031278130080866},
				{4.607322891625905,0.217045782013143,0.000892790955381,0.02987960768453},
				{4.616961642445206,0.216592659294953,0.00079555476164,0.028205580328006},
				{4.626600393264507,0.216141424588088,0.00069543876525,0.026371172997235},
				{4.636239144083808,0.215692066117011,0.000601455754908,0.024524594897946},
				{4.645877894903109,0.215244572203905,0.000521991022578,0.022847122851214},
				{4.655516645722409,0.214798931267665,0.000464051193907,0.021541847504499},
				{4.66515539654171,0.214355131822898,0.000432643863153,0.020800092864059},
				{4.674794147361011,0.213913162478933,0.00043034312571,0.020744713199031},
				{4.684432898180312,0.213473011938853,0.00045707867203,0.021379398308424},
				{4.694071648999612,0.213034668998527,0.000510165715231,0.022586848280166},
				{4.703710399818913,0.212598122545661,0.00058457064894,0.024177895874961},
				{4.713349150638214,0.21216336155886,0.000673386568727,0.025949693037237},
				{4.722987901457515,0.211730375106699,0.000768473358736,0.027721352036573},
				{4.732626652276815,0.211299152346807,0.000861203007884,0.029346260543454},
				{4.742265403096116,0.210869682524964,0.000943241665641,0.03071223967153},
				{4.751904153915417,0.210441954974204,0.001007297263868,0.031737946749401},
				{4.761542904734718,0.210015959113932,0.001047765464746,0.032369205500692},
				{4.771181655554019,0.209591684449055,0.001061216563784,0.032576319064377},
				{4.780820406373319,0.209169120569118,0.001046680878569,0.032352447798724},
				{4.79045915719262,0.20874825714745,0.00100570916982,0.031712918027519},
				{4.800097908011921,0.208329083940326,0.000942204927144,0.030695356768476},
				{4.809736658831222,0.207911590786137,0.000862046373369,0.029360626242787},
				{4.819375409650522,0.207495767604565,0.000772534681444,0.027794508116602},
				{4.829014160469823,0.207081604395773,0.000681720406392,0.026109776069367},
				{4.838652911289124,0.206669091239607,0.000597670408566,0.024447298594437},
				{4.848291662108425,0.206258218294796,0.000527742276618,0.022972641916364},
				{4.857930412927725,0.20584897579818,0.000477931721794,0.02186164956709},
				{4.867569163747026,0.205441354063926,0.000452351031582,0.021268545591598},
				{4.877207914566327,0.205035343482772,0.000452883970567,0.02128107071007},
				{4.886846665385628,0.204630934521267,0.000479046031743,0.021887120224996},
				{4.896485416204928,0.204228117721029,0.000528059942914,0.022979554889382},
				{4.906124167024229,0.203826883698001,0.000595136585616,0.024395421406823},
				{4.91576291784353,0.203427223141731,0.00067393322796,0.025960223958193},
				{4.925401668662831,0.203029126814643,0.000757145119623,0.027516270089227},
				{4.935040419482132,0.202632585551333,0.000837175101084,0.028933978314159},
				{4.944679170301432,0.202237590257861,0.000906819701597,0.030113447188866},
				{4.954317921120733,0.201844131911056,0.000959909498632,0.030982406275695},
				{4.963956671940034,0.20145220155783,0.000991846592379,0.031493596053469},
				{4.973595422759335,0.201061790314501,0.000999992472651,0.031622657583626},
				{4.983234173578635,0.200672889366117,0.000983873362148,0.031366755684135},
				{4.992872924397936,0.200285489965797,0.000945187724781,0.030743905490053},
				{5.002511675217237,0.199899583434071,0.000887618254168,0.029792922887298},
				{5.012150426036538,0.199515161158236,0.000816468769187,0.028573917638071},
				{5.021789176855838,0.199132214591713,0.000738161523466,0.027169128132242},
				{5.031427927675139,0.198750735253415,0.000659642827914,0.025683512764299},
				{5.04106667849444,0.198370714727118,0.00058775206391,0.024243598410922},
				{5.050705429313741,0.197992144660845,0.000528611663167,0.022991556345031},
				{5.060344180133042,0.197615016766253,0.000487092634945,0.022070175235929},
				{5.069982930952342,0.197239322818028,0.000466402336975,0.021596350084572},
				{5.079621681771643,0.196865054653288,0.00046782942322,0.021629364836255},
				{5.089260432590944,0.19649220417099,0.000490665783725,0.022150977037716},
				{5.098899183410245,0.196120763331347,0.000532309122012,0.023071825285658},
				{5.108537934229545,0.19575072415525,0.000588533313665,0.024259705556016},
				{5.118176685048846,0.195382078723696,0.000653898776149,0.025571444545606},
				{5.127815435868147,0.195014819177223,0.000722263059464,0.026874952269055},
				{5.137454186687448,0.194648937715352,0.000787343573129,0.028059643139724},
				{5.147092937506748,0.194284426596035,0.000843280772346,0.02903929703602},
				{5.156731688326049,0.193921278135108,0.000885151077453,0.029751488659441},
				{5.16637043914535,0.193559484705751,0.000909384612566,0.030156004585596},
				{5.176009189964651,0.193199038737957,0.000914052281883,0.030233297568797},
				{5.185647940783952,0.192839932717998,0.000898999523613,0.029983320756923},
				{5.195286691603252,0.192482159187909,0.000865818223947,0.029424789276178},
				{5.204925442422553,0.192125710744968,0.000817663307497,0.028594812597691},
				{5.214564193241854,0.191770580041188,0.000758934118545,0.027548758929301},
				{5.224202944061155,0.191416759782809,0.000694852767591,0.026360060083212},
				{5.233841694880455,0.191064242729802,0.000630979675335,0.025119308814839},
				{5.243480445699756,0.190713021695373,0.000572711573833,0.023931393060858},
				{5.253119196519057,0.190363089545473,0.000524807352125,0.02290867416777},
				{5.262757947338358,0.19001443919832,0.000490983510804,0.022158147729545},
				{5.272396698157658,0.189667063623917,0.000473613602164,0.021762665327665},
				{5.282035448976959,0.189320955843582,0.000473555658882,0.021761334032693},
				{5.29167419979626,0.188976108929477,0.000490119424101,0.022138640972308},
				{5.301312950615561,0.18863251600415,0.00052117216587,0.022829195471367},
				{5.310951701434862,0.188290170240078,0.000563369685216,0.023735409944136},
				{5.320590452254162,0.187949064859208,0.000612487813322,0.024748491132237},
				{5.330229203073463,0.187609193132519,0.000663821879368,0.025764741011072},
				{5.339867953892764,0.187270548379572,0.000712616112789,0.02669487053329},
				{5.349506704712065,0.186933123968077,0.000754483836032,0.027467869157103},
				{5.359145455531365,0.186596913313458,0.000785781038017,0.028031786208099},
				{5.368784206350666,0.186261909878425,0.000803901768778,0.028353161530565},
				{5.378422957169967,0.18592810717255,0.00080747146683,0.028416042420257},
				{5.388061707989268,0.185595498751848,0.000796424773774,0.028220998808941},
				{5.397700458808568,0.185264078218362,0.000771964893213,0.02778425621126},
				{5.407339209627869,0.184933839219755,0.000736412554293,0.027136922343789},
				{5.41697796044717,0.184604775448902,0.000692962073547,0.026324172798905},
				{5.426616711266471,0.184276880643486,0.000645369867143,0.025404130907068},
				{5.436255462085771,0.183950148585608,0.000597605705996,0.024445975251482},
				{5.445894212905072,0.183624573101386,0.000553499111038,0.023526561819315},
				{5.455532963724373,0.18330014806057,0.000516412652184,0.022724714567711},
				{5.465171714543674,0.18297686737616,0.000488969787939,0.022112661258635},
				{5.474810465362975,0.182654725004019,0.000472859134649,0.0217453244319},
				{5.484449216182275,0.182333714942501,0.000468729048941,0.021650151245214},
				{5.494087967001576,0.182013831232075,0.000476177774967,0.021821498000063},
				{5.503726717820877,0.181695067954961,0.000493835551239,0.02222241101318},
				{5.513365468640178,0.18137741923476,0.000519527037401,0.022793135751817},
				{5.523004219459478,0.181060879236096,0.000550495810082,0.02346264712435},
				{5.532642970278779,0.180745442164256,0.000583667991446,0.024159221664733},
				{5.54228172109808,0.18043110226484,0.000615929831197,0.0248179336609},
				{5.551920471917381,0.180117853823408,0.000644394074719,0.025384918253155},
				{5.561559222736681,0.179805691165135,0.000666632426453,0.025819225907313},
				{5.571197973555982,0.179494608654469,0.000680855577954,0.026093209422256},
				{5.580836724375283,0.179184600694789,0.000686028238293,0.026192140773391},
				{5.590475475194584,0.178875661728074,0.000681912967764,0.026113463342959},
				{5.600114226013885,0.178567786234566,0.000669043396715,0.025865873206127},
				{5.609752976833185,0.178260968732445,0.000648633755682,0.025468289217802},
				{5.619391727652486,0.177955203777501,0.000622436804092,0.024948683413996},
				{5.629030478471787,0.177650485962813,0.000592565864775,0.024342675793254},
				{5.638669229291088,0.177346809918432,0.000561299068777,0.023691751070289},
				{5.648307980110388,0.177044170311063,0.000530883786959,0.023040915497411},
				{5.657946730929689,0.176742561843753,0.000503358117676,0.022435643910441},
				{5.66758548174899,0.176441979255583,0.000480403270326,0.021918103711898},
				{5.677224232568291,0.176142417321363,0.00046323687155,0.021522938264789},
				{5.686862983387591,0.175843870851327,0.00045255264868,0.021273284858707},
				{5.696501734206892,0.175546334690834,0.000448507401281,0.021177993325167},
				{5.706140485026193,0.175249803720073,0.000450751903757,0.021230918580155},
				{5.715779235845494,0.174954272853766,0.00045849876431,0.021412584251078},
				{5.725417986664795,0.17465973704088,0.000470617815047,0.021693727550758},
				{5.735056737484095,0.174366191264341,0.000485748186849,0.022039695706805},
				{5.744695488303396,0.174073630540743,0.000502416042914,0.022414639031524},
				{5.754334239122697,0.173782049920072,0.000519147875188,0.0227848167688},
				{5.763972989941998,0.173491444485423,0.000534570907546,0.02312078950958},
				{5.773611740761298,0.173201809352726,0.00054749463604,0.023398603292507},
				{5.783250491580599,0.172913139670472,0.000556970059306,0.023600213119938},
				{5.7928892423999,0.172625430619439,0.000562325639645,0.023713406327321},
				{5.802527993219201,0.17233867741243,0.000563181427848,0.023731443863534},
				{5.812166744038501,0.172052875294002,0.000559444258808,0.023652574041907},
				{5.821805494857802,0.171768019540204,0.000551288062415,0.02347952432258},
				{5.831444245677103,0.171484105458319,0.000539123613014,0.02321903557459},
				{5.841082996496404,0.171201128386606,0.000523561724863,0.022881471212816},
				{5.850721747315704,0.170919083694041,0.000505373111409,0.022480505141322},
				{5.860360498135005,0.170637966780071,0.000485447166158,0.022032865591149},
				{5.869999248954306,0.170357773074356,0.000464750579198,0.021558074570755},
				{5.879637999773607,0.17007849803653,0.000444286062369,0.021078094372344},
				{5.889276750592908,0.169800137155946,0.000425050499565,0.020616752886068},
				{5.898915501412208,0.169522685951443,0.000407991922072,0.020198809917212},
				{5.908554252231509,0.169246139971098,0.000393964842359,0.019848547613337},
				{5.91819300305081,0.168970494791992,0.000383684251494,0.019587859798709},
				{5.927831753870111,0.168695746019972,0.000377679710849,0.019433983401486},
				{5.937470504689411,0.168421889289421,0.000376251983066,0.01939721585863},
				{5.947109255508712,0.16814892026302,0.000379435901369,0.019479114491395},
				{5.956748006328013,0.167876834631526,0.000386973796097,0.019671649552007},
				{5.966386757147314,0.167605628113543,0.000398304239037,0.019957560949091},
				{5.976025507966614,0.167335296455295,0.000412570296878,0.02031182652738},
				{5.985664258785915,0.167065835430408,0.000428650612207,0.020703879158443},
				{5.995303009605216,0.166797240839683,0.000445214739439,0.021100112308677},
				{6.004941760424517,0.166529508510888,0.000460802012735,0.021466299465318},
				{6.014580511243818,0.166262634298531,0.000473920521421,0.021769715694534},
				{6.024219262063118,0.165996614083653,0.000483160219146,0.021980905785389},
				{6.033858012882419,0.165731443773615,0.000487311835848,0.022075140675604},
				{6.04349676370172,0.165467119301887,0.000485481431684,0.022033643177747},
				{6.053135514521021,0.165203636627839,0.00047718961803,0.021844670243104},
				{6.062774265340321,0.164940991736539,0.000462444640226,0.021504526040496},
				{6.072413016159622,0.164679180638545,0.000441779801453,0.021018558500845},
				{6.082051766978923,0.164418199369704,0.000416248255116,0.020402163000919},
				{6.091690517798224,0.164158043990954,0.000387371645411,0.019681759205186},
				{6.101329268617524,0.163898710588125,0.000357043126706,0.018895584846877},
				{6.110968019436825,0.16364019527174,0.000327389967863,0.018093920743242},
				{6.120606770256126,0.163382494176824,0.000300605083028,0.017337966519398},
				{6.130245521075427,0.163125603462709,0.000278760726876,0.016696129098579},
				{6.139884271894728,0.162869519312846,0.000263620325521,0.016236388931058},
				{6.149523022714028,0.162614237934613,0.000256465712997,0.01601454691825},
				{6.159161773533329,0.162359755559129,0.000257956878128,0.016061036022879},
				{6.16880052435263,0.162106068441067,0.000268039328736,0.016371906692131},
				{6.178439275171931,0.161853172858476,0.000285910694683,0.016908893952095},
				{6.188078025991231,0.161601065112591,0.000310053133759,0.017608325694375},
				{6.197716776810532,0.161349741527657,0.000338332447106,0.01839381545809},
				{6.207355527629833,0.16109919845075,0.000368158355185,0.019187453066644},
				{6.216994278449134,0.160849432251602,0.00039669433335,0.019917186883437},
				{6.226633029268434,0.16060043932242,0.000421100148098,0.020520724843395},
				{6.236271780087735,0.160352216077717,0.000438786150299,0.020947222973432},
				{6.245910530907036,0.160104758954141,0.000447656360692,0.021157891215617},
				{6.255549281726337,0.159858064410298,0.00044631722138,0.021126221180788},
				{6.265188032545637,0.15961212892659,0.000434231129181,0.020838213195506},
				{6.274826783364938,0.159366949005043,0.000411798152965,0.020292810376217},
				{6.284465534184239,0.159122521169146,0.00038035524599,0.019502698428435},
				{6.29410428500354,0.15887884196368,0.000342089836186,0.018495670741723},
				{6.303743035822841,0.158635907954561,0.000299872331391,0.017316822208203},
				{6.313381786642141,0.158393715728677,0.000257020091389,0.016031846162855},
				{6.323020537461442,0.158152261893724,0.000217012197174,0.01473133385591},
				{6.332659288280743,0.157911543078057,0.000183179740796,0.013534391038995},
				{6.342298039100044,0.157671555930522,0.00015839956754,0.012585688997425},
				{6.351936789919344,0.157432297120308,0.000144819852399,0.012034112032031},
				{6.361575540738645,0.157193763336793,0.000143644098543,0.011985161598556},
				{6.371214291557946,0.156955951289385,0.000154995333053,0.01244971216747},
				{6.380853042377247,0.156718857707376,0.000177875475641,0.013336996499998},
				{6.390491793196547,0.156482479339794,0.000210226439919,0.014499187560648},
				{6.400130544015848,0.156246812955246,0.000249090163387,0.015782590515715},
				{6.409769294835149,0.156011855341779,0.000290855329809,0.017054481223692},
				{6.41940804565445,0.155777603306732,0.000331570336164,0.018209072907855},
				{6.429046796473751,0.155544053676587,0.000367295038867,0.019164942965408},
				{6.438685547293051,0.155311203296831,0.000394459716429,0.019861009954909},
				{6.448324298112352,0.155079049031814,0.000410198118873,0.020253348337323},
				{6.457963048931653,0.154847587764602,0.000412623164048,0.020313127874558},
				{6.467601799750954,0.154616816396845,0.000401018469897,0.020025445560519},
				{6.477240550570254,0.154386731848636,0.000375926223144,0.019388816960928},
				{6.486879301389555,0.15415733105837,0.000339121046411,0.01841523951545},
				{6.496518052208856,0.153928610982616,0.000293470125938,0.017130969789766},
				{6.506156803028157,0.153700568595975,0.000242690410082,0.015578524002051},
				{6.515795553847457,0.153473200890952,0.000191023419273,0.013821122214676},
				{6.525434304666758,0.153246504877819,0.000142856488446,0.011952258717331},
				{6.535073055486059,0.153020477584489,0.000102324548828,0.010115559738723},
				{6.54471180630536,0.152795116056382,0.000072929109286,0.008539854172432},
				{6.554350557124661,0.152570417356299,0.000057209958877,0.007563726520522},
				{6.563989307943961,0.152346378564293,0.000056500856438,0.007516705158393},
				{6.573628058763262,0.152122996777541,0.000070792965876,0.008413855589203},
				{6.583266809582563,0.151900269110224,0.000098720167496,0.009935802307619},
				{6.592905560401864,0.151678192693397,0.000137669140954,0.011733249377479},
				{6.602544311221164,0.151456764674866,0.000184005494947,0.013564862511184},
				{6.612183062040465,0.151235982219072,0.000233396271942,0.015277312327161},
				{6.621821812859766,0.151015842506963,0.000281199993203,0.016769018850333},
				{6.631460563679067,0.150796342735877,0.000322888680225,0.017969103489747},
				{6.641099314498367,0.150577480119424,0.000354463042003,0.018827188903368},
				{6.650738065317668,0.150359251887367,0.000372822199154,0.019308604277728},
				{6.660376816136969,0.150141655285504,0.000376053300994,0.019392093775407},
				{6.67001556695627,0.149924687575554,0.000363613542127,0.019068653390501},
				{6.679654317775571,0.149708346035041,0.000336386916819,0.018340853764726},
				{6.689293068594871,0.149492627957181,0.000296609691147,0.017222360208362},
				{6.698931819414172,0.149277530650767,0.000247670780892,0.015737559559599},
				{6.708570570233473,0.149063051440062,0.000193804961475,0.013921385041542},
				{6.718209321052774,0.148849187664682,0.000139706944369,0.011819769218114},
				{6.727848071872074,0.148635936679489,0.000090101946115,0.009492204491831},
				{6.737486822691375,0.148423295854483,0.000049312538665,0.007022288705631},
				{6.747125573510676,0.14821126257469,0.000020862057882,0.004567500178681},
				{6.756764324329977,0.147999834240062,0.000007151565569,0.002674241120259},
				{6.766403075149277,0.147789008265361,0.000009240522794,0.003039822822747},
				{6.776041825968578,0.147578782080062,0.000026751642127,0.005172198964409},
				{6.785680576787879,0.147369153128243,0.000057908787046,0.007609782325816},
				{6.79531932760718,0.147160118868487,0.000099704253745,0.009985201737835},
				{6.80495807842648,0.146951676773772,0.000148179683085,0.01217290774981},
				{6.814596829245781,0.146743824331377,0.000198794077285,0.014099435353415},
				{6.824235580065082,0.146536559042773,0.000246844407411,0.015711282806033},
				{6.833874330884383,0.146329878423531,0.000287899251505,0.016967594157842},
				{6.843513081703684,0.146123780003216,0.000318204904967,0.017838298824916},
				{6.853151832522984,0.145918261325293,0.000335026107381,0.018303718403126},
				{6.862790583342285,0.145713319947027,0.00033688982695,0.018354558751154},
				{6.872429334161586,0.145508953439388,0.000323709865258,0.017991938896561},
				{6.882068084980887,0.145305159386952,0.00029678145996,0.017227346283164},
				{6.891706835800187,0.145101935387809,0.000258647308208,0.016082515605706},
				{6.901345586619488,0.144899279053468,0.000212848750884,0.014589336889809},
				{6.910984337438789,0.144697188008763,0.000163586497249,0.012790093715417},
				{6.92062308825809,0.14449565989176,0.000115323720785,0.010738888247177},
				{6.93026183907739,0.144294692353663,0.00007236956945,0.008507030589488},
				{6.939900589896691,0.144094283058727,0.000038482518226,0.006203427941528},
				{6.949539340715992,0.143894429684166,0.000016530874757,0.004065817846022},
				{6.959178091535293,0.14369512992006,0.000008241731028,0.002870841519086},
				{6.968816842354594,0.143496381469272,0.000014060950879,0.003749793444857},
				{6.978455593173894,0.143298182047353,0.000033135857025,0.0057563753374},
				{6.988094343993195,0.14310052938246,0.000063420368151,0.007963690611222},
				{6.997733094812496,0.142903421215266,0.000101890599299,0.010094087343521},
				{7.007371845631797,0.142706855298877,0.000144848497574,0.012035302138859},
				{7.017010596451097,0.142510829398741,0.000188282940506,0.013721623100259},
				{7.026649347270398,0.14231534129257,0.000228252651841,0.015108032692606},
				{7.036288098089699,0.142120388770251,0.000261253803609,0.016163347537222},
				{7.045926848909,0.141925969633767,0.000284537268677,0.016868232529723},
				{7.0555655997283,0.141732081697109,0.000296346086864,0.017214705541005},
				{7.065204350547601,0.141538722786198,0.000296051916408,0.017206159257905},
				{7.074843101366902,0.141345890738806,0.000284179706917,0.016857630524976},
				{7.084481852186203,0.141153583404467,0.000262320861153,0.01619632245767},
				{7.094120603005504,0.140961798644407,0.000232946216594,0.015262575686761},
				{7.103759353824804,0.140770534331457,0.000199139769865,0.014111689121602},
				{7.113398104644105,0.140579788349978,0.00016428147473,0.012817233505334},
				{7.123036855463406,0.140389558595783,0.000131711782546,0.01147657538405},
				{7.132675606282707,0.140199842976059,0.000104411771546,0.010218207844153},
				{7.142314357102007,0.140010639409289,0.000084730123062,0.009204896689345},
				{7.151953107921308,0.13982194582518,0.000074182840524,0.008612946100157},
				{7.161591858740609,0.139633760164581,0.000073343461785,0.008564079739538},
				{7.17123060955991,0.139446080379413,0.000081831900081,0.009046098611082},
				{7.18086936037921,0.139258904432595,0.000098399638296,0.009919659182435},
				{7.190508111198511,0.139072230297967,0.000121099233407,0.011004509685004},
				{7.200146862017812,0.138886055960219,0.000147517616011,0.01214568301954},
				{7.209785612837113,0.138700379414817,0.000175046590356,0.013230517388087},
				{7.219424363656413,0.138515198667935,0.000201160848673,0.014183118439658},
				{7.229063114475714,0.138330511736378,0.000223673778544,0.014955727282338},
				{7.238701865295015,0.138146316647515,0.000240944667824,0.015522392464566},
				{7.248340616114316,0.137962611439207,0.000252016766411,0.015875035949925},
				{7.257979366933617,0.137779394159739,0.000256673886977,0.016021045127475},
				{7.267618117752917,0.13759666286775,0.000255412345749,0.015981625253688},
				{7.277256868572218,0.137414415632164,0.000249334622512,0.015790333198272},
				{7.286895619391519,0.137232650532121,0.000239979568825,0.015491273957459},
				{7.29653437021082,0.137051365656913,0.000229110899457,0.015136409728108},
				{7.30617312103012,0.136870559105915,0.000218489816148,0.014781401021147},
				{7.315811871849421,0.136690228988516,0.000209659074137,0.014479608908276},
				{7.325450622668722,0.136510373424057,0.000203763789588,0.014274585443642},
				{7.335089373488023,0.136330990541765,0.000201429678438,0.014192592379057},
				{7.344728124307323,0.136152078480687,0.000202712328996,0.01423770799657},
				{7.354366875126624,0.135973635389625,0.000207122467765,0.014391749989675},
				{7.364005625945925,0.135795659427073,0.00021372300915,0.014619268420489},
				{7.373644376765226,0.135618148761155,0.000221284966375,0.014875650116052},
				{7.383283127584527,0.135441101569561,0.00022848209778,0.015115624293426},
				{7.392921878403827,0.135264516039483,0.000234099220971,0.01530030133595},
				{7.402560629223128,0.135088390367557,0.000237227190745,0.015402181363212},
				{7.412199380042429,0.134912722759797,0.000237418765769,0.015408399195548},
				{7.42183813086173,0.134737511431537,0.000234783912291,0.015322660091868},
				{7.43147688168103,0.134562754607372,0.000230010094873,0.015166083702563},
				{7.441115632500331,0.134388450521093,0.000224301998987,0.014976715226866},
				{7.450754383319632,0.134214597415632,0.000219245053342,0.014806925857231},
				{7.460393134138933,0.134041193543002,0.000216606631448,0.014717562007624},
				{7.470031884958233,0.133868237164237,0.000218097278873,0.014768116971148},
				{7.479670635777534,0.133695726549334,0.000225120153026,0.015004004566322},
				{7.489309386596835,0.133523659977199,0.000238539849973,0.015444735348107},
				{7.498948137416136,0.133352035735583,0.000258501077457,0.016077968698085},
				{7.508586888235437,0.133180852121032,0.000284323248589,0.016861887456303},
				{7.518225639054737,0.133010107438825,0.000314489612594,0.017733854984022},
				{7.527864389874038,0.132839800002924,0.000346739292593,0.01862093694188},
				{7.537503140693339,0.132669928135912,0.000378258966833,0.019448880863241},
				{7.54714189151264,0.132500490168945,0.000405959107611,0.020148426926469},
				{7.55678064233194,0.132331484441688,0.000426808674231,0.020659348349614},
				{7.566419393151241,0.132162909302272,0.000438193955675,0.020933082803906},
				{7.576058143970542,0.131994763107231,0.000438261890559,0.02093470540893},
				{7.585696894789843,0.131827044221453,0.00042620751081,0.02064479379431},
				{7.595335645609143,0.131659751018126,0.00040246866566,0.020061621710613},
				{7.604974396428444,0.131492881878686,0.000368799030308,0.019204140967724},
				{7.614613147247745,0.131326435192764,0.000328202009152,0.018116346462584},
				{7.624251898067046,0.131160409358134,0.000284722315634,0.016873716710729},
				{7.633890648886346,0.130994802780661,0.000243107310939,0.01559189888816},
				{7.643529399705647,0.130829613874254,0.000208365322569,0.014434864826821},
				{7.653168150524948,0.130664841060811,0.000185260835054,0.013611055618662},
				{7.662806901344249,0.130500482770168,0.000177796142353,0.013334021987118},
				{7.67244565216355,0.130336537440055,0.000188733499947,0.013738031152495},
				{7.68208440298285,0.13017300351604,0.000219211130746,0.01480578031534},
				{7.691723153802151,0.130009879451483,0.000268499815152,0.016385963967733},
				{7.701361904621452,0.129847163707489,0.000333935118864,0.018273891727374},
				{7.711000655440753,0.129684854752855,0.000411043984216,0.020274219694385},
				{7.720639406260053,0.129522951064024,0.000493865407958,0.0222230827735},
				{7.730278157079354,0.129361451125042,0.000575444849568,0.023988431577904},
				{7.739916907898655,0.129200353427501,0.000648462683347,0.025464930460274},
				{7.749555658717956,0.129039656470502,0.000705941116929,0.026569552441271},
				{7.759194409537256,0.128879358760601,0.00074196240851,0.027238986921512},
				{7.768833160356557,0.128719458811766,0.000752326434401,0.027428569674715},
				{7.778471911175858,0.128559955145333,0.000735077500251,0.027112312705695},
				{7.788110661995159,0.128400846289955,0.000690839631515,0.026283828326848},
				{7.79774941281446,0.128242130781562,0.0006229155803,0.02495827678948},
				{7.80738816363376,0.128083807163313,0.000537125972604,0.023175978352681},
				{7.817026914453061,0.127925873985553,0.000441389951138,0.021009282499368},
				{7.826665665272362,0.127768329805768,0.000345074593059,0.018576183490138},
				{7.836304416091663,0.127611173188541,0.000258164861471,0.016067509498081},
				{7.845943166910963,0.127454402705508,0.00019032650504,0.013795887250913},
				{7.855581917730264,0.127298016935317,0.000149948739964,0.012245355852894},
				{7.865220668549565,0.127142014463583,0.000143259939727,0.011969124434431},
				{7.874859419368866,0.126986393882844,0.000173607074091,0.013176003722333},
				{7.884498170188166,0.126831153792523,0.000240978040989,0.015523467428041},
				{7.894136921007467,0.126676292798881,0.000341825987532,0.018488536651987},
				{7.903775671826768,0.12652180951498,0.000469227922997,0.021661669441585},
				{7.913414422646069,0.126367702560638,0.000613378630664,0.024766482000162},
				{7.92305317346537,0.126213970562389,0.000762388179776,0.027611377723251},
				{7.93269192428467,0.126060612153443,0.000903320012533,0.030055282606114},
				{7.942330675103971,0.125907625973645,0.001023380348048,0.031990316473087},
				{7.951969425923272,0.125755010669435,0.001111150518581,0.033333924440142},
				{7.961608176742573,0.125602764893806,0.001157745061898,0.034025652997381},
				{7.971246927561873,0.125450887306268,0.001157780024343,0.03402616675946},
				{7.980885678381174,0.125299376572806,0.001110049581143,0.033317406578886},
				{7.990524429200475,0.125148231365843,0.001017832318918,0.031903484432241},
				{8.000163180019776,0.124997450364197,0.000888781675368,0.029812441620367},
				{8.009801930839076,0.124847032253049,0.000734392601245,0.02709967898785},
				{8.019440681658377,0.124696975723899,0.000569077573592,0.023855346855411},
				{8.029079432477678,0.12454727947453,0.000408923901078,0.020221866903865},
				{8.038718183296979,0.124397942208973,0.000270238057079,0.016438918975381},
				{8.04835693411628,0.124248962637466,0.000168007676782,0.012961777531737},
				{8.05799568493558,0.124100339476416,0.000114424967165,0.010696960650831},
				{8.067634435754881,0.123952071448368,0.000117615330748,0.010845060200296},
				{8.077273186574182,0.123804157281961,0.000180700959496,0.013442505700053},
				{8.086911937393483,0.1236565957119,0.000301302190893,0.017358058384895},
				{8.096550688212783,0.123509385478909,0.000471541555795,0.021715007616739},
				{8.106189439032084,0.123362525329707,0.000678569808397,0.026049372514459},
				{8.115828189851385,0.123216014016964,0.000905584088885,0.030092924232864},
				{8.125466940670686,0.123069850299269,0.00113326022167,0.033663930573682},
				{8.135105691489986,0.122924032941094,0.001341478705721,0.036626202447437},
				{8.144744442309287,0.122778560712762,0.001511191350282,0.038874044686424},
				{8.154383193128588,0.122633432390406,0.001626256592815,0.040326871845144},
				{8.164021943947889,0.122488646755943,0.00167506815355,0.040927596479031},
				{8.17366069476719,0.122344202597033,0.001651815066342,0.040642527804534},
				{8.18329944558649,0.122200098707048,0.001557240103919,0.0394618816571},
				{8.192938196405791,0.12205633388504,0.001398807099967,0.037400629673409},
				{8.202576947225092,0.121912906935704,0.001190240097074,0.034499856479028},
				{8.212215698044393,0.121769816669347,0.000950455864494,0.030829464226519},
				{8.221854448863693,0.121627061901857,0.000701969681698,0.026494710447528},
				{8.231493199682994,0.121484641454665,0.000468906798556,0.021654255899375},
				{8.241131950502295,0.121342554154718,0.000274793887914,0.016576908273683},
				{8.250770701321596,0.121200798834444,0.000140331640288,0.01184616563651},
				{8.260409452140896,0.12105937433172,0.000081357979882,0.009019865846141},
				{8.270048202960197,0.120918279489841,0.000107200396555,0.010353762434752},
				{8.279686953779498,0.12077751315749,0.00021958559508,0.014818420802504},
				{8.289325704598799,0.120637074188702,0.000412227428662,0.02030338466025},
				{8.2989644554181,0.120496961442838,0.000671153952492,0.025906639158556},
				{8.3086032062374,0.120357173784552,0.000975766630162,0.031237263487098},
				{8.318241957056701,0.120217710083759,0.001300555238136,0.036063211700241},
				{8.327880707876002,0.120078569215606,0.001617328100744,0.040216017962296},
				{8.337519458695303,0.119939750060444,0.001897763870376,0.043563331718046},
				{8.347158209514603,0.119801251503792,0.002116055148259,0.046000599433694},
				{8.356796960333904,0.119663072436313,0.002251398336958,0.047448902378859},
				{8.366435711153205,0.119525211753783,0.002290091090823,0.04785489620533},
				{8.376074461972506,0.119387668357058,0.002227028592699,0.047191403800897},
				{8.385713212791806,0.11925044115205,0.002066440114069,0.045458113841966},
				{8.395351963611107,0.119113529049694,0.001821773911893,0.042682243519906},
				{8.404990714430408,0.118976930965922,0.001514716259957,0.03891935585229},
				{8.414629465249709,0.118840645821631,0.00117341090688,0.03425508585422},
				{8.42426821606901,0.118704672542659,0.000830022690812,0.028810114383868},
				{8.43390696688831,0.118569010059753,0.000517854501537,0.022756416711277},
				{8.443545717707611,0.118433657308543,0.000268275009632,0.016379102833559},
				{8.453184468526912,0.118298613229514,0.000107739307736,0.010379754704992},
				{8.462823219346213,0.118163876767977,0.000055184134277,0.0074286024444},
				{8.472461970165513,0.118029446874043,0.000120051955733,0.01095682233738},
				{8.482100720984814,0.117895322502595,0.00030114631095,0.017353567672096},
				{8.491739471804115,0.117761502613262,0.000586448480196,0.024216698375217},
				{8.501378222623416,0.11762798617039,0.000953938981902,0.030885902640228},
				{8.511016973442716,0.117494772143017,0.001373374568691,0.037059068643063},
				{8.520655724262017,0.117361859504846,0.001808880300998,0.042530933460227},
				{8.530294475081318,0.117229247234219,0.002222136492834,0.047139542772854},
				{8.539933225900619,0.11709693431409,0.002575878642703,0.050753114610861},
				{8.54957197671992,0.116964919732,0.002837391693764,0.053267172759253},
				{8.55921072753922,0.116833202480049,0.002981672194553,0.054604690224863},
				{8.568849478358521,0.116701781554875,0.002993954517472,0.05471704046704},
				{8.578488229177822,0.116570655957622,0.002871348951134,0.053584969451646},
				{8.588126979997122,0.116439824693921,0.00262341575999,0.051219290896988},
				{8.597765730816423,0.116309286773861,0.002271594805627,0.047661250567175},
				{8.607404481635724,0.116179041211964,0.001847514615864,0.042982724621225},
				{8.617043232455025,0.116049087027163,0.001390310559026,0.037286868452922},
				{8.626681983274326,0.115919423242775,0.000943177307237,0.030711191888903},
				{8.636320734093626,0.115790048886477,0.000549457951632,0.023440519440322},
				{8.645959484912927,0.115660962990283,0.000248622806256,0.015767777467237},
				{8.655598235732228,0.115532164590517,0.000072509900346,0.008515274531451},
				{8.665236986551529,0.115403652727791,0.000042183728049,0.006494900156948},
				{8.67487573737083,0.115275426446982,0.000165719804775,0.012873220450788},
				{8.68451448819013,0.115147484797207,0.000437143511168,0.020907977213674},
				{8.694153239009431,0.1150198268318,0.000836649613053,0.028924896076782},
				{8.703791989828732,0.114892451608288,0.001332112250287,0.036498112968858},
				{8.713430740648032,0.114765358188367,0.001881775365771,0.043379434825394},
				{8.723069491467333,0.114638545637883,0.002437900560371,0.049375100611245},
				{8.732708242286634,0.114512013026803,0.002951055414163,0.054323617462046},
				{8.742346993105935,0.114385759429199,0.00337465811913,0.058091807676552},
				{8.751985743925236,0.11425978392322,0.00366936134388,0.060575253560176},
				{8.761624494744536,0.114134085591071,0.003806864229666,0.061699791163875},
				{8.771263245563837,0.114008663518993,0.003772785297026,0.061423002995836},
				{8.780901996383138,0.113883516797238,0.003568307483363,0.059735311862941},
				{8.790540747202439,0.113758644520048,0.003210415727005,0.056660530592332},
				{8.80017949802174,0.113634045785634,0.002730673279605,0.052255844454038},
				{8.80981824884104,0.113509719696153,0.002172618476899,0.04661135566468},
				{8.819456999660341,0.113385665357687,0.001587993524458,0.03984963644073},
				{8.829095750479642,0.113261881880223,0.001032130326403,0.032126785186238},
				{8.838734501298942,0.113138368377627,0.000558903545288,0.023641140947261},
				{8.848373252118243,0.113015123967629,0.000215710517796,0.01468708677022},
				{8.858012002937544,0.1128921477718,0.000038944445942,0.006240548528956},
				{8.867650753756845,0.112769438915526,0.000050391945433,0.007098728437763},
				{8.877289504576146,0.112646996527996,0.000254908623192,0.015965858047479},
				{8.886928255395446,0.112524819742173,0.000639615327601,0.025290617382758},
				{8.896567006214747,0.112402907694782,0.001174721313885,0.034274207706166},
				{8.906205757034048,0.112281259526281,0.001815931550476,0.042613748373925},
				{8.915844507853349,0.112159874380847,0.00250824756615,0.050082407751129},
				{8.92548325867265,0.112038751406354,0.003190837509427,0.056487498700398},
				{8.93512200949195,0.111917889754352,0.003802545450935,0.061664782906741},
				{8.944760760311251,0.111797288580047,0.004287542005608,0.065479325024068},
				{8.954399511130552,0.111676947042286,0.004600595045708,0.067827686424558},
				{8.964038261949852,0.111556864303531,0.004711463710036,0.068640102782821},
				{8.973677012769153,0.111437039529843,0.004607991948261,0.067882191687227},
				{8.983315763588454,0.111317471890863,0.00429758800163,0.065555991348082},
				{8.992954514407755,0.11119816055979,0.00380692203711,0.061700259619474},
				{9.002593265227055,0.111079104713366,0.003179834623164,0.056390022372437},
				{9.012232016046356,0.110960303531854,0.002473615527015,0.049735455431869},
				{9.021870766865657,0.110841756199021,0.001753966277944,0.041880380584996},
				{9.031509517684958,0.110723461902117,0.001089088028578,0.033001333739384},
				{9.041148268504259,0.110605419831859,0.000543425728042,0.023311493475146},
				{9.05078701932356,0.110487629182411,0.000171641835418,0.013101215035951},
				{9.06042577014286,0.110370089151366,0.000013381783454,0.003658112006772},
				{9.07006452096216,0.110252798939728,0.00008933106435,0.009451511220451},
				{9.079703271781462,0.110135757751894,0.000398952848784,0.019973804063919},
				{9.089342022600762,0.110018964795635,0.000920146759152,0.030333920932707},
				{9.098980773420063,0.10990241928208,0.001610895560724,0.040135963433362},
				{9.108619524239364,0.109786120425697,0.002412783706204,0.049120094729182},
				{9.118258275058665,0.109670067444275,0.003256095809363,0.057062209993682},
				{9.127897025877965,0.109554259558906,0.004066051750901,0.063765600059128},
				{9.137535776697266,0.10943869599397,0.004769621593269,0.069062447055322},
				{9.147174527516567,0.109323375977117,0.005302300015258,0.072816893749032},
				{9.156813278335868,0.109208298739246,0.005614212779978,0.074928050688496},
				{9.166452029155169,0.109093463514494,0.005674978140957,0.075332450782894},
				{9.17609077997447,0.108978869540214,0.005476852046726,0.074005756848543},
				{9.18572953079377,0.108864516056961,0.005035835734455,0.070963622613668},
				{9.19536828161307,0.108750402308474,0.004390608795966,0.066261669130545},
				{9.205007032432372,0.108636527541659,0.003599347981663,0.059994566267815},
				{9.214645783251672,0.108522891006573,0.002734690726344,0.052294270492513},
				{9.224284534070973,0.10840949195641,0.001877276982519,0.043327554541186},
				{9.233923284890274,0.108296329647478,0.00110844267722,0.033293282764241},
				{9.243562035709575,0.108183403339191,0.000502726454536,0.022421562267959},
				{9.253200786528875,0.108070712294046,0.000120878691453,0.010994484592402},
				{9.262839537348176,0.107958255777611,0.000004028194475,0.002007036241475},
				{9.272478288167477,0.107846033058507,0.000169564442777,0.013021691241053},
				{9.282117038986778,0.107734043408395,0.000609144382146,0.024680850515046},
				{9.291755789806079,0.107622286101954,0.00128904277726,0.03590324187674},
				{9.30139454062538,0.107510760416875,0.002152851605019,0.046398831935929},
				{9.31103329144468,0.107399465633834,0.003126317220902,0.055913479778156},
				{9.32067204226398,0.107288401036488,0.00412390256703,0.064217618820925},
				{9.330310793083282,0.10717756591145,0.005056495496853,0.071109039487625},
				{9.339949543902582,0.107066959548281,0.005839569332464,0.07641707487508},
				{9.349588294721883,0.106956581239468,0.006401049083106,0.080006556500737},
				{9.359227045541184,0.106846430280416,0.006688151507168,0.081781119503025},
				{9.368865796360485,0.106736505969428,0.006672551692221,0.081685688417378},
				{9.378504547179785,0.106626807607692,0.006353372867793,0.079708047697787},
				{9.388143297999086,0.106517334499265,0.00575768899415,0.075879437228735},
				{9.397782048818387,0.10640808595106,0.00493845689097,0.070274155213491},
				{9.407420799637688,0.106299061272832,0.003970028378386,0.063008161204607},
				{9.417059550456989,0.106190259777159,0.00294162077749,0.054236710607204},
				{9.42669830127629,0.106081680779431,0.001949317682315,0.044151077929248},
				{9.43633705209559,0.105973323597839,0.001087316471932,0.032974482132885},
				{9.44597580291489,0.105865187553351,0.000439219579638,0.020957566166852},
				{9.455614553734192,0.105757271969708,0.000070173798598,0.008376980279214},
				{9.465253304553492,0.105649576173405,0.000020596018383,0.004538283638437},
				{9.474892055372793,0.105542099493676,0.000302087746295,0.017380671629578},
				{9.484530806192094,0.105434841262484,0.000895947328718,0.029932379269241},
				{9.494169557011395,0.105327800814501,0.001754454932884,0.041886214114955},
				{9.503808307830695,0.105220977487103,0.002804851736755,0.052960850982163},
				{9.513447058649996,0.105114370620348,0.003955684595203,0.06289423340182},
				{9.523085809469297,0.105007979556968,0.005104964617945,0.071449035108562},
				{9.532724560288598,0.10490180364235,0.006149412484445,0.078418189754964},
				{9.542363311107898,0.104795842224529,0.006993952826101,0.083629856068876},
				{9.5520020619272,0.104690094654172,0.007560585336901,0.08695162641895},
				{9.5616408127465,0.10458456028456,0.007795805192188,0.08829385704673},
				{9.5712795635658,0.104479238471585,0.007675868650825,0.087612034851527},
				{9.580918314385102,0.104374128573726,0.007209387951276,0.084908114755166},
				{9.590557065204402,0.104269229952044,0.006436979928605,0.080230791648877},
				{9.600195816023703,0.104164541970165,0.00542796030016,0.073674692399494},
				{9.609834566843004,0.104060063994267,0.004274348787713,0.06537850401862},
				{9.619473317662305,0.10395579539307,0.003082701363737,0.055522079965875},
				{9.629112068481605,0.103851735537822,0.00196449397489,0.044322612455608},
				{9.638750819300906,0.103747883802284,0.001025925174873,0.032030066732252},
				{9.648389570120207,0.103644239562721,0.000358070062662,0.018922739301227},
				{9.658028320939508,0.103540802197888,0.000028297982668,0.005319584820971},
				{9.667667071758808,0.103437571089017,0.000073762177534,0.008588490992853},
				{9.67730582257811,0.103334545619805,0.000497587999286,0.022306680597668},
				{9.68694457339741,0.103231725176402,0.001268146439986,0.035611043792421},
				{9.69658332421671,0.1031291091474,0.002321519508202,0.048182149269226},
				{9.706222075036012,0.103026696923817,0.003566971052801,0.059724124546122},
				{9.715860825855312,0.102924487899091,0.004894957026267,0.069963969486209},
				{9.725499576674613,0.102822481469062,0.00618696911206,0.078657288995108},
				{9.735138327493914,0.102720677031964,0.007326327343139,0.085593967913277},
				{9.744777078313215,0.102619073988411,0.008208939404627,0.090603197540856},
				{9.754415829132515,0.102517671741387,0.008753033944468,0.093557650379151},
				{9.764054579951816,0.102416469696233,0.008906959055977,0.094376687036456},
				{9.773693330771117,0.102315467260635,0.008654303311872,0.093028508060013},
				{9.783332081590418,0.102214663844615,0.008015834060156,0.089531190431917},
				{9.792970832409718,0.102114058860516,0.007048037209483,0.083952589057653},
				{9.80260958322902,0.102013651722993,0.005838350267674,0.076409098068713},
				{9.81224833404832,0.101913441849002,0.004497489257387,0.067063322743413},
				{9.82188708486762,0.101813428657786,0.003149541305901,0.056120774281018},
				{9.831525835686922,0.101713611570867,0.001920708710478,0.043825890869184},
				{9.841164586506222,0.101613990012031,0.000927726026876,0.030458595287302},
				{9.850803337325523,0.101514563407323,0.000267012566663,0.016340519167481},
				{9.860442088144824,0.101415331185028,0.000005568538181,0.00235977502757},
				{9.870080838964125,0.101316292775668,0.000174473237968,0.013208831816931},
				{9.879719589783425,0.101217447611984,0.000765614263288,0.02766973551171},
				{9.889358340602726,0.101118795128932,0.001731985949793,0.041617135290557},
				{9.898997091422027,0.101020334763665,0.002991570334904,0.054695249655742},
				{9.908635842241328,0.100922065955529,0.004434483664036,0.066591918909401},
				{9.918274593060628,0.100823988146049,0.005932767723283,0.077024461850007},
				{9.92791334387993,0.100726100778917,0.007351952940535,0.085743530021423},
				{9.93755209469923,0.100628403299985,0.008563350169404,0.092538371335374},
				{9.94719084551853,0.100530895157252,0.00945594546655,0.097241685847944},
				{9.956829596337831,0.100433575800856,0.009946799839083,0.099733644469068},
				{9.966468347157132,0.10033644468306,0.009988979691949,0.099944883270474},
				{9.976107097976433,0.100239501258245,0.009576261013642,0.097858372220477},
				{9.985745848795734,0.1001427449829,0.008744139444691,0.093510103436427},
				{9.995384599615035,0.100046175315607,0.007567013526333,0.086988582735511},
				{10.005023350434335,0.099949791717037,0.006151759660275,0.078433154088532},
				{10.014662101253636,0.099853593649937,0.00462825148369,0.068031253727168},
				{10.024300852072937,0.099757580579119,0.003137664423182,0.056014858949941},
				{10.033939602892238,0.099661751971454,0.001819618155433,0.042656982493291},
				{10.043578353711538,0.099566107295858,0.00079932850974,0.028272398372621},
				{10.05321710453084,0.099470646023283,0.000175951030311,0.013264653418442},
				{10.06285585535014,0.099375367626709,0.000013201234326,0.003633350289514},
				{10.07249460616944,0.099280271581133,0.000333138130438,0.01825207194917},
				{10.082133356988741,0.09918535736356,0.001113715046965,0.03337236951379},
				{10.091772107808042,0.099090624452993,0.002290361361113,0.047857719974036},
				{10.101410858627343,0.098996072330424,0.003761490080766,0.061330987932418},
				{10.111049609446644,0.098901700478822,0.005397463950501,0.073467434625831},
				{10.120688360265945,0.098807508383128,0.007052229105953,0.08397755120241},
				{10.130327111085245,0.098713495530242,0.008576573106821,0.092609789476171},
				{10.139965861904546,0.098619661409015,0.009831802726422,0.099155447285672},
				{10.149604612723847,0.098526005510241,0.010702587859738,0.103453312463824},
				{10.159243363543148,0.098432527326645,0.01110778206935,0.105393463124379},
				{10.168882114362448,0.098339226352876,0.011008204480613,0.104919990853093},
				{10.17852086518175,0.098246102085496,0.010410638426759,0.10203253611843},
				{10.18815961600105,0.098153154022975,0.009367643206514,0.096786585881072},
				{10.19779836682035,0.098060381665675,0.007973156444782,0.089292532973269},
				{10.207437117639651,0.097967784515849,0.006354251789922,0.079713560890993},
				{10.217075868458952,0.097875362077626,0.004659774831327,0.068262543399192},
				{10.226714619278253,0.097783113857007,0.003046872199621,0.0551984800481},
				{10.236353370097554,0.097691039361849,0.001666634724867,0.040824437838957},
				{10.245992120916855,0.097599138101866,0.000650165317927,0.025498339513125},
				{10.255630871736155,0.097507409588613,0.000096357629179,0.009816192193482},
				{10.265269622555456,0.097415853335478,0.000062524189477,0.007907223879283},
				{10.274908373374757,0.097324468857677,0.000558761620125,0.023638139100288},
				{10.284547124194058,0.097233255672244,0.001546604268471,0.039326889890648},
				{10.294185875013358,0.097142213298019,0.00294212811879,0.054241387507973},
				{10.30382462583266,0.097051341255645,0.004623258802884,0.067994549802791},
				{10.31346337665196,0.096960639067555,0.006440648823607,0.08025365302344},
				{10.32310212747126,0.096870106257968,0.008231154494469,0.090725710217498},
				{10.332740878290561,0.096779742352877,0.00983269711654,0.099159957223365},
				{10.342379629109862,0.096689546880041,0.011099152915967,0.105352517368914},
				{10.352018379929163,0.09659951936898,0.011913904228981,0.109150832470397},
				{10.361657130748464,0.096509659350962,0.012200794368609,0.110457206051071},
				{10.371295881567764,0.096419966359,0.011931457670356,0.109231211978792},
				{10.380934632387065,0.09633043992784,0.011128320403631,0.105490854597122},
				{10.390573383206366,0.096241079593956,0.00986296105672,0.099312441600837},
				{10.400212134025667,0.096151884895537,0.008249942301615,0.090829193003213},
				{10.409850884844968,0.096062855372485,0.006436644297255,0.080228699959898},
				{10.419489635664268,0.095973990566405,0.004590001394892,0.067749549038295},
				{10.429128386483569,0.095885290020595,0.002881335958624,0.053678077076437},
				{10.43876713730287,0.095796753280041,0.001470667908201,0.038349288235914},
				{10.44840588812217,0.095708379891406,0.000491937494627,0.022179663988137},
				{10.458044638941471,0.095620169403027,0.000040506614518,0.0063644806951},
				{10.467683389760772,0.095532121364902,0.000164105013924,0.012810347923629},
				{10.477322140580073,0.095444235328688,0.000858080199741,0.02929300598677},
				{10.486960891399374,0.095356510847688,0.002065420883611,0.045446901804318},
				{10.496599642218674,0.095268947476845,0.003681588508549,0.060676095033789},
				{10.506238393037975,0.095181544772738,0.005563749938359,0.074590548585992},
				{10.515877143857276,0.095094302293569,0.007543595889725,0.086853876653405},
				{10.525515894676577,0.095007219599161,0.009442595384826,0.097173017781821},
				{10.535154645495878,0.094920296250946,0.011088301967541,0.105301006488735},
				{10.544793396315178,0.09483353181196,0.012330222392279,0.111041534536764},
				{10.554432147134479,0.094746925846835,0.013053786298905,0.114253167566176},
				{10.56407089795378,0.094660477921792,0.013191119805595,0.1148526003432},
				{10.57370964877308,0.094574187604635,0.012727610023044,0.112816709857381},
				{10.583348399592381,0.09448805446474,0.011703627551559,0.108183305327389},
				{10.592987150411682,0.094402078073052,0.010211213483719,0.101050549151003},
				{10.602625901230983,0.094316258002076,0.008385999611002,0.091575103663614},
				{10.612264652050284,0.094230593825871,0.006395070597909,0.079969185302268},
				{10.621903402869584,0.09414508512004,0.004421854053764,0.066497022893993},
				{10.631542153688885,0.094059731461726,0.00264940581876,0.051472379183011},
				{10.641180904508186,0.093974532429605,0.001243614458577,0.035264918241457},
				{10.650819655327487,0.093889487603877,0.000337866795927,0.018381153280645},
				{10.660458406146788,0.093804596566261,0.000020592724226,0.004537920694057},
				{10.670097156966088,0.093719858899986,0.000326852493653,0.018079062300158},
				{10.679735907785389,0.093635274189787,0.00123476609149,0.035139238629916},
				{10.68937465860469,0.093550842021897,0.002667144724704,0.051644406519041},
				{10.69901340942399,0.09346656198404,0.004498208921572,0.067068688085958},
				{10.708652160243291,0.093382433665422,0.006564809601149,0.081023512643857},
				{10.718290911062592,0.09329845665673,0.008681151076194,0.093172694906792},
				{10.727929661881893,0.093214630550121,0.01065568662535,0.103226385315721},
				{10.737568412701194,0.093130954939214,0.012308649331284,0.110944352408243},
				{10.747207163520494,0.093047429419089,0.013488612614175,0.116140486541837},
				{10.756845914339795,0.092964053586276,0.014086553788852,0.118686788602824},
				{10.766484665159096,0.09288082703875,0.014046114399677,0.118516304362213},
				{10.776123415978397,0.092797749375925,0.013369091189211,0.115624786223419},
				{10.785762166797698,0.092714820198645,0.012115625135635,0.110071000429879},
				{10.795400917616998,0.092632039109182,0.010399038046893,0.101975673799653},
				{10.805039668436299,0.092549405711226,0.008375759560035,0.091519175914311},
				{10.8146784192556,0.092466919609879,0.006231238781912,0.078938195963123},
				{10.8243171700749,0.092384580411651,0.004163110184677,0.064522168164721},
				{10.833955920894201,0.092302387724452,0.002363143703401,0.048612176493147},
				{10.843594671713502,0.092220341157586,0.000999627391816,0.031616884600095},
				{10.853233422532803,0.092138440321744,0.000201803093025,0.014205741551398},
				{10.862872173352104,0.092056684829001,0.000047795070803,0.006913397920164},
				{10.872510924171404,0.091975074292805,0.000557159442979,0.02360422510864},
				{10.882149674990705,0.091893608327975,0.0016887642644,0.04109457706803},
				{10.891788425810006,0.091812286550694,0.003344224466068,0.05782926997696},
				{10.901427176629307,0.091731108578501,0.005376607498221,0.073325353720392},
				{10.911065927448607,0.091650074030286,0.007603638952666,0.087198847198032},
				{10.920704678267908,0.091569182526288,0.009824221800338,0.099117212432243},
				{10.930343429087209,0.091488433688081,0.011836770097447,0.108796921360152},
				{10.93998217990651,0.091407827138576,0.013457684960121,0.116007262531795},
				{10.94962093072581,0.091327362502011,0.014538278556818,0.120574784083646},
				{10.959259681545111,0.091247039403944,0.014978584034499,0.122387025597073},
				{10.968898432364412,0.091166857471251,0.014736769093687,0.121395095014943},
				{10.978537183183713,0.09108681633212,0.013833266054275,0.117614905748697},
				{10.988175934003014,0.091006915616039,0.012349213087158,0.111127013309806},
				{10.997814684822314,0.090927154953799,0.010419320622515,0.102075073463187},
				{11.007453435641615,0.090847533977482,0.008219789659692,0.090663055649432},
				{11.017092186460916,0.090768052320459,0.005952366157893,0.077151579101743},
				{11.026730937280217,0.090688709617381,0.003825974966575,0.061854466019642},
				{11.036369688099517,0.090609505504178,0.002037607254728,0.045139863255534},
				{11.046008438918818,0.090530439618049,0.000754210489463,0.027462892955098},
				{11.055647189738119,0.090451511597458,0.000097247621128,0.009861420847313},
				{11.06528594055742,0.090372721082129,0.000131354361042,0.011460993021618},
				{11.07492469137672,0.090294067713041,0.000858154007488,0.029294265778264},
				{11.084563442196021,0.090215551132421,0.002215821324692,0.047072511348893},
				{11.094202193015322,0.09013717098374,0.004084461160504,0.063909789238457},
				{11.103840943834623,0.090058926911705,0.006296835475953,0.079352602200264},
				{11.113479694653924,0.089980818562259,0.008653478039474,0.093024072365565},
				{11.123118445473224,0.089902845582569,0.01094083006299,0.104598422851352},
				{11.132757196292525,0.089825007621025,0.012950746825926,0.113801348084835},
				{11.142395947111826,0.089747304327235,0.01449959175603,0.12041425063517},
				{11.152034697931127,0.089669735352017,0.015445165084469,0.124278578542198},
				{11.161673448750427,0.089592300347396,0.015699905293142,0.125299262939342},
				{11.171312199569728,0.089514998966596,0.015239137363395,0.12344690098741},
				{11.180950950389029,0.089437830864038,0.014103589872801,0.118758535999738},
				{11.19058970120833,0.089360795695335,0.01239592819758,0.111337002822869},
				{11.20022845202763,0.089283893117284,0.010271596180476,0.101348883469312},
				{11.209867202846931,0.089207122787863,0.007924783991613,0.089021255841585},
				{11.219505953666232,0.089130484366224,0.005570789868297,0.074637724163433},
				{11.229144704485533,0.08905397751269,0.003426378481936,0.058535275534811},
				{11.238783455304834,0.088977601888751,0.001689930034443,0.041108758609846},
				{11.248422206124134,0.088901357157056,0.00052319896775,0.022873542964519},
				{11.258060956943435,0.088825242981408,0.000036361186847,0.006030023784954},
				{11.267699707762736,0.088749259026762,0.000277733045539,0.016665324645471},
				{11.277338458582037,0.088673404959217,0.00122912183513,0.03505883391001},
				{11.286977209401337,0.088597680446016,0.002807254606127,0.052983531461456},
				{11.296615960220638,0.088522085155533,0.004871175769839,0.069793808964972},
				{11.306254711039939,0.088446618757276,0.007234956866934,0.085058549640433},
				{11.31589346185924,0.088371280921878,0.009684570829147,0.098410217097345},
				{11.32553221267854,0.088296071321093,0.011997399143681,0.109532639627105},
				{11.335170963497841,0.088220989627793,0.013962591503697,0.118163410172932},
				{11.344809714317142,0.088146035515959,0.015400414882432,0.124098408057606},
				{11.354448465136443,0.088071208660683,0.016178814808213,0.127195970094232},
				{11.364087215955744,0.087996508738155,0.016225661104146,0.127379987062906},
				{11.373725966775044,0.087921935425665,0.01553554274213,0.124641657330647},
				{11.383364717594345,0.087847488401595,0.014170470391892,0.119039784911988},
				{11.393003468413646,0.087773167345418,0.012254404493632,0.110699613791703},
				{11.402642219232947,0.087698971937687,0.009962090563361,0.099810272834819},
				{11.412280970052247,0.087624901860037,0.007503208759762,0.086621064180496},
				{11.421919720871548,0.087550956795177,0.00510327580696,0.071437215839924},
				{11.431558471690849,0.087477136426884,0.002983040684864,0.054617219673502},
				{11.44119722251015,0.087403440440004,0.001338258435239,0.036582214739389},
				{11.45083597332945,0.087329868520441,0.000321696533157,0.017935900678715},
				{11.460474724148751,0.087256420355159,0.00002902941217,0.005387894966451},
				{11.470113474968052,0.087183095632172,0.000489924676395,0.02213424216897},
				{11.479752225787353,0.087109894040541,0.001665151842678,0.040806272099738},
				{11.489390976606654,0.087036815270373,0.003449994589043,0.058736654561209},
				{11.499029727425954,0.086963859012812,0.005683670356116,0.075390121077737},
				{11.508668478245255,0.086891024960037,0.008163909268153,0.090354353897048},
				{11.518307229064556,0.086818312805259,0.010665369557929,0.103273276107271},
				{11.527945979883857,0.086745722242713,0.012960211215962,0.113842923433836},
				{11.537584730703157,0.086673252967656,0.014838947882247,0.121815220240521},
				{11.547223481522458,0.086600904676364,0.01612966714748,0.12700262653772},
				{11.556862232341759,0.086528677066125,0.016713853464478,0.12928206938504},
				{11.56650098316106,0.086456569835237,0.016537356905237,0.128597655131178},
				{11.57613973398036,0.086384582683001,0.015615491201535,0.124961959017673},
				{11.585778484799661,0.086312715309721,0.014031781366524,0.118455820315103},
				{11.595417235618962,0.086240967416695,0.011930461522523,0.109226652070466},
				{11.605055986438263,0.086169338706216,0.009503397723024,0.097485371841238},
				{11.614694737257564,0.086097828881564,0.006972621822625,0.083502226453099},
				{11.624333488076864,0.086026437647002,0.004570068374095,0.067602280835006},
				{11.633972238896165,0.085955164707775,0.002516364831339,0.050163381378637},
				{11.643610989715466,0.085884009770103,0.001000617089476,0.031632532138229},
				{11.653249740534767,0.085812972541178,0.000163043705119,0.012768856844642},
				{11.662888491354067,0.085742052729161,0.000082055200714,0.0090584325749},
				{11.672527242173368,0.085671250043174,0.000766970053957,0.027694224198496},
				{11.682165992992669,0.085600564193304,0.002157044480177,0.046443992939634},
				{11.69180474381197,0.085529994890589,0.004126915849044,0.06424107602651},
				{11.70144349463127,0.085459541847022,0.006497971282441,0.080609994928922},
				{11.711082245450571,0.085389204775543,0.009054610222434,0.09515571565825},
				{11.720720996269872,0.085318983390036,0.011563918402308,0.10753566107254},
				{11.730359747089173,0.085248877405328,0.013796956455047,0.11746044634279},
				{11.739998497908473,0.085178886537179,0.015549715579737,0.124698498706828},
				{11.749637248727774,0.085109010502284,0.016661820689887,0.129080675121752},
				{11.759275999547075,0.085039249018266,0.017031263508238,0.130503883115553},
				{11.768914750366376,0.084969601803673,0.016623812459545,0.128933364415673},
				{11.778553501185677,0.084900068577974,0.0154762296147,0.124403495186833},
				{11.788192252004977,0.084830649061557,0.013692992132822,0.117017059153024},
				{11.797831002824278,0.084761342975722,0.01143681125483,0.106943028079582},
				{11.807469753643579,0.084692150042681,0.008913809550833,0.094412973424379},
				{11.81710850446288,0.08462306998555,0.006354707968235,0.079716422199161},
				{11.82674725528218,0.084554102528349,0.003993741504954,0.06319605608702},
				{11.836386006101481,0.084485247395997,0.002047232020036,0.045246348140327},
				{11.846024756920782,0.084416504314308,0.000693780049726,0.026339704814714},
				{11.855663507740083,0.084347873009987,0.000057891469264,0.007608644377546},
				{11.865302258559383,0.084279353210629,0.000198540976598,0.01409045693362},
				{11.874941009378684,0.084210944644711,0.001103724440707,0.033222348512818},
				{11.884579760197985,0.084142647041593,0.002691503412549,0.0518797013537},
				{11.894218511017286,0.084074460131511,0.004817451374068,0.069407862480181},
				{11.903857261836587,0.084006383645574,0.00728782428418,0.08536875473017},
				{11.913496012655887,0.083938417315764,0.00987725325843,0.099384371298658},
				{11.923134763475188,0.083870560874927,0.012349342504148,0.111127595601397},
				{11.932773514294489,0.083802814056772,0.014478287663787,0.120325756443859},
				{11.94241226511379,0.083735176595871,0.016069536168737,0.126765674252681},
				{11.95205101593309,0.083667648227649,0.016977596889826,0.130298107775308},
				{11.961689766752391,0.083600228688384,0.017119368001878,0.130841002754784},
				{11.971328517571692,0.083532917715205,0.016481759868616,0.128381306538825},
				{11.980967268390993,0.083465715046086,0.015122913738013,0.122975256608852},
				{11.990606019210293,0.083398620419843,0.013166903072022,0.1147471266395},
				{12.000244770029594,0.083331633576132,0.010792399179201,0.103886472551534},
				{12.009883520848895,0.083264754255445,0.008216339364256,0.090644025529848},
				{12.019522271668196,0.083197982199105,0.005674090684313,0.075326560284622},
				{12.029161022487497,0.083131317149266,0.003397925189584,0.058291724880843},
				{12.038799773306797,0.083064758848907,0.001595777024499,0.039947177929101},
				{12.048438524126098,0.082998307041827,0.000432225827682,0.020790041550747},
				{12.058077274945399,0.082931961472649,0.00001344772391,0.003667113839312},
				{12.0677160257647,0.082865721886809,0.000377510796754,0.0194296370721},
				{12.077354776584,0.082799588030554,0.001490903382844,0.038612218051337},
				{12.086993527403301,0.082733559650944,0.003251611898263,0.057022906785461},
				{12.096632278222602,0.082667636495844,0.005498465448038,0.074151638201985},
				{12.106271029041903,0.082601818313921,0.008025890420123,0.089587334038486},
				{12.115909779861203,0.082536104854641,0.010602722991886,0.102969524578324},
				{12.125548530680504,0.082470495868271,0.012993356574713,0.113988405439822},
				{12.135187281499805,0.082404991105865,0.014979287567421,0.122389899776989},
				{12.144826032319106,0.082339590319273,0.016379087328197,0.127980808437034},
				{12.154464783138407,0.082274293261129,0.017064972826609,0.130632969906562},
				{12.164103533957707,0.082209099684853,0.016974461467422,0.130286075493209},
				{12.173742284777008,0.082144009344643,0.016116045661462,0.126948988422365},
				{12.183381035596309,0.082079021995478,0.014568371326291,0.120699508392912},
				{12.19301978641561,0.08201413739311,0.012472999595424,0.11168258411867},
				{12.20265853723491,0.081949355294063,0.010021418664253,0.100107036037696},
				{12.212297288054211,0.081884675455631,0.007437498025887,0.086240930107963},
				{12.221936038873512,0.081820097635871,0.004956994864899,0.070405929188524},
				{12.231574789692813,0.081755621593605,0.002805989854363,0.052971594787801},
				{12.241213540512113,0.081691247088413,0.001180227334641,0.034354436898908},
				{12.250852291331414,0.081626973880633,0.000227248707112,0.015074770549225},
				{12.260491042150715,0.081562801731356,0.000032952589965,0.005740434649462},
				{12.270129792970016,0.081498730402423,0.000613806046017,0.024775109404738},
				{12.279768543789316,0.081434759656424,0.001915414623063,0.043765450106941},
				{12.289407294608617,0.081370889256694,0.003817574809729,0.061786526117993},
				{12.299046045427918,0.081307118967307,0.006145339541077,0.07839221607454},
				{12.308684796247219,0.081243448553081,0.008685077130575,0.09319376122131},
				{12.31832354706652,0.081179877779565,0.011204048044013,0.105849175925053},
				{12.32796229788582,0.081116406413045,0.013471704440183,0.116067671813397},
				{12.337601048705121,0.081053034220535,0.01528076049177,0.123615373201596},
				{12.347239799524422,0.080989760969777,0.016466106946693,0.128320329436502},
				{12.356878550343723,0.080926586429239,0.016919841598539,0.130076291454433},
				{12.366517301163023,0.080863510368109,0.016601046221171,0.12884504732884},
				{12.376156051982324,0.080800532556296,0.015539423972949,0.124657225915506},
				{12.385794802801625,0.080737652764424,0.013832472404866,0.117611531768215},
				{12.395433553620926,0.080674870763829,0.011636462558282,0.107872436508506},
				{12.405072304440226,0.080612186326561,0.00915205809379,0.095666389572253},
				{12.414711055259527,0.080549599225376,0.006605899520841,0.081276684976942},
				{12.424349806078828,0.080487109233735,0.004229845565004,0.065037262896002},
				{12.433988556898129,0.080424716125802,0.002239773914074,0.047326249735996},
				{12.44362730771743,0.08036241967644,0.000815882205595,0.028563651825257},
				{12.45326605853673,0.080300219661211,0.000086288090947,0.009289138331766},
				{12.462904809356031,0.080238115856368,0.000115422609814,0.010743491509461},
				{12.472543560175332,0.08017610803886,0.00089826916732,0.029971138905962},
				{12.482182310994633,0.08011419598632,0.002360963538533,0.048589747257352},
				{12.491821061813933,0.080052379477071,0.004367687261908,0.066088480553782},
				{12.501459812633234,0.079990658290119,0.006733210534535,0.082056142576498},
				{12.511098563452535,0.07992903220515,0.009239925761289,0.096124532567338},
				{12.520737314271836,0.079867501002528,0.011657803579679,0.107971309057912},
				{12.530376065091136,0.079806064463296,0.013765440055589,0.117326212141996},
				{12.540014815910437,0.079744722369166,0.015370267001816,0.123976880916631},
				{12.549653566729738,0.079683474502523,0.0163260792195,0.127773546634269},
				{12.559292317549039,0.079622320646419,0.016546281740139,0.128632351063559}
				// part of the data.
			};
		
		private static double[][] expectedDftTopHits = {
				{11.961689766752391,0.083600228688384,0.017119368001878,0.130841002754784},
				{12.154464783138407,0.082274293261129,0.017064972826609,0.130632969906562},
				{11.759275999547075,0.085039249018266,0.017031263508238,0.130503883115553},
				{12.356878550343723,0.080926586429239,0.016919841598539,0.130076291454433},
				{11.556862232341759,0.086528677066125,0.016713853464478,0.12928206938504},
				{12.559292317549039,0.079622320646419,0.016546281740139,0.128632351063559},
				{11.364087215955744,0.087996508738155,0.016225661104146,0.127379987062906},
				{12.752067333935054,0.078418657446927,0.015962538081091,0.126342938390283},
				{11.161673448750427,0.089592300347396,0.015699905293142,0.125299262939342},
				{12.95448110114037,0.077193365924319,0.015386964715799,0.124044204684454},
				{10.959259681545111,0.091247039403944,0.014978584034499,0.122387025597073},
				{13.156894868345686,0.076005775679329,0.014618280207037,0.120906080107815},
				{10.756845914339795,0.092964053586276,0.014086553788852,0.118686788602824},
				{13.359308635551002,0.074854173017521,0.013681961010536,0.116969914980459},
				{10.56407089795378,0.094660477921792,0.013191119805595,0.1148526003432},
				{13.552083651937018,0.073789391040032,0.012733551895357,0.112843040969998},
				{10.361657130748464,0.096509659350962,0.012200794368609,0.110457206051071},
				{13.754497419142334,0.072703492503353,0.011709892128797,0.108212254984346},
				{10.159243363543148,0.098432527326645,0.01110778206935,0.105393463124379},
				{13.95691118634765,0.071649091023677,0.010588096197285,0.102898475194171},
				{9.966468347157132,0.10033644468306,0.009988979691949,0.099944883270474},
				{14.149686202733665,0.070672945369404,0.009461599966732,0.097270755968748},
				{9.764054579951816,0.102416469696233,0.008906959055977,0.094376687036456},
				{14.352099969938982,0.069676214776551,0.008362043550727,0.091444210044851},
				{9.5616408127465,0.10458456028456,0.007795805192188,0.08829385704673},
				{14.554513737144298,0.068707207816082,0.007239252118178,0.085083794686053},
				{9.359227045541184,0.106846430280416,0.006688151507168,0.081781119503025},
				{14.747288753530313,0.067809074380578,0.006148806534079,0.078414326076804},
				{9.166452029155169,0.109093463514494,0.005674978140957,0.075332450782894},
				{14.949702520735629,0.06689096312204,0.00514917444077,0.071757748297797},
				{8.964038261949852,0.111556864303531,0.004711463710036,0.068640102782821},
				{15.152116287940945,0.065997381553616,0.004191092827347,0.064738650181688},
				{8.761624494744536,0.114134085591071,0.003806864229666,0.061699791163875},
				{15.34489130432696,0.065168268720028,0.003313236941245,0.057560723946497},
				{8.568849478358521,0.116701781554875,0.002993954517472,0.05471704046704},
				{20.337764228725558,0.049169613176436,0.002931622231309,0.054144457069108},
				{20.54017799593091,0.048685069827443,0.002911018813283,0.053953858187185},
				{20.135350461520204,0.049663898421389,0.00290732691842,0.053919633886179},
				{20.732953012316962,0.04823239600292,0.002861813015593,0.053495915877687},
				{19.942575445134153,0.050143974771523,0.002847266049018,0.053359779319429},
				{20.935366779522315,0.047766060682449,0.002800521087437,0.052919949805694},
				{19.7401616779288,0.050658146387833,0.002766721129947,0.052599630511504},
				{21.13778054672767,0.047308656544587,0.00270532733467,0.052012761267503},
				{19.537747910723446,0.051182971782083,0.002638148087373,0.051362905752816},
				{21.340194313933022,0.046859929449991,0.002581651305437,0.050809952818685},
				{15.547305071532277,0.06431982876769,0.002548131289069,0.050479018305325},
				{21.532969330319073,0.046440413519373,0.002466811555519,0.049667006709878},
				{19.335334143518093,0.051718785544507,0.002464638466505,0.049645125304556},
				{21.735383097524426,0.046007930732718,0.002333658398629,0.048307953782264},
				{8.366435711153205,0.119525211753783,0.002290091090823,0.04785489620533},
				{19.142559127132042,0.052239619235791,0.00227667404489,0.047714505602487},
				{21.93779686472978,0.045583428735624,0.002185458487978,0.04674888755872},
				{18.94014535992669,0.052797905242891,0.002054258489634,0.045323928444408},
				{22.13057188111583,0.045186360541063,0.002044442502167,0.045215511742836},
				{22.332985648321184,0.04477681648782,0.00190684061315,0.043667386149736},
				{15.749718838737593,0.063493196941423,0.00186755678811,0.043215237915693},
				{18.737731592721335,0.053368252984713,0.001801715226902,0.042446616200844},
				{22.535399415526538,0.044374629513379,0.001764226688308,0.042002698583636},
				{8.164021943947889,0.122488646755943,0.00167506815355,0.040927596479031},
				{22.72817443191259,0.043998254369075,0.001631340637856,0.040389858106415},
				{18.544956576335284,0.053923016529253,0.001533344722989,0.039157945847411},
				{22.930588199117942,0.043609871291416,0.001511252055199,0.038874825468409},
				{23.133001966323295,0.043228284917616,0.001391139814357,0.037297986733289},
				{15.942493855123608,0.062725443653135,0.001305206382329,0.036127640143367},
				{23.325776982709346,0.042871026364578,0.001288390335583,0.035894154615797},
				{18.34254280912993,0.054518068209292,0.001264878343728,0.035565128197831},
				{23.5281907499147,0.042502205572421,0.0011937476843,0.034550653891058},
				{7.971246927561873,0.125450887306268,0.001157780024343,0.03402616675946},
				{23.72096576630075,0.042156799594587,0.001106705429716,0.033267182473361},
				{4.366354121143386,0.229024026053603,0.001102191623778,0.033199271434441},
				{4.568767888348702,0.218877391987937,0.001095450608474,0.033097592185443},
				{4.173579104757371,0.23960250300758,0.001082958052982,0.032908328018633},
				{4.771181655554019,0.209591684449055,0.001061216563784,0.032576319064377},
				{3.971165337552053,0.251815251947286,0.001045585374175,0.03233551258562},
				{23.923379533506104,0.04180011434419,0.001035555569964,0.032180049253603},
				{4.973595422759335,0.201061790314501,0.000999992472651,0.031622657583626},
				{18.140129041924578,0.055126399469863,0.000994392191472,0.031533984706534},
				{3.768751570346728,0.265339856271821,0.000987469960964,0.031424034765833},
				{24.116154549892155,0.041465980736322,0.000968977882776,0.031128409576716},
				{24.318568317097508,0.041120841776567,0.000917078162184,0.03028329840331},
				{5.176009189964651,0.193199038737957,0.000914052281883,0.030233297568797},
				{3.566337803141403,0.280399685952114,0.000912477990427,0.030207250626746},
				{24.51134333348356,0.04079743759429,0.000871467747423,0.029520632571532},
				{16.13526887150965,0.061976035724184,0.000848745775164,0.029133241755154},
				{24.713757100688913,0.040463293214617,0.000833336436539,0.028867567208532},
				{3.363924035936077,0.297271873358975,0.000825010611131,0.028722997948184},
				{5.378422957169967,0.18592810717255,0.00080747146683,0.028416042420257},
				{24.906532117074963,0.040150109830603,0.000806131026729,0.028392446649225},
				{25.099307133461014,0.039841737251259,0.000779862784474,0.02792602342752},
				{25.301720900666368,0.039523003353249,0.000762769616996,0.02761828410665},
				{7.768833160356557,0.128719458811766,0.000752326434401,0.027428569674715},
				{25.49449591705242,0.039224152666268,0.000748379866904,0.027356532435681},
				{17.937715274719224,0.055748459861517,0.000736317228828,0.027135165907516},
				{25.68727093343847,0.038929787543069,0.000731796035486,0.02705172888165},
				{3.161510268730752,0.316304523787446,0.000729672695899,0.02701245445899},
				{25.889684700643823,0.038625422115517,0.000722591772988,0.026881067184693},
				{26.082459717029874,0.038339942277264,0.000709420137221,0.026634942035255},
				{26.284873484235227,0.038044695197021,0.000693274623963,0.026330108696382},
				{5.580836724375283,0.179184600694789,0.000686028238293,0.026192140773391},
				{26.477648500621278,0.037767704332828,0.000678655832111,0.026051023628849},
				{26.67042351700733,0.037494717673393,0.000656020992301,0.025612906752282},
				{26.872837284212682,0.037212296916168,0.000633812694714,0.025175636927666},
				{2.968735252344727,0.336843778578839,0.000631972425168,0.02513906174},
				{27.065612300598733,0.036947252066338,0.000605155877077,0.024599916200601},
				{27.268026067804087,0.036672988265209,0.000572268953561,0.023922143582062},
				{5.802527993219201,0.17233867741243,0.000563181427848,0.023731443863534},
				{27.460801084190138,0.036415543630143,0.000536724723589,0.023167320164163},
				{2.766321485139402,0.36149088432851,0.00053664366768,0.02316557073935},
				{16.3280438878957,0.061244323377972,0.000516672058061,0.022730421422871},
				{17.725662756694568,0.056415379990366,0.000506703739198,0.022510080834991},
				{27.66321485139549,0.036149088432849,0.000494659435815,0.022240940533503},
				{6.033858012882419,0.165731443773615,0.000487311835848,0.022075140675604},
				{27.855989867781542,0.035898921730892,0.000453158991685,0.021287531366621},
				{6.245910530907036,0.160104758954141,0.000447656360692,0.021157891215617},
				{2.563907717934077,0.390029638354446,0.000444533785018,0.021083969859066},
				{7.576058143970542,0.131994763107231,0.000438261890559,0.02093470540893},
				{32.87777904463784,0.030415679801312,0.000419421087835,0.020479772650955},
				{32.68500402825186,0.030595070422376,0.000417816841226,0.020440568515234},
				{32.48259026104658,0.030785722196521,0.000416070532396,0.020397807048701},
				{6.457963048931653,0.154847587764602,0.000412623164048,0.020313127874558},
				{33.07055406102382,0.030238380589413,0.00041248689003,0.020309773263862},
				{32.2898152446606,0.030969517552918,0.000408488918384,0.020211108786603},
				{28.048764884167593,0.035652193746487,0.000406579138672,0.020163807643208},
				{33.2729678282291,0.030054427520938,0.000404107215661,0.020102418154566},
				{32.08740147745532,0.031164879484012,0.000398191739957,0.019954742292425},
				{31.894626461069308,0.031353243820573,0.000388231510842,0.019703591318388},
				{33.47538159543438,0.029872699050468,0.000387129098094,0.019675596511765},
				{6.660376816136969,0.150141655285504,0.000376053300994,0.019392093775407},
				{31.701851444683257,0.031543898997347,0.000375845933183,0.019386746327906},
				{33.66815661182036,0.029701655826589,0.000368336469661,0.019192093936332},
				{16.48226390100454,0.060671277077359,0.000363935516954,0.019077094038494},
				{31.509076428297206,0.031736887060959,0.000361919580873,0.019024184105314},
				{28.251178651372946,0.03539675325905,0.000359836601094,0.018969359533034},
				{2.361493950728751,0.423460750213399,0.000358625000048,0.018937396865667},
				{17.494332737031307,0.057161368486106,0.000357741437348,0.018914053963857},
				{31.306662661091853,0.03194208245144,0.00035121026192,0.018740604630588},
				{16.66540016657129,0.060004559746839,0.000345354020381,0.018583703085788},
				{33.87057037902564,0.029524155891371,0.000343414728798,0.018531452420076},
				{16.85817518295734,0.059318401259167,0.00034080770796,0.018460977979511},
				{31.1138876447058,0.032139988786331,0.000339707472089,0.018431154930964},
				{17.060588950162693,0.058614623617109,0.00033719648368,0.018362910544891},
				{6.862790583342285,0.145713319947027,0.00033688982695,0.018354558751154},
				{17.27264146818735,0.057895024443238,0.000333468541126,0.018261121026001},
				{30.92111262831975,0.032340362781258,0.000327067971812,0.018085020647272},
				{30.718698861114397,0.032553462128107,0.00031598075656,0.017775847562349},
				{34.063345395411616,0.029357069553559,0.000313588452191,0.017708428845909},
				{28.443953667758997,0.035156856591758,0.000312872981009,0.017688215879752},
				{30.525923844728347,0.032759041301634,0.00030516542053,0.017468984530599},
				{7.0555655997283,0.141732081697109,0.000296346086864,0.017214705541005},
				{30.333148828342296,0.032967233492938,0.00029229885816,0.01709674992975},
				{39.19116083127868,0.025515957649356,0.000290336358437,0.017039259327715},
				{38.9887470640734,0.025648426156312,0.000288901624517,0.016997106357179},
				{39.38393584766466,0.025391063094048,0.000285947421455,0.016909979936553},
				{34.265759162616895,0.029183652265057,0.000283237131746,0.01682965037503},
				{2.159080183523426,0.463160195545906,0.000281161509596,0.016767871349587},
				{38.78633329686812,0.025782277286849,0.000281138297997,0.016767179190215},
				{30.130735061136942,0.03318870243195,0.000281113182679,0.01676643023063},
				{39.58634961486994,0.025261232968659,0.000278565784999,0.016690290141255},
				{38.59355828048214,0.025911059890679,0.000270148542633,0.016436196112037},
				{29.93796004475089,0.033402409466283,0.000268645469568,0.016390407852388},
				{28.636728684145048,0.034920189768521,0.000266884632225,0.016336604060373},
				{39.78876338207522,0.025132723789312,0.000264702122555,0.016269668790569},
				{7.257979366933617,0.137779394159739,0.000256673886977,0.016021045127475},
				{29.735546277545538,0.033629784052602,0.000254827473138,0.015963316483055},
				{38.39114451327686,0.026047673563214,0.000254647616686,0.015957682058688},
				{34.468172929822174,0.029012271756789,0.000249962857677,0.015810213713825},
				{39.9815383984612,0.025011543828901,0.0002485518815,0.015765528265806},
				{29.542771261159487,0.033849227994217,0.000242291808122,0.015565725428708},
				{7.412199380042429,0.134912722759797,0.000237418765769,0.015408399195548},
				{38.188730746071585,0.026185735437224,0.000234840522149,0.01532450724},
				{29.349996244773436,0.034071554614869,0.000228670389283,0.015121851384094},
				{40.183952165666476,0.024885556201075,0.000227911491649,0.015096737781689},
				{28.819864949711796,0.034698288897082,0.000226298113883,0.015043208231063},
				{29.157221228387385,0.034296821091662,0.000215682375502,0.014686128676487},
				{34.67058669702745,0.028842892355373,0.000215549647934,0.014681609173872},
				{1.956666416318101,0.511073319223069,0.000213609451339,0.014615384064029},
				{37.986316978866306,0.026325268663354,0.000211960457221,0.01455886181062},
				{44.82983006056859,0.022306575747642,0.000207845111853,0.014416834321481},
				{45.03224382777387,0.022206310745352,0.000207750174986,0.014413541375584},
				{28.98372371363994,0.034502122980471,0.000207690609813,0.014411474935383},
				{40.376727182052456,0.024766742373426,0.000206449918508,0.014368365199555},
				{45.22501884415985,0.022111654689319,0.000206093597777,0.014355960357182},
				{44.63705504418261,0.022402911639448,0.000203899712162,0.014279345648935},
				{45.42743261136513,0.022013130448182,0.000200199248622,0.014149178372668},
				{44.43464127697733,0.022504963948435,0.00019828304231,0.014081301158286},
				{45.62020762775111,0.021920110670248,0.000192101598613,0.013860072099841},
				{44.24186626059135,0.022603024793526,0.000190511421901,0.013802587507451},
				{37.78390321166103,0.026466296888337,0.000187313886309,0.013686266339247},
				{40.569502198438435,0.024649057686453,0.000182953219037,0.013526020073815},
				{34.87300046423273,0.028675479215666,0.000181623714909,0.013476784294075},
				{45.822621394956386,0.02182328224701,0.000181148120171,0.01345912776413},
				{44.04909124420537,0.022701943939231,0.000180610931293,0.013439156643656},
				{43.84667747700009,0.022806745175266,0.00017066980508,0.013064065411654},
				{46.025035162161664,0.021727305508332,0.000167444091099,0.012940018975988},
				{37.58148944445575,0.026608844268346,0.000162180172081,0.012734997922303},
				{43.65390246061411,0.022907459439674,0.000160568280679,0.012671553996218},
				{40.762277214824415,0.02453248612019,0.000159751370331,0.012639278869114},
				{1.75425264911278,0.570043317594962,0.000156677164263,0.012517074908438},
				{46.217810178547644,0.021636680667838,0.000152440338101,0.012346673159247},
				{43.46112744422813,0.023009067155086,0.000150400942673,0.012263806206617},
				{35.07541423143801,0.028509998296861,0.000149557696092,0.012229378401719},
				{43.26835242784215,0.023111580263373,0.000140624115081,0.011858503914112},
				{40.955052231210395,0.024417011956291,0.000138794139283,0.011781092448617},
				{37.37907567725047,0.026752935482795,0.000137721074669,0.011735462269082},
				{46.42022394575292,0.021542334676554,0.000136478300437,0.011682392753067},
				{43.07557741145617,0.02321501092018,0.000131478735541,0.011466417729235},
				{42.873163644250894,0.023324614164182,0.000124177413477,0.011143491978585},
				{41.147827247596375,0.024302619770973,0.000121477926036,0.011021702501693},
				{35.27782799864329,0.02834641633942,0.000120406409411,0.010972985437469},
				{46.6226377129582,0.021448807897929,0.000120103856763,0.010959190515843},
				{42.680388627864914,0.023429964724996,0.00011761815192,0.010845190266648},
				{37.17666191004519,0.026898595748582,0.000114907895828,0.010719510055418},
				{42.487613611478935,0.02353627128001,0.000111729497809,0.010570217491087},
				{1.55183888190746,0.644396793803,0.000110386634766,0.010506504402791},
				{41.340602263982355,0.024189294428138,0.000108573221612,0.010419847485074},
				{42.294838595092955,0.023643546901158,0.000106584683948,0.010323985855652},
				{46.83469023098278,0.021351694546673,0.000104981462153,0.010246046171721},
				{42.102063578706975,0.02375180489979,0.000102443640685,0.010121444594792},
				{41.523738529549036,0.02408260998196,0.000101922725775,0.010095678569303},
				{41.909288562320995,0.023861058832172,0.000099797121078,0.00998985090369},
				{41.716513545935015,0.023971322505148,0.000099347623461,0.009967327799399},
				{35.48988051666787,0.02817704611686,0.000094915340998,0.00974245046168},
				{36.97424814283991,0.027045850834797,0.000094472682887,0.00971970590535},
				{47.04674274900736,0.021255456628208,0.000091970366669,0.009590118178064},
				{47.258795267031935,0.021160082358206,0.000082579034826,0.009087300744777},
				{36.762195624815334,0.027201857315752,0.000077745514094,0.008817341668204},
				{47.461209034237214,0.021069838302657,0.000076487872023,0.008745734504499},
				{35.692294283873146,0.028017251904477,0.000075543136523,0.008691555472003},
				{1.349425114702139,0.741056312873451,0.000074184068593,0.008613017391864},
				{47.67326155226179,0.020976118843972,0.000072164366751,0.008494961256578},
				{47.87567531946707,0.020887433823693,0.000068949378362,0.008303576239307},
				{48.07808908667235,0.020799495549777,0.000066074472628,0.008128620585821},
				{36.550143106790756,0.027359673998491,0.000065317449082,0.008081921125677},
				{48.27086410305833,0.020716430471702,0.000063324606168,0.00795767592757},
				{35.91398555271702,0.027844305905067,0.000062526587714,0.007907375526333},
				{48.47327787026361,0.020629923205863,0.000061073493103,0.007814953173429},
				{48.67569163746889,0.020544135406393,0.000058705286415,0.007661937510544},
				{36.33809058876618,0.027519332573549,0.000058266554847,0.007633253228257},
				{36.1260380707416,0.027680865475528,0.000057136611158,0.007558876315852},
				{48.878105404674166,0.020459058134941,0.000056121219567,0.007491409718292},
				{49.070880421060146,0.020378684698936,0.000053699988631,0.007328027608534},
				{49.273294188265425,0.020294969444892,0.00005134433777,0.007165496337997},
				{49.4757079554707,0.020211939178314,0.000048758846745,0.006982753521744},
				{1.147011347496818,0.871830956321706,0.000047075966311,0.006861192776102},
				{49.66848297185668,0.02013349190807,0.000046459079308,0.006816089737364},
				{49.99620049971285,0.020001519915613,0.000046212400168,0.006797970297642},
				{49.86125798824266,0.020055651227969,0.000044133191245,0.006643281662301},
				{0.944597580291497,1.058651875533501,0.00002777697084,0.005270386213591},
				{0.742183813086176,1.347375114315364,0.000014856841596,0.003854457367254},
				{0.530131295061555,1.88632516004151,0.000006881505781,0.002623262430895},
				{0.327717527856234,3.051408347125972,0.000002530450095,0.001590738852037},
				{0.125303760650913,7.980606446329458,0.000000614483183,0.000783889777534}
		};
		
		private static double[][] expectedSpwTopHits = {
				{0,Double.NaN,1,1},
				{0.202413767205321,4.940375419156333,0.993380403794941,0.99668470631135},
				{0.395188783591341,2.530436190299587,0.978449222669663,0.989165922719573},
				{0.597602550796662,1.673352964552952,0.959629184753725,0.979606647973423},
				{0.800016318001982,1.249974503641964,0.928520658410141,0.963597767956185},
				{0.992791334388002,1.007261007789156,0.886667558629905,0.941630266415595},
				{1.195205101593323,0.836676482276476,0.846194165584622,0.919888126667924},
				{1.397618868798644,0.715502646912297,0.79606627018006,0.892225459275883},
				{1.600032636003965,0.624987251820982,0.737921396658872,0.859023513449354},
				{1.792807652389984,0.557784321517651,0.680791603529185,0.825100965657649},
				{1.995221419595305,0.501197506291223,0.620307854234372,0.787596250774705},
				{2.197635186800631,0.455034578080188,0.555804340965627,0.745522864146786},
				{2.390410203186655,0.418338241138237,0.492699082688982,0.701925268592734},
				{2.59282397039198,0.385679865435995,0.431662538856299,0.657010303462814},
				{2.795237737597306,0.357751323456146,0.370525056793414,0.608707694048148},
				{2.98801275398333,0.334670592910588,0.312355256608265,0.558887516955125},
				{3.190426521188655,0.31343771541475,0.259499601097199,0.509411033544817},
				{3.392840288393981,0.294738306256483,0.209628243667668,0.457851770410105},
				{3.585615304780005,0.278892160758823,0.164543005066433,0.405639008314577},
				{3.78802907198533,0.263989526214458,0.125762388136637,0.354629931247543},
				{3.990442839190656,0.250598753145608,0.091731190253224,0.302871573861305},
				{7.980885678381174,0.125299376572806,0.077318051174895,0.278061236375901},
				{7.788110661995159,0.128400846289955,0.07663783418248,0.276835391853138},
				{8.18329944558649,0.122200098707048,0.076448957288472,0.276494045665493},
				{7.585696894789843,0.131827044221453,0.074808783792908,0.273511944515972},
				{8.385713212791806,0.11925044115205,0.073811717255419,0.271683119194807},
				{7.383283127584527,0.135441101569561,0.07102597896575,0.266506996091566},
				{8.578488229177822,0.116570655957622,0.07039446892636,0.265319560014636},
				{8.780901996383138,0.113883516797238,0.065890079365946,0.256690629680841},
				{7.190508111198511,0.139072230297967,0.065729358605252,0.256377375377103},
				{4.183217855576672,0.239050423507562,0.063598778636688,0.252187982736466},
				{8.983315763588454,0.111317471890863,0.060244072132317,0.245446678796674},
				{6.988094343993195,0.14310052938246,0.059389253082036,0.243699103572493},
				{9.17609077997447,0.108978869540214,0.054493070911252,0.233437509649269},
				{6.785680576787879,0.147369153128243,0.051597468171706,0.227150760887359},
				{9.378504547179785,0.106626807607692,0.048238366999692,0.219632345067141},
				{6.592905560401864,0.151678192693397,0.043114915564921,0.207641314686941},
				{9.5712795635658,0.104479238471585,0.041671234407601,0.204135333559874},
				{4.385631622781988,0.228017327037983,0.041049025262745,0.202605590403486},
				{9.773693330771117,0.102315467260635,0.035585967614439,0.188642433228685},
				{6.390491793196547,0.156482479339794,0.034227528781739,0.185006834419},
				{9.976107097976433,0.100239501258245,0.029494544661402,0.171739758534248},
				{6.197716776810532,0.161349741527657,0.025299415012278,0.15905789830209},
				{10.168882114362448,0.098339226352876,0.023995956465564,0.154906282847288},
				{4.578406639168003,0.218416597478489,0.02397642237957,0.154843218707086},
				{10.371295881567764,0.096419966359,0.018942007488579,0.137629965808971},
				{6.004941760424517,0.166529508510888,0.017065834867405,0.130636269341272},
				{10.56407089795378,0.094660477921792,0.014616638988556,0.120899292754574},
				{19.161836628770647,0.052187064286862,0.013472938903058,0.116072989549931},
				{18.969061612384596,0.052717420631241,0.013399794380712,0.115757480884441},
				{19.364250395976,0.051641554904072,0.013308551455997,0.11536269525283},
				{18.766647845179243,0.053286021470099,0.013243984563105,0.115082511977732},
				{19.55702541236205,0.051132520355979,0.012960244106794,0.113843067890823},
				{4.751904153915417,0.210441954974204,0.012863887617606,0.1134190796013},
				{18.56423407797389,0.053867021704196,0.012817593961419,0.113214813347983},
				{19.759439179567405,0.050608723805991,0.012532144926355,0.111947063053725},
				{18.37145906158784,0.05443225802848,0.012260726414356,0.11072816450369},
				{19.96185294677276,0.050095549880387,0.011881154911508,0.10900071060093},
				{5.29167419979626,0.188976108929477,0.01163842428747,0.107881528944813},
				{5.484449216182275,0.182333714942501,0.011538108069905,0.107415585786722},
				{18.169045294382485,0.055038665147099,0.011517577181264,0.107319975686097},
				{5.089260432590944,0.19649220417099,0.011513959416026,0.107303119321045},
				{5.850721747315704,0.170919083694041,0.011367764689465,0.106619719983995},
				{5.686862983387591,0.175843870851327,0.011316681163878,0.106379890787114},
				{20.15462796315881,0.049616395888226,0.011253033958655,0.106080318432097},
				{4.896485416204928,0.204228117721029,0.011241330051694,0.106025138772341},
				{10.756845914339795,0.092964053586276,0.010917848974066,0.104488511206093},
				{16.462986399365935,0.060742320727333,0.010719062248832,0.103532904184284},
				{17.976270277996434,0.055628892119186,0.01066635849607,0.103278063963601},
				{16.66540016657129,0.060004559746839,0.010596607280361,0.102939823588155},
				{16.26057263216058,0.061498449201117,0.01059481369934,0.102931111425748},
				{20.357041730364163,0.049123051042747,0.010466080825999,0.102303865156696},
				{16.85817518295734,0.059318401259167,0.010330512224339,0.101639127428066},
				{16.06779761577453,0.062236283024766,0.01032066215409,0.101590659777805},
				{15.865383848569202,0.063030306076722,0.009874594502785,0.0993709942729},
				{17.060588950162693,0.058614623617109,0.009866488483027,0.09933019924991},
				{17.783495261610383,0.056231915339991,0.00973862840128,0.098684489162583},
				{20.549816746750214,0.048662234428837,0.009724443454911,0.098612592780592},
				{17.253363966548743,0.057959711621387,0.009316354275514,0.0965212633336},
				{15.662970081363886,0.063844851570637,0.009231442882976,0.096080398016326},
				{17.600358996043635,0.056817022892816,0.008947082341876,0.094589018082843},
				{20.742591763136264,0.048209983179498,0.008906669751686,0.094375154313441},
				{17.436500232115492,0.057350958431334,0.008824279755416,0.093937637586946},
				{15.47019506497787,0.064640426045037,0.008441928481882,0.091879967794302},
				{20.945005530341618,0.047744079062255,0.008199465346525,0.090550899203295},
				{10.94962093072581,0.091327362502011,0.007962801084557,0.089234528544487},
				{27.634298598937583,0.036186914475855,0.007921389028577,0.089002185526971},
				{27.827073615323634,0.035936225771485,0.007850969791582,0.08860569841484},
				{27.43188483173223,0.036453929656457,0.007829651697463,0.088485319106976},
				{28.029487382528988,0.035676713824717,0.0076809888492,0.087641250842283},
				{15.267781297772554,0.065497401390331,0.00760720556455,0.087219295826958},
				{27.229471064526876,0.036724914620275,0.007571724524615,0.087015656778624},
				{21.13778054672767,0.047308656544587,0.00753362752,0.086796471817698},
				{28.23190114973434,0.035420923114468,0.007340210572526,0.085675028873799},
				{27.036696048140826,0.036986767843949,0.007206053634661,0.084888477631895},
				{21.33055556311372,0.046881104293845,0.006949302147179,0.08336247445451},
				{28.424676166120392,0.035180699831223,0.006917981690103,0.083174405258489},
				{26.834282280935472,0.037265762860013,0.006728056673616,0.082024732085},
				{15.065367530567238,0.066377404863906,0.006682682023075,0.081747672890882},
				{21.52333057949977,0.046461210838459,0.006470371078494,0.080438616836032},
				{28.617451182506443,0.034943712968096,0.006383784456364,0.079898588575544},
				{26.63186851373012,0.037548998842663,0.006139722844382,0.078356383558599},
				{21.71610559588582,0.046048772215837,0.006089294674273,0.078033932838688},
				{28.819864949711796,0.034698288897082,0.005813046685972,0.076243338633428},
				{21.918519363091175,0.04562351970197,0.005775251677352,0.075995076665214},
				{11.142395947111826,0.089747304327235,0.005748661985809,0.075819931322897},
				{14.862953763361922,0.067281377303686,0.005721544067984,0.075640888862998},
				{22.111294379477226,0.045225755798727,0.005522027197667,0.074310343813405},
				{26.429454746524765,0.037836573232049,0.005472459077881,0.07397607098164},
				{29.003001215278545,0.034479190363003,0.005263150293295,0.072547572621658},
				{22.304069395863277,0.044834867676007,0.005249816447068,0.072455617084306},
				{22.50648316306863,0.044431641885344,0.004921131089518,0.070150773976611},
				{29.195776231664595,0.034251529812571,0.004791750945042,0.069222474277088},
				{14.660539996156606,0.06821031150709,0.004776735544638,0.069113931624804},
				{26.227040979319412,0.038128586476397,0.004759881923052,0.068991897517408},
				{22.69925817945468,0.044054303100755,0.004582977217899,0.067697689900758},
				{29.378912497231344,0.034038019620169,0.004465155447889,0.06682181865146},
				{29.571687513617395,0.033816129009868,0.004250269263082,0.065194089172885},
				{11.335170963497841,0.088220989627793,0.004179981130661,0.064652773572844},
				{22.901671946660034,0.043664934260218,0.004176024133945,0.064622164417058},
				{29.754823779184143,0.03360799604868,0.004100190208831,0.064032727638538},
				{26.02462721211406,0.038425142148992,0.004035329959135,0.063524247017461},
				{29.957237546389496,0.033380914994297,0.00397708411116,0.063064126975323},
				{38.9983858148927,0.025642086950638,0.003950013443555,0.062849132400973},
				{38.79597204768742,0.025775871752119,0.003939780932731,0.062767674265749},
				{14.45812622895129,0.06916525586819,0.00389582841137,0.062416571608584},
				{39.19116083127868,0.025515957649356,0.003888928927449,0.062361277468063},
				{38.59355828048214,0.025911059890679,0.003848583393612,0.062036951840105},
				{30.150012562775547,0.033167482033976,0.003844647131924,0.062005218586211},
				{33.46574284461508,0.029881302938444,0.003812370464123,0.061744396216359},
				{33.66815661182036,0.029701655826589,0.003809039007612,0.061717412515527},
				{39.39357459848396,0.025384850453213,0.003768182949972,0.061385527202854},
				{33.2729678282291,0.030054427520938,0.003741491613031,0.061167733430552},
				{33.87057037902564,0.029524155891371,0.003723179115034,0.061017858984353},
				{23.104085713865388,0.043282387902494,0.003718417678627,0.060978829757769},
				{38.39114451327686,0.026047673563214,0.003685214836411,0.060705970352269},
				{30.3524263299809,0.032946295269062,0.003677037118143,0.060638577804422},
				{33.07055406102382,0.030238380589413,0.003612673344814,0.060105518422308},
				{34.072984146230915,0.029348764866274,0.003568090117829,0.059733492429532},
				{39.59598836568924,0.025255083690916,0.003567446343661,0.059728103466129},
				{30.54520134636695,0.032738366614792,0.003530079535332,0.059414472440074},
				{38.188730746071585,0.026185735437224,0.003461841571065,0.058837416420718},
				{32.87777904463784,0.030415679801312,0.003403166624712,0.058336666211845},
				{34.275397913436194,0.029175445388717,0.003360021846621,0.057965695429465},
				{30.747615113572305,0.03252284758692,0.003336137391093,0.057759305666647},
				{25.822213444908705,0.038726347070652,0.003329537332978,0.057702143226901},
				{39.78876338207522,0.025132723789312,0.003321738890238,0.057634528628572},
				{23.29686073025144,0.042924238230153,0.003260549580348,0.057101222231649},
				{37.986316978866306,0.026325268663354,0.003192968121419,0.056506354699443},
				{14.246073710926673,0.070194779297892,0.003191073415098,0.056489586784626},
				{30.940390129958356,0.032320213022516,0.003173358528931,0.056332570764443},
				{32.67536527743256,0.030604095516896,0.00316802833686,0.056285240843937},
				{11.518307229064556,0.086818312805259,0.003117010923685,0.055830197238456},
				{34.47781168064147,0.029004160973519,0.003116663079807,0.055827081956766},
				{39.991177149280496,0.02500551549826,0.003029838096812,0.05504396512618},
				{31.133165146344407,0.032120087864482,0.002978075707457,0.054571748253624},
				{32.48259026104658,0.030785722196521,0.002917683137942,0.054015582362338},
				{37.78390321166103,0.026466296888337,0.002894291631403,0.053798621092021},
				{34.68022544784675,0.028834875987292,0.00285568008515,0.053438563651639},
				{31.33557891354976,0.03191260652177,0.002818642060855,0.053090884913089},
				{14.024382442082755,0.071304387492979,0.002800756531287,0.052922174287222},
				{23.499274497456792,0.04255450525114,0.002792587034136,0.052844933854969},
				{40.183952165666476,0.024885556201075,0.002713228382828,0.052088658869546},
				{32.2898152446606,0.030969517552918,0.002691681190872,0.051881414696128},
				{13.802691173238838,0.072449639526735,0.002677316659408,0.051742793308901},
				{25.61979967770335,0.039032311438028,0.002668661484487,0.051659089079145},
				{31.52835392993581,0.031717482055113,0.002668500668949,0.051657532548009},
				{34.88263921505203,0.02866755562373,0.002593426250066,0.050925693417621},
				{13.600277406033522,0.07352791197894,0.002587137083247,0.050863907471284},
				{37.58148944445575,0.026608844268346,0.002581553001152,0.050808985437145},
				{31.721128946321862,0.031524729201542,0.002554404547962,0.050541117399223},
				{32.10667897909392,0.031146167457903,0.002546709489842,0.050464933268975},
				{31.913903962707913,0.031334304984076,0.002506974960257,0.050069701020245},
				{13.397863638828206,0.074638765325384,0.002418109892722,0.049174280805339},
				{11.711082245450571,0.085389204775543,0.002392187296294,0.048909991783823},
				{40.376727182052456,0.024766742373426,0.002375151518688,0.048735526248195},
				{35.08505298225731,0.028502165879747,0.002343909907391,0.048413943315855},
				{23.701688264662145,0.042191087353509,0.002337790511986,0.048350703324623},
				{37.36943692643117,0.026759835904638,0.002287392489548,0.047826692228791},
				{13.19544987162289,0.075783698905978,0.002151449932126,0.046383724862563},
				{35.28746674946259,0.028338673532444,0.002118098425095,0.046022803316343},
				{25.417385910498,0.039343148958012,0.002072844501637,0.045528502079877},
				{40.579140949257734,0.024643202803392,0.002058141568209,0.0453667451798},
				{44.82983006056859,0.022306575747642,0.002043141953868,0.045201127794203},
				{37.15738440840659,0.026912550921473,0.002039916292944,0.04516543250035},
				{44.63705504418261,0.022402911639448,0.002028838353794,0.045042628184796},
				{45.03224382777387,0.022206310745352,0.002014153698279,0.04487932372796},
				{44.43464127697733,0.022504963948435,0.001979919377237,0.044496284982426},
				{35.499519267487166,0.028169395547727,0.001944494358626,0.044096421154394},
				{45.23465759497915,0.022106943064625,0.001941595473967,0.044063539054041},
				{23.9041020318675,0.041833824113822,0.00191798044874,0.043794753666847},
				{44.23222750977205,0.022607950272888,0.001880241216718,0.043361748312513},
				{36.945331890382015,0.027067018993551,0.00186129711808,0.043142752787466},
				{45.437071362184426,0.022008460713255,0.001833691902648,0.042821628911656},
				{11.903857261836587,0.084006383645574,0.001819099562672,0.042650903421521},
				{35.701933034692445,0.028009687851587,0.001813999978559,0.042591078626388},
				{12.993036104417573,0.076964305491309,0.00178937436136,0.042300997167441},
				{40.771915965643714,0.024526686478081,0.001758685458234,0.041936683920328},
				{44.03945249338607,0.022706912629084,0.001750805366182,0.041842626186482},
				{36.73327937235744,0.027223270480787,0.001745314847367,0.041776965511714},
				{35.91398555271702,0.027844305905067,0.001717791778758,0.041446251685256},
				{45.639485129389705,0.021910851911781,0.001700227820428,0.04123381889212},
				{36.53086560515216,0.027374111821182,0.001686540660467,0.04106751344393},
				{36.1163993199223,0.027688252949634,0.001677335158637,0.040955282426531},
				{36.31881308712758,0.027533939437972,0.0016620246488,0.040767936528606},
				{43.83703872618079,0.022811759850986,0.001585836301525,0.03982256020806},
				{24.116154549892155,0.041465980736322,0.001571403057582,0.039640926547972},
				{25.205333392473342,0.039674142945422,0.001561341456787,0.039513813493348},
				{45.841898896594984,0.02181410508879,0.00155165060209,0.039390996459722},
				{40.964690982029694,0.024411266777007,0.001494477439026,0.038658471762684},
				{43.644263709794814,0.022912518507571,0.001406953736401,0.037509381978395},
				{46.04431266380026,0.021718208876342,0.001398186408398,0.037392330876776},
				{12.790622337212257,0.078182278675422,0.001358316454147,0.03685534498749},
				{12.106271029041903,0.082601818313921,0.001337953907782,0.03657805226884},
				{24.318568317097508,0.041120841776567,0.001286134682268,0.035862719950781},
				{41.157465998415674,0.024296928290933,0.001274862233795,0.03570521297787},
				{46.26600393264414,0.021614142458809,0.001272898720271,0.035677706208098},
				{43.451488693408834,0.02301417120725,0.001216339775263,0.034876063070002},
				{46.47805645066872,0.021515529614742,0.001200156586449,0.034643276208371},
				{24.993280874448686,0.040010753491044,0.001184085561194,0.03441054433155},
				{46.690108968693295,0.021417812510794,0.001171264158919,0.034223736776093},
				{46.892522735898574,0.021325361521538,0.001162014818091,0.034088338447208},
				{47.10457525392315,0.021229360303312,0.001159728955266,0.03405479342568},
				{47.30698902112843,0.021138525632087,0.001158450523464,0.034036018031846},
				{47.50940278833371,0.021048464962931,0.001151871518052,0.033939232726332},
				{47.71181655553899,0.020959168444906,0.001139238121812,0.033752601704337},
				{47.91423032274427,0.02087062639354,0.001120869420957,0.033479388001523},
				{41.350241014801654,0.024183655897968,0.001101298237288,0.033185813795772},
				{48.116644089949546,0.020782829287317,0.001098052509233,0.033136875369184},
				{24.530620835122164,0.040765376739599,0.001094225333172,0.033079076969766},
				{48.319057857154824,0.02069576776427,0.001072816292055,0.032753874458683},
				{48.5214716243601,0.02060943261865,0.001047604752362,0.032366722916624},
				{43.258713677022854,0.023116729902469,0.001034620461373,0.032165516650181},
				{49.948006745616354,0.020020818950653,0.001026543914147,0.032039724002353},
				{24.761950854785425,0.040384540211085,0.00102615467031,0.032033649032079},
				{48.72388539156538,0.020523814797683,0.001024897102194,0.032014014153082},
				{49.745592978411075,0.020102283240125,0.001018037605203,0.031906701571972},
				{49.543179211205796,0.020184413191106,0.001006910685004,0.031731855996831},
				{48.92629915877066,0.020438905398401,0.001006819011641,0.031730411463463},
				{49.34076544400052,0.020267216995953,0.000996948804302,0.03157449610528},
				{49.12871292597594,0.020354695664565,0.000994793104289,0.031540340903186},
				{41.533377280368335,0.024077021072704,0.000969722997985,0.031140375687919},
				{12.308684796247219,0.081243448553081,0.000917998001418,0.03029848183355},
				{12.588208570006941,0.079439420981841,0.000909798022683,0.030162858330787},
				{43.065938660636874,0.023220206759687,0.000878919845529,0.029646582358329},
				{41.73579104757361,0.023960250300758,0.000871055794803,0.029513654379003},
				{41.92856606395959,0.02385008823041,0.000798682785363,0.028260976369594},
				{42.88280239507019,0.023319371499726,0.000772006039558,0.027784996662907},
				{42.12134108034557,0.023740934508531,0.00074645925429,0.027321406521069},
				{42.31411609673155,0.023632775353595,0.000712537684003,0.026693401506792},
				{42.69966612950351,0.023419386862818,0.000712120722422,0.026685590164387},
				{42.50689111311753,0.023525597234078,0.000699239209942,0.026443131621306}
		};

		private List<ValidObservation> obs;
		
		public DftTest(String name) {
			obs = new ArrayList<ValidObservation>();
			for (double[] jdAndMag : jdAndMagPairs) {
				ValidObservation ob = new ValidObservation();
				ob.setDateInfo(new DateInfo(jdAndMag[0]));
				ob.setMagnitude(new Magnitude(jdAndMag[1], 0));
				obs.add(ob);
			}
		}

		private static void dftTest(DFTandSpectralWindow.DFTandSpectralWindowAlgorithm algorithm, 
				double[][] expectedDftResult, double[][] expectedDftTopHits)
				throws Exception {
			
			algorithm.execute();
			
			Map<PeriodAnalysisCoordinateType, List<Double>> series = algorithm.getResultSeries();
			Map<PeriodAnalysisCoordinateType, List<Double>> topHits = algorithm.getTopHits();
			
			compareDftResult(expectedDftResult, series, "DFT ");
			
			if (expectedDftTopHits.length != topHits.get(PeriodAnalysisCoordinateType.FREQUENCY).size())
				throw new Exception("DFT TopHits array length mismatch");
			compareDftResult(expectedDftTopHits, topHits, "TopHits ");

		}

		private static void spwTest(DFTandSpectralWindow.DFTandSpectralWindowAlgorithm algorithm, 
				double[][] expectedDftResult, double[][] expectedDftTopHits)
				throws Exception {
			
			algorithm.execute();
			
			//Map<PeriodAnalysisCoordinateType, List<Double>> series = algorithm.getResultSeries();
			Map<PeriodAnalysisCoordinateType, List<Double>> topHits = algorithm.getTopHits();
			
			// not yet implemented. At this point
			//compareDftResult(expectedDftResult, series, "DFT ");
			
			if (expectedDftTopHits.length != topHits.get(PeriodAnalysisCoordinateType.FREQUENCY).size())
				throw new Exception("DFT TopHits array length mismatch");
			compareDftResult(expectedDftTopHits, topHits, "TopHits ");

		}
		
		private static void compareDftResult(double[][] expected, Map<PeriodAnalysisCoordinateType, List<Double>> result, String prefix) throws Exception {
			int i = 0;
			for (double[] d : expected) {
				String fmtStrFreq = "%1.9f";
				String fmtStrPeriod = "%1.9f";
				String fmtStrPower = "%1.9f";
				String fmtStrSemiAmp = "%1.9f";
				double frequency = result.get(PeriodAnalysisCoordinateType.FREQUENCY).get(i);
				double period = result.get(PeriodAnalysisCoordinateType.PERIOD).get(i);
				double power = result.get(PeriodAnalysisCoordinateType.POWER).get(i);
				double semiamp = result.get(PeriodAnalysisCoordinateType.SEMI_AMPLITUDE).get(i);
				compareDouble(frequency, d[0], fmtStrFreq, i + 1, 
						prefix + "Frequency comparison failed: expected " + fmtStrFreq + "; got " + fmtStrFreq + "; obs.#%d");
				compareDouble(period, d[1], fmtStrPeriod, i + 1, 
						prefix + "Period comparison failed: expected " + fmtStrPeriod + "; got " + fmtStrPeriod + "; obs.#%d");
				compareDouble(power, d[2], fmtStrPower, i + 1,
						prefix + "Power comparison failed: " + fmtStrPower + "; got " + fmtStrPower + "; obs.#%d");
				compareDouble(semiamp, d[3], fmtStrSemiAmp, i + 1, 
						prefix + "Semiamplitude comparison failed: " + fmtStrSemiAmp + "; got " + fmtStrSemiAmp + "; obs.#%d");
				i++;
			}
		}
		
		private static void compareDouble(double a, double b, String fmtStr, int obsN, String errorMessage) throws Exception {
			if (!String.format(fmtStr, a).equals(String.format(fmtStr, b))) {
				//System.out.print(Double.toString(a) + ";" + Double.toString(b) + "; ");
				//System.out.println(String.format(errorMessage, a, b, obsN));
				throw new Exception(String.format(errorMessage, a, b, obsN));
			}
		}
		
		public void testDcDft() throws Exception {
			DFTandSpectralWindow.FtResult ftResult = new DFTandSpectralWindow.FtResult(obs);
			
			ftResult.setAnalysisType(DFTandSpectralWindow.FAnalysisType.DFT);
			DFTandSpectralWindow.DFTandSpectralWindowAlgorithm dft_algorithm = 
					new DFTandSpectralWindow.DFTandSpectralWindowAlgorithm(obs, 0.0, 50.0, 0.009638750819301, ftResult);  
			dftTest(dft_algorithm, expectedDftResult, expectedDftTopHits);
			
			ftResult.setAnalysisType(DFTandSpectralWindow.FAnalysisType.SPW);
			DFTandSpectralWindow.DFTandSpectralWindowAlgorithm spw_algorithm = 
					new DFTandSpectralWindow.DFTandSpectralWindowAlgorithm(obs, 0.0, 50.0, 0.009638750819301, ftResult);  
			spwTest(spw_algorithm, null, expectedSpwTopHits);
		}
		
	}
	
}
