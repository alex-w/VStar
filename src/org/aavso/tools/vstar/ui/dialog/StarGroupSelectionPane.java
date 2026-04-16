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
package org.aavso.tools.vstar.ui.dialog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.aavso.tools.vstar.ui.resources.StarGroups;
import org.aavso.tools.vstar.util.locale.LocaleProps;

/**
 * This class represents a widget that permits a star group to be selected from
 * a pop-up list and a star in that group from another pop-up list.
 */
@SuppressWarnings("serial")
public class StarGroupSelectionPane extends JPanel {

    private final static String NO_STARS = "No stars";

    private JComboBox<String> starGroupSelector;
    private JComboBox<String> starSelector;
    private ActionListener starGroupSelectorListener;
    private ActionListener starSelectorListener;

    private StarGroups starGroups;

    // Selected star group, name and AUID.
    private String selectedStarGroup;
    private String selectedStarName;
    private String selectedAUID;

    private boolean clearStarField;
    private JTextField starField;

    /**
     * Constructor
     * 
     * @param starField An optional star field to be cleared when a group star is
     *                  selected.
     */
    public StarGroupSelectionPane(JTextField starField) {
        this(starField, true);
    }

    /**
     * Constructor
     * 
     * @param starField      An optional star field to be cleared or set when a
     *                       group star is selected.
     * @param clearStarField Whether to clear (true) or set (false) the star field.
     */
    public StarGroupSelectionPane(JTextField starField, boolean clearStarField) {
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.setBorder(BorderFactory.createEtchedBorder());

        selectedStarGroup = null;
        selectedStarName = null;

        selectedAUID = null;

        this.clearStarField = clearStarField;
        this.starField = starField;

        starGroups = StarGroups.getInstance();
        Set<String> starGroupMapKeys = starGroups.getGroupNames();

        starGroupSelector = new JComboBox<String>(starGroupMapKeys.toArray(new String[0]));
        selectedStarGroup = (String) starGroupSelector.getItemAt(0);
        starGroupSelector.setBorder(BorderFactory.createTitledBorder(LocaleProps.get("NEW_STAR_FROM_AID_DLG_GROUP")));
        starGroupSelectorListener = createStarGroupSelectorListener();
        starGroupSelector.addActionListener(starGroupSelectorListener);

        starSelector = new JComboBox<String>();
        starSelector.setBorder(BorderFactory.createTitledBorder(LocaleProps.get("NEW_STAR_FROM_AID_DLG_STAR")));
        starSelectorListener = createStarSelectorListener();
        populateStarListForSelectedGroup();
        starSelector.addActionListener(starSelectorListener);

        this.add(starGroupSelector);
        this.add(starSelector);
    }

