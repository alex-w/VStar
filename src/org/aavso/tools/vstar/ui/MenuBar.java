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
package org.aavso.tools.vstar.ui;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.aavso.tools.vstar.plugin.period.PeriodAnalysisPluginBase;
import org.aavso.tools.vstar.ui.dialog.AboutBox;
import org.aavso.tools.vstar.ui.dialog.FileExtensionFilter;
import org.aavso.tools.vstar.ui.dialog.HelpContentsDialog;
import org.aavso.tools.vstar.ui.dialog.InfoDialog;
import org.aavso.tools.vstar.ui.dialog.MessageBox;
import org.aavso.tools.vstar.ui.dialog.StarSelectorDialog;
import org.aavso.tools.vstar.ui.dialog.prefs.PreferencesDialog;
import org.aavso.tools.vstar.ui.mediator.AnalysisType;
import org.aavso.tools.vstar.ui.mediator.Mediator;
import org.aavso.tools.vstar.ui.mediator.message.AnalysisTypeChangeMessage;
import org.aavso.tools.vstar.ui.mediator.message.FilteredObservationMessage;
import org.aavso.tools.vstar.ui.mediator.message.NewStarMessage;
import org.aavso.tools.vstar.ui.mediator.message.ProgressInfo;
import org.aavso.tools.vstar.ui.mediator.message.ZoomRequestMessage;
import org.aavso.tools.vstar.ui.mediator.message.ZoomType;
import org.aavso.tools.vstar.ui.resources.PluginClassLoader;
import org.aavso.tools.vstar.util.notification.Listener;

/**
 * VStar's menu bar.
 */
public class MenuBar extends JMenuBar implements Listener<NewStarMessage> {

	// File menu item names.
	public static final String NEW_STAR_FROM_DATABASE = "New Star from AAVSO Database...";
	public static final String NEW_STAR_FROM_FILE = "New Star from File...";
	public static final String SAVE = "Save...";
	public static final String PRINT = "Print...";
	public static final String INFO = "Info...";
	public static final String PREFS = "Preferences...";
	public static final String QUIT = "Quit";

	// View menu item names.
	public static final String ZOOM_IN = "Zoom In";
	public static final String ZOOM_OUT = "Zoom Out";
	public static final String ZOOM_TO_FIT = "Zoom To Fit";
	public static final String FILTER = "Filter...";
	public static final String NO_FILTER = "No Filter";

	// Analysis menu item names.
	public static final String RAW_DATA = "Raw Data";
	public static final String PHASE_PLOT = "Phase Plot...";

	// Help menu item names.
	public static final String HELP_CONTENTS = "Help Contents...";
	public static final String ABOUT = "About...";

	private Mediator mediator = Mediator.getInstance();

	private JFileChooser fileOpenDialog;

	private Map<String, PeriodAnalysisPluginBase> menuItemNameToPeriodAnalysisPlugin;

	// The parent window.
	private MainFrame parent;

	// Menu items.

	// File menu.
	JMenuItem fileNewStarFromDatabaseItem;
	JMenuItem fileNewStarFromFileItem;
	JMenuItem fileSaveItem;
	JMenuItem filePrintItem;
	JMenuItem fileInfoItem;
	JMenuItem filePrefsItem;
	JMenuItem fileQuitItem;

	// View menu.
	JMenuItem viewZoomInItem;
	JMenuItem viewZoomOutItem;
	JMenuItem viewZoomToFitItem;
	JMenuItem viewFilterItem;
	JMenuItem viewNoFilterItem;

	// Analysis menu.
	JCheckBoxMenuItem analysisRawDataItem;
	JCheckBoxMenuItem analysisPhasePlotItem;

	JMenu analysisPeriodSearchMenu;
	JMenuItem analysisPeriodSearchItem;

	// Help menu.
	JMenuItem helpContentsItem;
	JMenuItem helpAboutItem;

	// New star message.
	private NewStarMessage newStarMessage;

