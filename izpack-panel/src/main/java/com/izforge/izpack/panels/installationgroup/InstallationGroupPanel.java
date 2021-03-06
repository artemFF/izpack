/*
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izforge.izpack.panels.installationgroup;

import com.izforge.izpack.api.adaptator.IXMLElement;
import com.izforge.izpack.api.data.Pack;
import com.izforge.izpack.api.data.ResourceManager;
import com.izforge.izpack.api.handler.AbstractUIHandler;
import com.izforge.izpack.installer.base.InstallerFrame;
import com.izforge.izpack.installer.base.IzPanel;
import com.izforge.izpack.installer.data.GUIInstallData;
import com.izforge.izpack.util.Debug;
import com.izforge.izpack.util.OsConstraintHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.List;


/**
 * A panel which displays the available installGroups found on the packs to
 * allow the user to select a subset of the packs based on the pack
 * installGroups attribute. This panel will be skipped if there are no
 * pack elements with an installGroups attribute.
 *
 * @author Scott.Stark@jboss.org
 * @version $Revision: 1.1.1.1 $
 */
public class InstallationGroupPanel extends IzPanel
        implements ListSelectionListener
{
    private static final long serialVersionUID = 1L;

    /**
     * HashMap<String, Pack> of the GUIInstallData.availablePacks
     */
    private HashMap<String, Pack> packsByName;
    private TableModel groupTableModel;
    private JTextPane descriptionField;
    private JScrollPane groupScrollPane;
    private JTable groupsTable;
    private GroupData[] rows;
    private int selectedGroup = -1;

    public InstallationGroupPanel(InstallerFrame parent, GUIInstallData idata, ResourceManager resourceManager)
    {
        super(parent, idata, resourceManager);
        buildLayout();
    }

    /**
     * If there are no packs with an installGroups attribute, this panel is
     * skipped. Otherwise, the unique installGroups are displayed in a table.
     */
    public void panelActivate()
    {
        // Set/restore availablePacks from allPacks; consider OS constraints
        this.installData.setAvailablePacks(new ArrayList<Pack>());
        for (Pack pack : this.installData.getAllPacks())
        {
            if (OsConstraintHelper.oneMatchesCurrentSystem(pack.osConstraints))
            {
                this.installData.getAvailablePacks().add(pack);
            }
        }

        Debug.trace("InstallationGroupPanel.panelActivate, selectedGroup=" + selectedGroup);
        // If there are no groups, skip this panel
        Map<String, GroupData> installGroups = getInstallGroups(this.installData);
        if (installGroups.size() == 0)
        {
            super.askQuestion("Skip InstallGroup selection",
                    "Skip InstallGroup selection", AbstractUIHandler.CHOICES_YES_NO);
            parent.skipPanel();
            return;
        }

        // Build the table model from the unique groups
        groupTableModel = getModel(installGroups);
        groupsTable.setModel(groupTableModel);
        TableColumnModel columnModel = groupsTable.getColumnModel();

        // renders the radio buttons and adjusts their state
        TableCellRenderer radioButtonRenderer = new TableCellRenderer()
        {
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column)
            {
                if (value == null)
                {
                    return null;
                }

                int selectedRow = table.getSelectedRow();

                if (selectedRow != -1)
                {
                    JRadioButton selectedButton = (JRadioButton) table.getValueAt(selectedRow, 0);
                    if (!selectedButton.isSelected())
                    {
                        selectedButton.doClick();
                    }
                }

                JRadioButton button = (JRadioButton) value;
                button.setForeground(isSelected ?
                        table.getSelectionForeground() : table.getForeground());
                button.setBackground(isSelected ?
                        table.getSelectionBackground() : table.getBackground());

                // long millis = System.currentTimeMillis() % 100000;
                // System.out.printf("%1$5d: row: %2$d; isSelected: %3$5b; buttonSelected: %4$5b; selectedRow: %5$d%n", millis, row, isSelected, button.isSelected(), selectedRow);

                return button;
            }
        };
        columnModel.getColumn(0).setCellRenderer(radioButtonRenderer);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.RIGHT);
        columnModel.getColumn(1).setCellRenderer(renderer);

        //groupsTable.setColumnSelectionAllowed(false);
        //groupsTable.setRowSelectionAllowed(true);
        groupsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupsTable.getSelectionModel().addListSelectionListener(this);
        groupsTable.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        groupsTable.setIntercellSpacing(new Dimension(0, 0));
        groupsTable.setShowGrid(false);
        if (selectedGroup >= 0)
        {
            groupsTable.getSelectionModel().setSelectionInterval(selectedGroup, selectedGroup);
            descriptionField.setText(rows[selectedGroup].description);
        }
        else
        {
            descriptionField.setText(rows[0].description);
        }
    }

    /**
     * Remove all packs from the GUIInstallData availablePacks and selectedPacks
     * that do not list the selected installation group. Packs without any
     * installGroups are always included.
     */
    public void panelDeactivate()
    {

        Debug.trace("InstallationGroupPanel.panelDeactivate, selectedGroup=" + selectedGroup);
        if (selectedGroup >= 0)
        {
            removeUnusedPacks();
            GroupData group = this.rows[selectedGroup];
            this.installData.setVariable("INSTALL_GROUP", group.name);
            Debug.trace("Added variable INSTALL_GROUP=" + group.name);
        }
    }

    /**
     * There needs to be a valid selectedGroup to go to the next panel
     *
     * @return true if selectedGroup >= 0, false otherwise
     */
    public boolean isValidated()
    {
        Debug.trace("InstallationGroupPanel.isValidated, selectedGroup=" + selectedGroup);
        return selectedGroup >= 0;
    }

    /**
     * Update the current selected install group index.
     *
     * @param e
     */
    public void valueChanged(ListSelectionEvent e)
    {
        Debug.trace("valueChanged: " + e);
        if (!e.getValueIsAdjusting())
        {
            ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
            if (listSelectionModel.isSelectionEmpty())
            {
                descriptionField.setText("");
            }
            else
            {
                selectedGroup = listSelectionModel.getMinSelectionIndex();
                if (selectedGroup >= 0)
                {
                    GroupData data = rows[selectedGroup];
                    descriptionField.setText(data.description);
                    ((JRadioButton) groupTableModel.getValueAt(selectedGroup, 0)).setSelected(true);
                }
                Debug.trace("selectedGroup set to: " + selectedGroup);
            }
        }
    }

    /* Add the installation group to pack mappings
    * @see com.izforge.izpack.installer.IzPanel#makeXMLData(com.izforge.izpack.api.adaptator.IXMLElement)
    */

    public void makeXMLData(IXMLElement panelRoot)
    {
        InstallationGroupPanelAutomationHelper helper = new InstallationGroupPanelAutomationHelper();
        this.installData.setAttribute("GroupData", rows);
        this.installData.setAttribute("packsByName", packsByName);
        helper.makeXMLData(this.installData, panelRoot);
    }

    /**
     * Create the panel ui.
     */
    protected void buildLayout()
    {
        GridBagConstraints gridBagConstraints;

        descriptionField = new JTextPane();
        groupScrollPane = new JScrollPane();
        groupsTable = new JTable();

        setLayout(new GridBagLayout());

        descriptionField.setMargin(new Insets(2, 2, 2, 2));
        descriptionField.setAlignmentX(LEFT_ALIGNMENT);
        descriptionField.setCaretPosition(0);
        descriptionField.setEditable(false);
        descriptionField.setOpaque(false);
        descriptionField.setText("<b>Install group description text</b>");
        descriptionField.setContentType("text/html");
        descriptionField.setBorder(new TitledBorder(this.installData.getLangpack().getString("PacksPanel.description")));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.3;
        add(descriptionField, gridBagConstraints);

        groupScrollPane.setBorder(new EmptyBorder(1, 1, 1, 1));
        groupScrollPane.setViewportView(groupsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(groupScrollPane, gridBagConstraints);
    }

    protected void removeUnusedPacks()
    {
        GroupData data = rows[selectedGroup];
        Debug.trace("InstallationGroupPanel.removeUnusedPacks, GroupData=" + data.name);

        // Now remove the packs not in groupPackNames
        Iterator<Pack> iter = this.installData.getAvailablePacks().iterator();
        while (iter.hasNext())
        {
            Pack pack = iter.next();

            //reverse dependencies must be reset in case the user is going
            //back and forth between the group selection panel and the packs selection panel
            pack.revDependencies = null;

            if (!data.packNames.contains(pack.name))
            {
                iter.remove();
                Debug.trace("Removed AvailablePack: " + pack.name);
            }
        }

        this.installData.getSelectedPacks().clear();
        if (!"no".equals(this.installData.getVariable("InstallationGroupPanel.selectPacks")))
        {
            this.installData.getSelectedPacks().addAll(this.installData.getAvailablePacks());
        }
        else
        {
            for (Pack availablePack : this.installData.getAvailablePacks())
            {
                if (availablePack.preselected)
                {
                    this.installData.getSelectedPacks().add(availablePack);
                }
            }
        }
    }

    protected void addDependents(Pack p, HashMap<String, Pack> packsByName, GroupData data)
    {
        data.packNames.add(p.name);
        data.size += p.nbytes;
        Debug.trace("addDependents, added pack: " + p.name);
        if (p.dependencies == null || p.dependencies.size() == 0)
        {
            return;
        }

        Debug.trace(p.name + " dependencies: " + p.dependencies);
        for (String dependent : p.dependencies)
        {
            if (!data.packNames.contains(dependent))
            {
                Debug.trace("Need dependent: " + dependent);
                Pack dependentPack = packsByName.get(dependent);
                addDependents(dependentPack, packsByName, data);
            }
        }
    }

    /**
     * Build the set of unique installGroups installDataGUI. The GroupData description
     * is taken from the InstallationGroupPanel.description.[name] property
     * where [name] is the installGroup name. The GroupData size is built
     * from the Pack.nbytes sum.
     *
     * @param idata - the panel install installDataGUI
     * @return HashMap<String, GroupData> of unique install group names
     */
    protected HashMap<String, GroupData> getInstallGroups(GUIInstallData idata)
    {
        /* First create a packsByName<String, Pack> of all packs and identify
        the unique install group names.
        */
        packsByName = new HashMap<String, Pack>();
        HashMap<String, GroupData> installGroups = new HashMap<String, GroupData>();
        for (Pack pack : idata.getAvailablePacks())
        {
            packsByName.put(pack.name, pack);
            Set<String> groups = pack.installGroups;
            Debug.trace("Pack: " + pack.name + ", installGroups: " + groups);
            for (String group : groups)
            {
                GroupData data = installGroups.get(group);
                if (data == null)
                {
                    String description = getGroupDescription(group);
                    String sortKey = getGroupSortKey(group);
                    data = new GroupData(group, description, sortKey);
                    installGroups.put(group, data);
                }
            }
        }
        Debug.trace("Found installGroups: " + installGroups.keySet());

        /* Build up a set of the packs to include in the installation by finding
        all packs in the selected group, and then include their dependencies.
        */
        for (GroupData data : installGroups.values())
        {
            Debug.trace("Adding dependents for: " + data.name);
            for (Pack pack : idata.getAvailablePacks())
            {
                Set<String> groups = pack.installGroups;
                if (groups.size() == 0 || groups.contains(data.name))
                {
                    // The pack may have already been added while traversing dependencies
                    if (!data.packNames.contains(pack.name))
                    {
                        addDependents(pack, packsByName, data);
                    }
                }
            }
            Debug.trace("Completed dependents for: " + data);
            if (Debug.tracing())
            {
                Debug.trace(data);
            }
        }

        return installGroups;
    }

    /**
     * Look for a key = InstallationGroupPanel.description.[group] entry:
     * first using installData.langpack.getString(key+".html")
     * next using installData.langpack.getString(key)
     * next using installData.getVariable(key)
     * lastly, defaulting to group + " installation"
     *
     * @param group - the installation group name
     * @return the group description
     */
    protected String getGroupDescription(String group)
    {
        String description = null;
        String key = "InstallationGroupPanel.description." + group;
        if (this.installData.getLangpack() != null)
        {
            String htmlKey = key + ".html";
            String html = this.installData.getLangpack().getString(htmlKey);
            // This will equal the key if there is no entry
            if (htmlKey.equalsIgnoreCase(html))
            {
                description = this.installData.getLangpack().getString(key);
            }
            else
            {
                description = html;
            }
        }
        if (description == null || key.equalsIgnoreCase(description))
        {
            description = this.installData.getVariable(key);
        }
        if (description == null)
        {
            description = group + " installation";
        }
        try
        {
            description = URLDecoder.decode(description, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            emitWarning("Failed to convert description", e.getMessage());
        }

        return description;
    }

    /**
     * Look for a key = InstallationGroupPanel.sortKey.[group] entry:
     * by using installData.getVariable(key)
     * if this variable is not defined, defaults to group
     *
     * @param group - the installation group name
     * @return the group sortkey
     */
    protected String getGroupSortKey(String group)
    {
        String key = "InstallationGroupPanel.sortKey." + group;
        String sortKey = this.installData.getVariable(key);
        if (sortKey == null)
        {
            sortKey = group;
        }
        try
        {
            sortKey = URLDecoder.decode(sortKey, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            emitWarning("Failed to convert sortKey", e.getMessage());
        }

        return sortKey;
    }


    /**
     * Look for a key = InstallationGroupPanel.group.[group] entry:
     * first using installData.langpackgetString(key+".html")
     * next using installData.langpack.getString(key)
     * next using installData.getVariable(key)
     * lastly, defaulting to group
     *
     * @param group - the installation group name
     * @return the localized group name
     */
    protected String getLocalizedGroupName(String group)
    {
        String gname = null;
        String key = "InstallationGroupPanel.group." + group;
        if (this.installData.getLangpack() != null)
        {
            String htmlKey = key + ".html";
            String html = this.installData.getLangpack().getString(htmlKey);
            // This will equal the key if there is no entry
            if (htmlKey.equalsIgnoreCase(html))
            {
                gname = this.installData.getLangpack().getString(key);
            }
            else
            {
                gname = html;
            }
        }
        if (gname == null || key.equalsIgnoreCase(gname))
        {
            gname = this.installData.getVariable(key);
        }
        if (gname == null)
        {
            gname = group;
        }
        try
        {
            gname = URLDecoder.decode(gname, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            emitWarning("Failed to convert localized group name", e.getMessage());
        }

        return gname;
    }

    protected TableModel getModel(Map<String, GroupData> groupData)
    {
        String c1 = installData.getLangpack().getString("InstallationGroupPanel.colNameSelected");
        //String c2 = installData.getLangpack().getString("InstallationGroupPanel.colNameInstallType");
        String c3 = installData.getLangpack().getString("InstallationGroupPanel.colNameSize");
        String[] columns = {c1, c3};
        DefaultTableModel model = new DefaultTableModel(columns, 0)
        {
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }
        };
        rows = new GroupData[groupData.size()];
        // The name of the group to select if there is no current selection
        String defaultGroup = this.installData.getVariable("InstallationGroupPanel.defaultGroup");
        Debug.trace("InstallationGroupPanel.defaultGroup=" + defaultGroup + ", selectedGroup=" + selectedGroup);
        List<GroupData> values = new ArrayList<GroupData>(groupData.values());
        Collections.sort(values, new Comparator<GroupData>()
        {
            public int compare(GroupData g1, GroupData g2)
            {
                if (g1.sortKey == null || g2.sortKey == null)
                {
                    return 0;
                }

                return g1.sortKey.compareTo(g2.sortKey);
            }
        });

        ButtonGroup buttonGroup = new ButtonGroup();
        boolean madeSelection = false;
        int count = 0;
        for (GroupData gd : values)
        {
            rows[count] = gd;
            Debug.trace("Creating button#" + count + ", group=" + gd.name);
            JRadioButton button = new JRadioButton(getLocalizedGroupName(gd.name));
            if (selectedGroup == count)
            {
                button.setSelected(true);
                Debug.trace("Selected button#" + count);
            }
            else if (selectedGroup < 0 && !madeSelection)
            {
                if (defaultGroup != null)
                {
                    if (defaultGroup.equals(gd.name))
                    {
                        madeSelection = true;
                    }
                }
                else if (count == 0)
                {
                    madeSelection = true;
                }
                if (madeSelection)
                {
                    button.setSelected(true);
                    Debug.trace("Selected button#" + count);
                    selectedGroup = count;
                }
            }
            else
            {
                button.setSelected(false);
            }
            buttonGroup.add(button);
            String sizeText = gd.getSizeString();
            //Object[] installDataGUI = { button, gd.description, sizeText};
            Object[] data = {button, sizeText};
            model.addRow(data);
            count++;
        }
        return model;
    }

    protected static class GroupData
    {
        static final long ONEK = 1024;
        static final long ONEM = 1024 * 1024;
        static final long ONEG = 1024 * 1024 * 1024;

        String name;
        String description;
        String sortKey;
        long size;
        HashSet<String> packNames = new HashSet<String>();

        GroupData(String name, String description, String sortKey)
        {
            this.name = name;
            this.description = description;
            this.sortKey = sortKey;
        }

        String getSizeString()
        {
            String s;
            if (size < ONEK)
            {
                s = size + " bytes";
            }
            else if (size < ONEM)
            {
                s = size / ONEK + " KB";
            }
            else if (size < ONEG)
            {
                s = size / ONEM + " MB";
            }
            else
            {
                s = size / ONEG + " GB";
            }
            return s;
        }

        public String toString()
        {
            StringBuffer tmp = new StringBuffer("GroupData(");
            tmp.append(name);
            tmp.append("){description=");
            tmp.append(description);
            tmp.append(", sortKey=");
            tmp.append(sortKey);
            tmp.append(", size=");
            tmp.append(size);
            tmp.append(", sizeString=");
            tmp.append(getSizeString());
            tmp.append(", packNames=");
            tmp.append(packNames);
            tmp.append("}");
            return tmp.toString();
        }
    }

}