    // Star group selector listener.
    private ActionListener createStarGroupSelectorListener() {
        return new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Populate the star selector list according
                // to the selected group.
                String group = (String) starGroupSelector.getSelectedItem();
                // Avoid clearing the star list / selectedStarName when the combo fires
                // with no selection (can happen around dialog show/layout); that would
                // wipe the last star and break persistence across invocations.
                if (group == null) {
                    return;
                }
                selectedStarGroup = group;
                populateStarListForSelectedGroup();
                updateStarFieldForSelection();
            }
        };
    }

    // Star selector listener.
    private ActionListener createStarSelectorListener() {
        return new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String starName = (String) starSelector.getSelectedItem();
                if (starName == null || NO_STARS.equals(starName) || selectedStarGroup == null) {
                    return;
                }
                selectedStarName = starName;
                selectedAUID = starGroups.getAUID(selectedStarGroup, selectedStarName);
                updateStarFieldForSelection();
            }
        };
    }

    private void withSuppressedStarSelectorListener(Runnable action) {
        boolean wasRegistered = removeAllStarSelectorListeners();
        try {
            action.run();
        } finally {
            if (wasRegistered) {
                starSelector.addActionListener(starSelectorListener);
            }
        }
    }

    private void withSuppressedGroupSelectorListener(Runnable action) {
        boolean wasRegistered = removeAllGroupSelectorListeners();
        try {
            action.run();
        } finally {
            if (wasRegistered) {
                starGroupSelector.addActionListener(starGroupSelectorListener);
            }
        }
    }

    private boolean removeAllStarSelectorListeners() {
        boolean removedAny = false;
        for (ActionListener listener : starSelector.getActionListeners()) {
            if (listener == starSelectorListener) {
                starSelector.removeActionListener(listener);
                removedAny = true;
            }
        }
        return removedAny;
    }

    private boolean removeAllGroupSelectorListeners() {
        boolean removedAny = false;
        for (ActionListener listener : starGroupSelector.getActionListeners()) {
            if (listener == starGroupSelectorListener) {
                starGroupSelector.removeActionListener(listener);
                removedAny = true;
            }
        }
        return removedAny;
    }

    private void syncSelectionFromUI() {
        Object group = starGroupSelector.getSelectedItem();
        if (group instanceof String && starGroups.doesGroupExist((String) group)) {
            selectedStarGroup = (String) group;
        }

        Object star = starSelector.getSelectedItem();
        if (star instanceof String && !NO_STARS.equals(star) && selectedStarGroup != null
                && starGroups.doesStarExistInGroup(selectedStarGroup, (String) star)) {
            selectedStarName = (String) star;
            selectedAUID = starGroups.getAUID(selectedStarGroup, selectedStarName);
        }
    }

    private void updateStarFieldForSelection() {
        if (starField == null) {
            return;
        }
        if (clearStarField) {
            starField.setText("");
        } else if (selectedStarName != null) {
            starField.setText(selectedStarName);
        }
    }

    /**
     * Populate the star list combo-box given the currently selected star group.
     * If the previously selected star is still in this group (e.g. after
     * {@link #refreshGroups()} or prefs-driven star-group updates),
     * keep that selection instead of resetting to the first list entry.
     */
    public void populateStarListForSelectedGroup() {
        withSuppressedStarSelectorListener(() -> {
            starSelector.removeAllItems();

            if (selectedStarGroup != null && !starGroups.getStarNamesInGroup(selectedStarGroup).isEmpty()) {

                for (String starName : starGroups.getStarNamesInGroup(selectedStarGroup)) {
                    starSelector.addItem(starName);
                }

                // Prefer the model's string instance so setSelectedIndex/Item matches reliably.
                String nameToSelect = findStarNameToSelect(selectedStarGroup, selectedStarName);
                if (nameToSelect == null && starSelector.getItemCount() > 0) {
                    nameToSelect = (String) starSelector.getItemAt(0);
                }
                if (nameToSelect != null) {
                    int idx = indexOfStarItem(nameToSelect);
                    if (idx >= 0) {
                        starSelector.setSelectedIndex(idx);
                        nameToSelect = (String) starSelector.getItemAt(idx);
                    } else {
                        starSelector.setSelectedIndex(0);
                        nameToSelect = (String) starSelector.getItemAt(0);
                    }
                }
                selectedStarName = nameToSelect;
                if (selectedStarName != null) {
                    selectedAUID = starGroups.getAUID(selectedStarGroup, selectedStarName);
                } else {
                    selectedAUID = null;
                }
            } else {
                starSelector.addItem(NO_STARS);
                selectedStarName = null;
                selectedAUID = null;
            }
        });
    }

    /**
     * Pick a star name to show: keep {@code desiredName} if it matches a star in the
     * group (exact or trim), else null so the caller can fall back to the first star.
     */
    private String findStarNameToSelect(String groupName, String desiredName) {
        if (desiredName == null || !starGroups.doesStarExistInGroup(groupName, desiredName)) {
            String trimmed = desiredName == null ? null : desiredName.trim();
            if (trimmed != null && starGroups.doesStarExistInGroup(groupName, trimmed)) {
                return trimmed;
            }
            for (String key : starGroups.getStarNamesInGroup(groupName)) {
                if (key != null && trimmed != null && key.equalsIgnoreCase(trimmed)) {
                    return key;
                }
            }
            return null;
        }
        return desiredName;
    }

    private int indexOfStarItem(String starName) {
        for (int i = 0; i < starSelector.getItemCount(); i++) {
            Object o = starSelector.getItemAt(i);
            if (starName.equals(o)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Add the specified group (to the map and visually) if it does not exist.
     * 
     * @param groupName The group to add.
     */
    public void addGroup(String groupName) {
        if (!starGroups.doesGroupExist(groupName)) {
            starGroups.addStarGroup(groupName);
            starGroupSelector.addItem(groupName);
            selectAndRefreshStarsInGroup(groupName);
        }
    }

    /**
     * Remove the specified group (from the map and visually) if it exists.
     * 
     * @param groupName The group to remove.
     */
    public void removeGroup(String groupName) {
        if (starGroups.doesGroupExist(groupName)) {
            if (MessageBox.showConfirmDialog("Remove Group", LocaleProps.get("REALLY_DELETE"))) {
                starGroups.removeStarGroup(groupName);
                starGroupSelector.removeItem(groupName);
                if (starGroupSelector.getItemCount() > 0) {
                    selectAndRefreshStarsInGroup((String) starGroupSelector.getItemAt(0));
                }
            }
        }
    }

    /**
     * Add the specified group-star-AUID triple.
     * 
     * @param groupName The group to add.
     * @param starName  The star to add to the specified group.
     * @param auid      The AUID of the star to be added.
     */
    public void addStar(String groupName, String starName, String auid) {
        if (starGroups.doesGroupExist(groupName)) {
            starGroups.addStar(groupName, starName, auid);
            selectAndRefreshStarsInGroup(groupName);
        }
    }

    /**
     * Remove the specified star in the specified group.
     * 
     * @param groupName The group to add.
     * @param starName
     */
    public void removeStar(String groupName, String starName) {
        if (starGroups.doesGroupExist(groupName)) {
            if (MessageBox.showConfirmDialog("Remove Star", LocaleProps.get("REALLY_DELETE"))) {
                starGroups.removeStar(groupName, starName);
                selectAndRefreshStarsInGroup(groupName);
            }
        }
    }

    /**
     * Clear the groups in the star group selector list.
     */
    public void resetGroups() {
        starGroups.resetGroupsToDefault();

        withSuppressedGroupSelectorListener(() -> {
            starGroupSelector.removeAllItems();

            for (String groupName : starGroups.getGroupNames()) {
                starGroupSelector.addItem(groupName);
            }

            selectAndRefreshStarsInGroup(starGroups.getDefaultStarListTitle());
        });
    }

    /**
     * Refresh the groups in the star group selector list. Only groups with stars
     * will be "refreshed".
     */
    public void refreshGroups() {
        syncSelectionFromUI();
        // Rebuilding the combo fires ActionListeners; without removing them first,
        // selectedStarGroup is overwritten (often to the first group) before we
        // can restore the user's last choice across dialog invocations.
        final String savedGroup = getSelectedStarGroupName();
        final String savedStar = getSelectedStarName();

        withSuppressedGroupSelectorListener(() -> {
            starGroupSelector.removeAllItems();

            for (String groupName : starGroups.getGroupNames()) {
                if (!starGroups.getStarNamesInGroup(groupName).isEmpty()) {
                    starGroupSelector.addItem(groupName);
                }
            }

            String groupToSelect = resolveGroupToSelect(savedGroup);

            selectedStarName = savedStar;

            if (groupToSelect != null) {
                starGroupSelector.setSelectedItem(groupToSelect);
                selectedStarGroup = groupToSelect;
                populateStarListForSelectedGroup();
            }
        });
    }

    private String resolveGroupToSelect(String savedGroup) {
        if (savedGroup != null && starGroups.doesGroupExist(savedGroup)
                && !starGroups.getStarNamesInGroup(savedGroup).isEmpty()
                && groupAppearsInCombo(savedGroup)) {
            return savedGroup;
        }

        String def = starGroups.getDefaultStarListTitle();
        if (groupAppearsInCombo(def)) {
            return def;
        }
        if (starGroupSelector.getItemCount() > 0) {
            return (String) starGroupSelector.getItemAt(0);
        }

        return null;
    }

    private boolean groupAppearsInCombo(String groupName) {
        if (groupName == null) {
            return false;
        }
        for (int i = 0; i < starGroupSelector.getItemCount(); i++) {
            if (groupName.equals(starGroupSelector.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Select the specified group and refresh its stars.
     * 
     * @param groupName The group to select.
     */
    public void selectAndRefreshStarsInGroup(String groupName) {
        if (starGroups.doesGroupExist(groupName)) {
            withSuppressedGroupSelectorListener(() -> {
                starGroupSelector.setSelectedItem(groupName);
                selectedStarGroup = groupName;
                populateStarListForSelectedGroup();
            });
        }
    }

    /**
     * @return the starGroups
     */
    public StarGroups getStarGroups() {
        return starGroups;
    }

    /**
     * @return the selectedStarGroup
     */
    public String getSelectedStarGroupName() {
        return selectedStarGroup;
    }

    /**
     * @return the selectedStarName
     */
    public String getSelectedStarName() {
        return selectedStarName;
    }

    /**
     * @return the selectedAUID
     */
    public String getSelectedAUID() {
        return selectedAUID;
    }
}