	/**
	 * Constructor
	 */
	public MenuBar(MainFrame parent) {
		super();

		this.parent = parent;

		List<String> extensions = new ArrayList<String>();
		extensions.add("csv");
		extensions.add("tsv");
		extensions.add("txt");

		this.fileOpenDialog = new JFileChooser();
		this.fileOpenDialog.setFileFilter(new FileExtensionFilter(extensions));

		createFileMenu();
		createViewMenu();
		createAnalysisMenu();
		createHelpMenu();

		this.newStarMessage = null;

		// Listen to events

		this.mediator.getProgressNotifier().addListener(
				createProgressListener());

		this.mediator.getNewStarNotifier().addListener(this);

		this.mediator.getAnalysisTypeChangeNotifier().addListener(
				createAnalysisTypeChangeListener());
	}

	private void createFileMenu() {
		JMenu fileMenu = new JMenu("File");

		fileNewStarFromDatabaseItem = new JMenuItem(NEW_STAR_FROM_DATABASE);
		fileNewStarFromDatabaseItem
				.addActionListener(createNewStarFromDatabaseListener());
		fileMenu.add(fileNewStarFromDatabaseItem);

		fileNewStarFromFileItem = new JMenuItem(NEW_STAR_FROM_FILE);
		fileNewStarFromFileItem
				.addActionListener(createNewStarFromFileListener());
		fileMenu.add(fileNewStarFromFileItem);

		fileMenu.addSeparator();

		fileSaveItem = new JMenuItem(SAVE);
		fileSaveItem.addActionListener(this.createSaveListener());
		fileSaveItem.setEnabled(false);
		fileMenu.add(fileSaveItem);

		filePrintItem = new JMenuItem(PRINT);
		filePrintItem.addActionListener(this.createPrintListener());
		filePrintItem.setEnabled(false);
		fileMenu.add(filePrintItem);

		fileMenu.addSeparator();

		fileInfoItem = new JMenuItem(INFO);
		fileInfoItem.addActionListener(this.createInfoListener());
		fileMenu.add(fileInfoItem);

		fileMenu.addSeparator();

		filePrefsItem = new JMenuItem(PREFS);
		filePrefsItem.addActionListener(this.createPrefsListener());
		fileMenu.add(filePrefsItem);

		// Mac OS X applications don't have Quit item in File menu,
		// but in application (VStar) menu. See also VStar.java.
		String os_name = System.getProperty("os.name");
		if (!os_name.startsWith("Mac OS X")) {
			fileMenu.addSeparator();

			fileQuitItem = new JMenuItem(QUIT, KeyEvent.VK_Q);
			// fileQuitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
			// ActionEvent.META_MASK));
			fileQuitItem.addActionListener(createQuitListener());
			fileMenu.add(fileQuitItem);
		}

		this.add(fileMenu);
	}

	private void createViewMenu() {
		JMenu viewMenu = new JMenu("View");

		viewZoomInItem = new JMenuItem(ZOOM_IN);
		viewZoomInItem.setEnabled(false);
		viewZoomInItem.addActionListener(createZoomInListener());
		viewMenu.add(viewZoomInItem);

		viewZoomOutItem = new JMenuItem(ZOOM_OUT);
		viewZoomOutItem.setEnabled(false);
		viewZoomOutItem.addActionListener(createZoomOutListener());
		viewMenu.add(viewZoomOutItem);

		viewZoomToFitItem = new JMenuItem(ZOOM_TO_FIT);
		viewZoomToFitItem.setEnabled(false);
		viewZoomToFitItem.addActionListener(createZoomToFitListener());
		// viewMenu.add(viewZoomToFitItem);

		viewMenu.addSeparator();

		viewFilterItem = new JMenuItem(FILTER);
		viewFilterItem.setEnabled(false);
		viewFilterItem.addActionListener(createFilterListener());
		viewMenu.add(viewFilterItem);

		viewNoFilterItem = new JMenuItem(NO_FILTER);
		viewNoFilterItem.setEnabled(false);
		viewNoFilterItem.addActionListener(createShowAllListener());
		viewMenu.add(viewNoFilterItem);

		this.add(viewMenu);
	}

