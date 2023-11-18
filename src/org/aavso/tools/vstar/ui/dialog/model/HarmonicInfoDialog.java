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
package org.aavso.tools.vstar.ui.dialog.model;

import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.aavso.tools.vstar.ui.dialog.period.PeriodAnalysis2DChartPane;
import org.aavso.tools.vstar.ui.mediator.DocumentManager;
import org.aavso.tools.vstar.ui.mediator.Mediator;
import org.aavso.tools.vstar.ui.mediator.message.HarmonicSearchResultMessage;
import org.aavso.tools.vstar.util.model.Harmonic;
import org.aavso.tools.vstar.util.prefs.NumericPrecisionPrefs;

/**
 * This dialog shows harmonics found from a search for harmonics of some
 * frequency. When an entry is selected, the cross-hair of the corresponding
 * plot is moved to pin-point the frequency.
 */
@SuppressWarnings("serial")
public class HarmonicInfoDialog extends JDialog implements
		ListSelectionListener {

	private HarmonicSearchResultMessage msg;
	private PeriodAnalysis2DChartPane plotPane;

	private double startX, startY;

	private JList harmonicList;
	private DefaultListModel harmonicListModel;

	private Map<String, Harmonic> harmonicMap;

	/**
	 * Constructor.
	 * 
	 * @param msg
	 *            The harmonic search result message.
	 * @param plotPane
	 *            The corresponding plot pane to set the cross-hair on.
	 */
	public HarmonicInfoDialog(HarmonicSearchResultMessage msg,
			PeriodAnalysis2DChartPane plotPane) {
		super(DocumentManager.findActiveWindow());

		this.setTitle("Harmonics");
		this.setModal(true);

		this.msg = msg;
		this.plotPane = plotPane;

		startX = plotPane.getChart().getXYPlot().getDomainCrosshairValue();
		startY = plotPane.getChart().getXYPlot().getRangeCrosshairValue();

		this.harmonicMap = new TreeMap<String, Harmonic>();

		JPanel topPane = new JPanel();
		topPane.setLayout(new BoxLayout(topPane, BoxLayout.PAGE_AXIS));
		topPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		topPane.add(createListPane());
		topPane.add(createButtonPane());

		getContentPane().add(topPane);
		pack();
		
		this.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) {
				dismiss();
			}
		});
		
		setLocationRelativeTo(Mediator.getUI().getContentPane());
		setVisible(true);
	}

	private JPanel createListPane() {
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		harmonicListModel = new DefaultListModel();

		for (Harmonic harmonic : msg.getHarmonics()) {
			String label = "Frequency: " + harmonic.toString() + " ("
					+ harmonic.getHarmonicNumber() + "f), Period: "
					+ NumericPrecisionPrefs.formatOther(harmonic.getPeriod());
			harmonicListModel.addElement(label);
			harmonicMap.put(label, harmonic);
		}

		harmonicList = new JList(harmonicListModel);
		harmonicList
				.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		harmonicList.addListSelectionListener(this);
		JScrollPane modelListScroller = new JScrollPane(harmonicList);

		panel.add(modelListScroller);

		return panel;
	}

	private JPanel createButtonPane() {
		JPanel panel = new JPanel(new FlowLayout());

		JButton dismissButton = new JButton("Dismiss");
		dismissButton.addActionListener(createDismissButtonListener());
		dismissButton.setEnabled(true);
		panel.add(dismissButton);

		JButton copyButton = new JButton("Copy");
		copyButton.addActionListener(createCopyButtonListener());
		copyButton.setEnabled(true);
		panel.add(copyButton);
		
		this.getRootPane().setDefaultButton(dismissButton);

		return panel;
	}

	// List selection listener to update button states.
	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting() == false) {
			int index = harmonicList.getSelectedIndex();
			if (index != -1) {
				int selectedModelIndex = harmonicList.getSelectedIndex();
				String desc = (String) harmonicListModel
						.get(selectedModelIndex);
				Harmonic harmonic = harmonicMap.get(desc);
				double x = harmonic.getFrequency();
				double y = findNthChartRangeValueFromFrequency(x);
				// 'y' is not the exact value because the chart resolution is limited.
				plotPane.setCrossHair(x, y);								
			}
		}
	}

	// Return the range value corresponding to the specified frequency.
	private Double findNthChartRangeValueFromFrequency(double frequency) {
		Double value = null;

		List<Double> freqVals = plotPane.getModel().getDomainValues();
		List<Double> rangeVals = plotPane.getModel().getRangeValues();

		// We take points from the chart; the number of chart points (typically, 100)
		// is less than the number of calculated frequencies among which 
		// the harmonic had been searched (see PeriodAnalysisDialogBase.findHarmonics()).
		// So we cannot directly compare 'frequency' with chart values, and even
		// Tolerance() will not help much.
		// We assume, however, that the point of interest exists on the chart.
		// So, we simply try to find the closest chart point.
		
		int i = 0;
		Double minDif = null; 
		while (i < freqVals.size()) {
			double f = freqVals.get(i); // we assume get() returns non-null
			double dif = Math.abs(f - frequency);
			if (minDif != null) {
				if (dif < minDif) {
					minDif = dif;
					value = rangeVals.get(i);
				}
			} else {
				minDif = dif;
				value = rangeVals.get(i);
			}
			i++;
		}
		
		if (value == null) {
			throw new IllegalArgumentException("Unknown frequency");
		}

		return value;
	}

	private void dismiss() {
		setVisible(false);
		dispose();
		// Restore the plot's cross hair.
		plotPane.setCrossHair(startX, startY);
	}
	
	// Return a listener for the "Dismiss" button.
	private ActionListener createDismissButtonListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dismiss();
			}
		};
	}
	
	// Return a listener for the "Dismiss" button.
	private ActionListener createCopyButtonListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String s = "";
				for (int i = 0; i < harmonicListModel.size(); i++) {
					s += harmonicListModel.get(i).toString() + "\n";
				}
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				clipboard.setContents(new StringSelection(s) , null);
			}
		};
	}

}
