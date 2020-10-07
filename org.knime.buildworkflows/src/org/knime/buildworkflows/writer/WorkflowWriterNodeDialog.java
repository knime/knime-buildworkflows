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
 *   9 Dec 2019 (Marc Bux, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.buildworkflows.writer;

import java.awt.Component;
import java.awt.Dimension;
import java.util.Optional;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentButtonGroup;
import org.knime.core.node.defaultnodesettings.DialogComponentLabel;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.ButtonGroupEnumInterface;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.SettingsModelWriterFileChooser;
import org.knime.filehandling.core.defaultnodesettings.status.DefaultStatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusView;
import org.knime.filehandling.core.node.portobject.SelectionMode;
import org.knime.filehandling.core.node.portobject.writer.PortObjectWriterNodeDialog;

/**
 * Dialog for the workflow writer node.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowWriterNodeDialog extends PortObjectWriterNodeDialog<WorkflowWriterNodeConfig> {

    enum ExistsOption implements ButtonGroupEnumInterface {
            FAIL("Fail"), OVERWRITE("Overwrite");

        private final String m_name;

        private ExistsOption(final String name) {
            m_name = name;
        }

        @Override
        public String getText() {
            return m_name;
        }

        @Override
        public String getActionCommand() {
            return name();
        }

        @Override
        public String getToolTip() {
            return "";
        }

        @Override
        public boolean isDefault() {
            return this == EXISTS_OPTION_DEF;
        }
    }

    static final ExistsOption EXISTS_OPTION_DEF = ExistsOption.FAIL;

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

    static final SelectionMode SELECTION_MODE = SelectionMode.FOLDER;

    private final DialogComponentLabel m_originalName;

    private final DialogComponentBoolean m_useCustomName;

    private final DialogComponentString m_customName;

    private final StatusView m_workflowNameStatus = new StatusView(400);

    private final DialogComponentButtonGroup m_existsOption;

    private final DialogComponentIONodes m_ioNodes;

    private final int m_workflowInputPortIndex;

    private final JRadioButton m_writeButton;

    private final JRadioButton m_openButton;

    private final JRadioButton m_exportButton;

    // lazily initialized
    private ChangeListener m_workflowNameChangeListener;

    WorkflowWriterNodeDialog(final NodeCreationConfiguration creationConfig, final String fileChooserHistoryId) {
        super(new WorkflowWriterNodeConfig(creationConfig), fileChooserHistoryId, SELECTION_MODE);
        final WorkflowWriterNodeConfig config = getConfig();

        m_existsOption =
                new DialogComponentButtonGroup(config.getExistsOption(), "If exists", false, ExistsOption.values());
        m_originalName = new DialogComponentLabel(" ");
        m_useCustomName = new DialogComponentBoolean(config.isUseCustomName(), "Use custom workflow name");
        m_customName = new DialogComponentString(config.getCustomName(), "Custom workflow name: ", true, 30);
        m_customName.setToolTipText("Name of the workflow directory or file to be written");
        addAdditionalPanel(group("Workflow", m_existsOption.getComponentPanel(), m_originalName.getComponentPanel(),
            m_useCustomName.getComponentPanel(), m_customName.getComponentPanel(), m_workflowNameStatus.getLabel()));

        final ButtonGroup group = new ButtonGroup();

        m_writeButton = new JRadioButton("Write workflow");
        m_writeButton.setToolTipText("Write workflow and refresh KNIME Explorer");
        m_writeButton.setActionCommand("WRITE");
        group.add(m_writeButton);

        m_openButton = new JRadioButton("Write workflow and open in explorer");
        m_openButton.setToolTipText("Write workflow, refresh KNIME Explorer, and open the workflow after write.");
        m_openButton.setActionCommand("OPEN");
        group.add(m_openButton);

        m_exportButton = new JRadioButton("Export workflow as knwf archive");
        m_exportButton.setToolTipText("Export workflow as a workflow archive (.knwf)?");
        m_exportButton.setActionCommand("EXPORT");
        group.add(m_exportButton);

        addAdditionalPanel(group("Deployment Options", m_writeButton, m_openButton, m_exportButton));

        final SettingsModelWriterFileChooser fc = config.getFileChooserModel();
        final ChangeListener cl = e -> {
            if (Stream.of(FSCategory.RELATIVE, FSCategory.MOUNTPOINT)
                .noneMatch(c -> c == fc.getLocation().getFSCategory())) {
                if (m_openButton.isSelected()) {
                    m_writeButton.setSelected(true);
                }
                m_openButton.setEnabled(false);
            } else {
                m_openButton.setEnabled(true);
            }
        };
        fc.addChangeListener(cl);

        config.isUseCustomName()
            .addChangeListener(e -> config.getCustomName().setEnabled(config.isUseCustomName().getBooleanValue()));

        m_workflowInputPortIndex =
            creationConfig.getPortConfig().get().getInputPortLocation().get(getPortObjectInputGrpName())[0];
        m_ioNodes = new DialogComponentIONodes(getConfig().getIONodes(), m_workflowInputPortIndex);
        addTab("Inputs and outputs", m_ioNodes.getComponentPanel());
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        super.saveSettingsTo(settings);
        m_useCustomName.saveSettingsTo(settings);
        m_customName.saveSettingsTo(settings);
        settings.addBoolean(getConfig().isOpenAfterWrite().getConfigName(), m_openButton.isSelected());
        settings.addBoolean(getConfig().isArchive().getConfigName(), m_exportButton.isSelected());
        m_existsOption.saveSettingsTo(settings);
        m_ioNodes.saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        super.loadSettingsFrom(settings, specs);

        final WorkflowPortObjectSpec portObjectSpec = WorkflowWriterNodeModel
            .validateAndGetWorkflowPortObjectSpec(specs[m_workflowInputPortIndex], NotConfigurableException::new);
        final WorkflowWriterNodeConfig config = getConfig();

        m_originalName.setText(
            String.format("Default workflow name: %s", WorkflowWriterNodeModel.determineWorkflowName(portObjectSpec)));
        m_useCustomName.loadSettingsFrom(settings, specs);
        m_customName.loadSettingsFrom(settings, specs);
        m_writeButton.setSelected(true);
        m_openButton.setSelected(settings.getBoolean(getConfig().isOpenAfterWrite().getConfigName(), false));
        m_exportButton.setSelected(settings.getBoolean(getConfig().isArchive().getConfigName(), false));
        m_existsOption.loadSettingsFrom(settings, specs);
        m_ioNodes.loadSettingsFrom(settings, specs);

        config.getCustomName().setEnabled(config.isUseCustomName().getBooleanValue());
        updateWorkflowNameStatus(portObjectSpec);

        if (m_workflowNameChangeListener == null) {
            m_workflowNameChangeListener = e -> updateWorkflowNameStatus(portObjectSpec);
            m_useCustomName.getModel().addChangeListener(m_workflowNameChangeListener);
            m_customName.getModel().addChangeListener(m_workflowNameChangeListener);
        }
    }

    private void updateWorkflowNameStatus(final WorkflowPortObjectSpec portObjectSpec) {
        final Optional<String> err = WorkflowWriterNodeModel.validateWorkflowName(portObjectSpec,
            ((SettingsModelBoolean)m_useCustomName.getModel()).getBooleanValue(),
            ((SettingsModelString)m_customName.getModel()).getStringValue());
        if (err.isPresent()) {
            m_workflowNameStatus.setStatus(new DefaultStatusMessage(StatusMessage.MessageType.ERROR, err.get()));
        } else {
            m_workflowNameStatus.clearStatus();
        }
    }
}