	private void createAnalysisMenu() {
		JMenu analysisMenu = new JMenu("Analysis");

		analysisRawDataItem = new JCheckBoxMenuItem(RAW_DATA);
		analysisRawDataItem.setEnabled(false);
		analysisRawDataItem.addActionListener(createRawDataListener());
		analysisMenu.add(analysisRawDataItem);

		analysisPhasePlotItem = new JCheckBoxMenuItem(PHASE_PLOT);
		analysisPhasePlotItem.setEnabled(false);
		analysisPhasePlotItem.addActionListener(createPhasePlotListener());
		analysisMenu.add(analysisPhasePlotItem);

		analysisMenu.addSeparator();

		analysisPeriodSearchMenu = new JMenu("Period Search");
		analysisPeriodSearchMenu.setEnabled(false);

		ActionListener periodSearchListener = createPeriodSearchListener();

		menuItemNameToPeriodAnalysisPlugin = new TreeMap<String, PeriodAnalysisPluginBase>();

		for (PeriodAnalysisPluginBase plugin : PluginClassLoader
				.getPeriodAnalysisPlugins()) {
			String itemName = plugin.getDisplayName() + "...";

			analysisPeriodSearchItem = new JMenuItem(itemName);
			analysisPeriodSearchItem.addActionListener(periodSearchListener);
			analysisPeriodSearchMenu.add(analysisPeriodSearchItem);

			menuItemNameToPeriodAnalysisPlugin.put(itemName, plugin);
		}

		analysisMenu.add(analysisPeriodSearchMenu);

		this.add(analysisMenu);
	}

	private void createHelpMenu() {
		JMenu helpMenu = new JMenu("Help");

		helpContentsItem = new JMenuItem(HELP_CONTENTS, KeyEvent.VK_H);
		helpContentsItem.addActionListener(createHelpContentsListener());
		helpMenu.add(helpContentsItem);

		helpMenu.addSeparator();

		helpAboutItem = new JMenuItem(ABOUT, KeyEvent.VK_A);
		helpAboutItem.addActionListener(createAboutListener());
		helpMenu.add(helpAboutItem);

		this.add(helpMenu);
	}

	// ** File Menu listeners **

	/**
	 * Returns the action listener to be invoked for File->New Star from AAVSO
	 * Database...
	 * 
	 * The action is to: a. ask the user for star and date range details; b.
	 * open a database connection and get the data for star in that range; c.
	 * create the corresponding observation models and GUI elements.
	 */
	public ActionListener createNewStarFromDatabaseListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					// Prompt user for star and JD range selection.
					MainFrame.getInstance().getStatusPane().setMessage(
							"Select a star...");
					StarSelectorDialog starSelectorDialog = StarSelectorDialog
							.getInstance();
					starSelectorDialog.showDialog();

