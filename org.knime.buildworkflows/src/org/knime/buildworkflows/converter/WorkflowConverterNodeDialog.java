/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Feb 10, 2020 (hornm): created
 */
package org.knime.buildworkflows.converter;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;

/**
 * Workflow Converter-dialog.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowConverterNodeDialog extends NodeDialogPane {

    private final DialogComponentWorkflowChooser m_workflowChooser;

    private final WorkflowConverterNodeConfig m_config;

    private final DialogComponentBoolean m_indent;

    private final DialogComponentBoolean m_removeEmptyObjects;

    private final DialogComponentBoolean m_removeAnnotations;

    private final DialogComponentBoolean m_removeMetadata;

    private final DialogComponentBoolean m_removeUiInfo;

    private final DialogComponentBoolean m_removeBundle;

    private final DialogComponentBoolean m_removeFeature;

    WorkflowConverterNodeDialog(final WorkflowConverterNodeConfig config) {
        m_config = config;
        m_workflowChooser = new DialogComponentWorkflowChooser(config.getWorkflowChooserModel(), "workflow_reader",
            createFlowVariableModel(config.getWorkflowChooserModel().getKeysForFSLocation(),
                FSLocationVariableType.INSTANCE));
        m_indent = new DialogComponentBoolean(config.getIndent(), "Indent the output JSON");
        m_removeEmptyObjects = new DialogComponentBoolean(config.getRemoveEmptyValues(), "Remove empty JSON Objects");
        m_removeAnnotations = new DialogComponentBoolean(config.getRemoveAnnotations(), "Remove annotations");
        m_removeMetadata = new DialogComponentBoolean(config.getRemoveMetadata(), "Remove metadata");
        m_removeUiInfo = new DialogComponentBoolean(config.getRemoveUIInfo(), "Remove UI info");
        m_removeBundle = new DialogComponentBoolean(config.getRemoveBundle(), "Remove bundle");
        m_removeFeature = new DialogComponentBoolean(config.getRemoveFeature(), "Remove feature");

        JPanel p1 = createWorkflowChooserPanel();
        JPanel p2 = group("Formatting options", m_indent.getComponentPanel(), m_removeEmptyObjects.getComponentPanel(),
            m_removeAnnotations.getComponentPanel(), m_removeMetadata.getComponentPanel(),
            m_removeUiInfo.getComponentPanel(), m_removeBundle.getComponentPanel(), m_removeFeature.getComponentPanel());

        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = createAndInitGBC();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy++;
        panel.add(p1, gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        panel.add(p2, gbc);
        addTabAt(0, "Settings", panel);
        setSelected("Settings");
    }

    private JPanel createWorkflowChooserPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Workflow location"));
        final GridBagConstraints gbc = createAndInitGBC();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 1;
        panel.add(m_workflowChooser.getComponentPanel(), gbc);
        return panel;
    }

    private static final GridBagConstraints createAndInitGBC() {
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.weightx = 1;
        return gbc;
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_workflowChooser.saveSettingsTo(settings);
        m_indent.saveSettingsTo(settings);
        m_removeEmptyObjects.saveSettingsTo(settings);
        m_removeAnnotations.saveSettingsTo(settings);
        m_removeMetadata.saveSettingsTo(settings);
        m_removeUiInfo.saveSettingsTo(settings);
        m_removeFeature.saveSettingsTo(settings);
        m_removeBundle.saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        m_workflowChooser.loadSettingsFrom(settings, specs);
        m_indent.loadSettingsFrom(settings, specs);
        m_removeEmptyObjects.loadSettingsFrom(settings, specs);
        m_removeAnnotations.loadSettingsFrom(settings, specs);
        m_removeMetadata.loadSettingsFrom(settings, specs);
        m_removeUiInfo.loadSettingsFrom(settings, specs);
        m_removeBundle.loadSettingsFrom(settings, specs);
        m_removeFeature.loadSettingsFrom(settings, specs);
    }

    private static JPanel group(final String label, final Component... components) {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), label));
        for (final Component component : components) {
            panel.add(alignLeft(component));
        }
        return panel;
    }

    private static Component alignLeft(final Component component) {
        final Box box = Box.createHorizontalBox();
        component.setMaximumSize(new Dimension(component.getPreferredSize().width, component.getMaximumSize().height));
        box.add(component);
        box.add(Box.createHorizontalGlue());
        return box;
    }
}
