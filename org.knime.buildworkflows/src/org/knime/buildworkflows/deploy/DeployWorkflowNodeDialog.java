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
 *   11 Jun 2020 ("Marc Bux, KNIME GmbH, Berlin, Germany"): created
 */
package org.knime.buildworkflows.deploy;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformation;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformationPortObjectSpec;
import org.knime.base.filehandling.remote.dialog.RemoteFileChooser;
import org.knime.base.filehandling.remote.dialog.RemoteFileChooserPanel;
import org.knime.buildworkflows.writer.DialogComponentIONodes;
import org.knime.buildworkflows.writer.SettingsModelIONodes;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentButtonGroup;
import org.knime.core.node.defaultnodesettings.DialogComponentLabel;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.ButtonGroupEnumInterface;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;

/**
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
final class DeployWorkflowNodeDialog extends NodeDialogPane {

    enum ExistsOption implements ButtonGroupEnumInterface {
            FAIL("Fail on execution"), OVERWRITE("Overwrite");

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

    static final String WORKFLOW_GRP_CFG = "workflow-group";

    static final SettingsModelBoolean createCreateParentModel() {
        return new SettingsModelBoolean("create-parent", true);
    }

    static final SettingsModelBoolean createUseCustomNameModel() {
        return new SettingsModelBoolean("use-custom-name", false);
    }

    static final SettingsModelString createCustomNameModel() {
        return new SettingsModelString("custom-name", "workflow");
    }

    static final SettingsModelString createExistsOptionModel() {
        return new SettingsModelString("exists", EXISTS_OPTION_DEF.getActionCommand());
    }

    private static final ExistsOption EXISTS_OPTION_DEF = ExistsOption.FAIL;

    static final SettingsModelBoolean createCreateSnapshotModel() {
        return new SettingsModelBoolean("create-snapshot", false);
    }

    static final SettingsModelString createSnapshotMessageModel() {
        return new SettingsModelString("snapshot-message", "");
    }

    static final SettingsModelIONodes createIONodesModel() {
        return new SettingsModelIONodes("io-nodes");
    }

    private final DialogComponentLabel m_info = new DialogComponentLabel(" ");

    private final RemoteFileChooserPanel m_workflowGrp =
        new RemoteFileChooserPanel(getPanel(), "Workflow Group", false, "deploymentTargetHistory",
            RemoteFileChooser.SELECT_DIR, createFlowVariableModel(WORKFLOW_GRP_CFG, StringType.INSTANCE), null);

    private final DialogComponentBoolean m_createParent =
        new DialogComponentBoolean(createCreateParentModel(), "Create workflow group if it does not exist");

    private final DialogComponentLabel m_originalName = new DialogComponentLabel(" ");

    private final DialogComponentBoolean m_useCustomName =
        new DialogComponentBoolean(createUseCustomNameModel(), "Use custom workflow name");

    private final DialogComponentString m_customName =
        new DialogComponentString(createCustomNameModel(), "Custom workflow name: ", true, 20);

    private final DialogComponentButtonGroup m_existsOption = new DialogComponentButtonGroup(createExistsOptionModel(),
        "If workflow already exists", false, ExistsOption.values());

    private final DialogComponentBoolean m_createSnapshot =
        new DialogComponentBoolean(createCreateSnapshotModel(), "Create Snapshot");

    private final DialogComponentString m_snapshotMessage =
        new DialogComponentString(createSnapshotMessageModel(), "Snapshot comment: ", false, 30);

    private final DialogComponentIONodes m_ioNodes = new DialogComponentIONodes(createIONodesModel(), 1);

    DeployWorkflowNodeDialog() {
        m_customName.getComponentPanel().setToolTipText("Name of the workflow directory or file to be written");

        m_useCustomName.getModel().addChangeListener(e -> m_customName.getModel()
            .setEnabled(((SettingsModelBoolean)m_useCustomName.getModel()).getBooleanValue()));
        m_createSnapshot.getModel().addChangeListener(e -> m_snapshotMessage.getModel()
            .setEnabled(((SettingsModelBoolean)m_createSnapshot.getModel()).getBooleanValue()));

        final Box optionsTab = Box.createVerticalBox();
        optionsTab.add(group("Workflow group on KNIME Server", m_info.getComponentPanel(), m_workflowGrp.getPanel(),
            m_createParent.getComponentPanel()));
        optionsTab.add(group("Workflow name", m_originalName.getComponentPanel(), m_useCustomName.getComponentPanel(),
            m_customName.getComponentPanel()));
        optionsTab.add(group("Deployment options", m_existsOption.getComponentPanel(),
            m_createSnapshot.getComponentPanel(), m_snapshotMessage.getComponentPanel()));
        addTab("Options", optionsTab);

        addTab("Inputs & Outputs", m_ioNodes.getComponentPanel());
    }

    private static Component group(final String label, final Component... components) {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), label));
        for (Component component : components) {
            panel.add(alignLeft(component));
        }
        return panel;
    }

    private static Component alignLeft(final Component component) {
        final Box box = Box.createHorizontalBox();
        component.setMaximumSize(component.getPreferredSize());
        box.add(component);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        settings.addString(WORKFLOW_GRP_CFG, m_workflowGrp.getSelection());
        m_createParent.saveSettingsTo(settings);
        m_useCustomName.saveSettingsTo(settings);
        m_customName.saveSettingsTo(settings);
        m_existsOption.saveSettingsTo(settings);
        m_snapshotMessage.saveSettingsTo(settings);
        m_ioNodes.saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {

        final ConnectionInformationPortObjectSpec con = (ConnectionInformationPortObjectSpec)specs[0];
        if (con == null) {
            throw new NotConfigurableException("No connection available.");
        }
        final ConnectionInformation conInf = con.getConnectionInformation();
        if (conInf == null) {
            throw new NotConfigurableException("No connection information available.");
        }

        final WorkflowPortObjectSpec portObjectSpec = (WorkflowPortObjectSpec)specs[1];
        if (portObjectSpec == null) {
            throw new NotConfigurableException("No workflow available.");
        }

        m_info.setText("Connection: " + conInf.toURI());
        m_workflowGrp.setConnectionInformation(conInf);
        m_workflowGrp.setSelection(settings.getString(WORKFLOW_GRP_CFG, DeployWorkflowNodeModel.WORKFLOW_GRP_PREFIX));
        m_createParent.loadSettingsFrom(settings, specs);
        m_originalName.setText(
            String.format("Default workflow name: %s", DeployWorkflowNodeModel.determineWorkflowName(portObjectSpec)));
        m_useCustomName.loadSettingsFrom(settings, specs);
        m_customName.loadSettingsFrom(settings, specs);
        m_existsOption.loadSettingsFrom(settings, specs);
        m_snapshotMessage.loadSettingsFrom(settings, specs);
        m_ioNodes.loadSettingsFrom(settings, specs);

        m_customName.getModel().setEnabled(((SettingsModelBoolean)m_useCustomName.getModel()).getBooleanValue());
        m_snapshotMessage.getModel().setEnabled(((SettingsModelBoolean)m_createSnapshot.getModel()).getBooleanValue());
    }
}