					if (!starSelectorDialog.isCancelled()) {
						String starName = starSelectorDialog.getStarName();
						String auid = starSelectorDialog.getAuid();
						double minJD, maxJD;
						// TODO: consider doing these value mods in the dialog
						// getters
						// to make this code more declarative.
						if (!starSelectorDialog.wantAllData()) {
							minJD = starSelectorDialog.getMinDate()
									.getJulianDay();
							maxJD = starSelectorDialog.getMaxDate()
									.getJulianDay();
						} else {
							minJD = 0;
							maxJD = Double.MAX_VALUE;
						}

						mediator.createObservationArtefactsFromDatabase(
								starName, auid, minJD, maxJD);
					} else {
						MainFrame.getInstance().getStatusPane().setMessage("");
					}
				} catch (Exception ex) {
					completeProgress();
					MessageBox.showErrorDialog(MainFrame.getInstance(),
							"Star Selection", ex);
				}
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for File->New Star from File...
	 * 
	 * The action is to open a file dialog to allow the user to select a single
	 * file.
	 */
	public ActionListener createNewStarFromFileListener() {
		final JFileChooser fileOpenDialog = this.fileOpenDialog;
		final MainFrame parent = this.parent;

		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int returnVal = fileOpenDialog.showOpenDialog(parent);

				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File f = fileOpenDialog.getSelectedFile();

					try {
						mediator.createObservationArtefactsFromFile(f, parent);
					} catch (Exception ex) {
						completeProgress();
						MessageBox.showErrorDialog(parent, NEW_STAR_FROM_FILE,
								ex);
					}
				}
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for File->Save...
	 */
	public ActionListener createSaveListener() {
		final Component parent = this.parent;
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediator.saveCurrentMode(parent);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for File->Print...
	 */
	public ActionListener createPrintListener() {
		final Component parent = this.parent;
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediator.printCurrentMode(parent);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for File->Info...
	 */
	public ActionListener createInfoListener() {
		final MenuBar self = this;
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new InfoDialog(self.newStarMessage);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for File->Preferences...
	 */
	public ActionListener createPrefsListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				PreferencesDialog prefsDialog = PreferencesDialog.getInstance();
				prefsDialog.showDialog();
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for File->Quit
	 */
	private ActionListener createQuitListener() {
		return new ActionListener() {
			// TODO: do other cleanup, e.g. if file needs saving;
			// need a document model including undo for this;
			// defer to Mediator.
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		};
	}

	// ** View Menu listeners **

	/**
	 * Returns the action listener to be invoked for View->Zoom In
	 */
	public ActionListener createZoomInListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ZoomRequestMessage msg = new ZoomRequestMessage(this,
						ZoomType.ZOOM_IN);
				mediator.getZoomRequestNotifier().notifyListeners(msg);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for View->Zoom Out
	 */
	public ActionListener createZoomOutListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ZoomRequestMessage msg = new ZoomRequestMessage(this,
						ZoomType.ZOOM_OUT);
				mediator.getZoomRequestNotifier().notifyListeners(msg);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for View->Zoom To Fit
	 */
	public ActionListener createZoomToFitListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ZoomRequestMessage msg = new ZoomRequestMessage(this,
						ZoomType.ZOOM_TO_FIT);
				mediator.getZoomRequestNotifier().notifyListeners(msg);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for View->Filter...
	 */
	public ActionListener createFilterListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediator.getObsFilterDialog().showDialog();
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for View->Show All...
	 */
	public ActionListener createShowAllListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mediator.getFilteredObservationNotifier().notifyListeners(
						FilteredObservationMessage.NO_FILTER);
			}
		};
	}

	// ** Analysis Menu listeners **

	/**
	 * Returns the action listener to be invoked for Analysis->Raw Data
	 */
	public ActionListener createRawDataListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setRawDataAnalysisMenuItemState(true); // ensure selected
				mediator.changeAnalysisType(AnalysisType.RAW_DATA);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for Analysis->Phase Plot
	 */
	public ActionListener createPhasePlotListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setPhasePlotAnalysisMenuItemState(true); // ensure selected
				mediator.changeAnalysisType(AnalysisType.PHASE_PLOT);
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for Analysis->Period Search
	 */
	public ActionListener createPeriodSearchListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String item = e.getActionCommand();
				PeriodAnalysisPluginBase plugin = menuItemNameToPeriodAnalysisPlugin
						.get(item);
				Mediator.getInstance().createPeriodAnalysisDialog(plugin);
			}
		};
	}

	// ** Help Menu listeners **

	/**
	 * Returns the action listener to be invoked for Help->Help Contents...
	 */
	public ActionListener createHelpContentsListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				javax.swing.SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						HelpContentsDialog helpContentsDialog = new HelpContentsDialog();
						helpContentsDialog.pack();
						helpContentsDialog.setVisible(true);
					}
				});
			}
		};
	}

	/**
	 * Returns the action listener to be invoked for Help->About...
	 */
	private ActionListener createAboutListener() {
		return new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AboutBox.showAboutBox(parent);
			}
		};
	}

	/**
	 * Return a progress listener.
	 */
	private Listener<ProgressInfo> createProgressListener() {
		final MainFrame parent = this.parent;
		return new Listener<ProgressInfo>() {
			public void update(ProgressInfo info) {
				switch (info.getType()) {
				case MIN_PROGRESS:
					break;
				case MAX_PROGRESS:
					break;
				case START_PROGRESS:
					resetProgress(parent);
					break;
				case COMPLETE_PROGRESS:
					completeProgress();
					break;
				case CLEAR_PROGRESS:
					break;
				case INCREMENT_PROGRESS:
					break;
				}
			}

			public boolean canBeRemoved() {
				return false;
			}
		};
	}

	/**
	 * Return an analysis type change listener.
	 */
	private Listener<AnalysisTypeChangeMessage> createAnalysisTypeChangeListener() {
		return new Listener<AnalysisTypeChangeMessage>() {
			public void update(AnalysisTypeChangeMessage info) {
				switch (info.getAnalysisType()) {
				case RAW_DATA:
					setRawDataAnalysisMenuItemState(true);
					setPhasePlotAnalysisMenuItemState(false);
					break;
				case PHASE_PLOT:
					setRawDataAnalysisMenuItemState(false);
					setPhasePlotAnalysisMenuItemState(true);
					break;
				}
			}

			public boolean canBeRemoved() {
				return false;
			}
		};
	}

	private void resetProgress(MainFrame parent) {
		// TODO: why not set cursor in MainFrame or StatusPane?
		parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		changeKeyMenuItemEnableState(false);
	}

	private void completeProgress() {
		// TODO: why not set cursor in MainFrame or StatusPane?
		parent.setCursor(null); // turn off the wait cursor
		changeKeyMenuItemEnableState(true);
	}

	/**
	 * New star listener update method.
	 */
	public void update(NewStarMessage msg) {
		this.newStarMessage = msg;
	}

	/**
	 * @see org.aavso.tools.vstar.util.notification.Listener#canBeRemoved()
	 */
	public boolean canBeRemoved() {
		return false;
	}

	// Helper methods

	// Enables or disabled key menu items.
	private void changeKeyMenuItemEnableState(boolean state) {
		this.fileNewStarFromDatabaseItem.setEnabled(state);
		this.fileNewStarFromFileItem.setEnabled(state);
		this.fileSaveItem.setEnabled(state);
		this.filePrintItem.setEnabled(state);
		this.fileInfoItem.setEnabled(state);

		this.viewZoomInItem.setEnabled(state);
		this.viewZoomOutItem.setEnabled(state);
		this.viewZoomToFitItem.setEnabled(state);
		this.viewFilterItem.setEnabled(state);
		this.viewNoFilterItem.setEnabled(state);
		
		this.analysisRawDataItem.setEnabled(state);
		this.analysisPhasePlotItem.setEnabled(state);

		this.analysisPeriodSearchMenu.setEnabled(state);

		AnalysisType type = mediator.getAnalysisType();

		switch (type) {
		case RAW_DATA:
			setRawDataAnalysisMenuItemState(true);
			setPhasePlotAnalysisMenuItemState(false);
			break;
		case PHASE_PLOT:
			setRawDataAnalysisMenuItemState(false);
			setPhasePlotAnalysisMenuItemState(true);
			break;
		}
	}

	private void setRawDataAnalysisMenuItemState(boolean state) {
		this.analysisRawDataItem.setState(state);
	}

	private void setPhasePlotAnalysisMenuItemState(boolean state) {
		this.analysisPhasePlotItem.setState(state);
	}
}
