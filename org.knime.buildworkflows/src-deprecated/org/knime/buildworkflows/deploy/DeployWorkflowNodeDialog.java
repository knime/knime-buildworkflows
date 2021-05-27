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
import java.awt.Dimension;
import java.net.URI;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformation;
import org.knime.base.filehandling.remote.dialog.RemoteFileChooser;
import org.knime.base.filehandling.remote.dialog.RemoteFileChooserPanel;
import org.knime.buildworkflows.ExistsOption;
import org.knime.buildworkflows.writer.DialogComponentIONodes;
import org.knime.buildworkflows.writer.SettingsModelIONodes;
import org.knime.buildworkflows.writer.WorkflowWriterNodeModel;
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
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.filehandling.core.defaultnodesettings.status.DefaultStatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusView;

/**
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
@Deprecated
final class DeployWorkflowNodeDialog extends NodeDialogPane {

    private static Component group(final String label, final Component... components) {
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

    static final String REST_ENDPOINT = "/knime/rest";

    private static final String REST_VERSION = "/v4/repository";

    private static final String WORKFLOW_GRP_PREFIX = REST_ENDPOINT + REST_VERSION;

    static final String PATH_SEPARATOR = "/";

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
        return new SettingsModelString("exists", ExistsOption.getDefault().getActionCommand());
    }

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

    private final StatusView m_workflowGrpStatus = new StatusView(400);

    private final DialogComponentBoolean m_createParent =
        new DialogComponentBoolean(createCreateParentModel(), "Create missing folders");

    private final DialogComponentLabel m_originalName = new DialogComponentLabel(" ");

    private final DialogComponentBoolean m_useCustomName =
        new DialogComponentBoolean(createUseCustomNameModel(), "Use custom workflow name");

    private final DialogComponentString m_customName =
        new DialogComponentString(createCustomNameModel(), "Custom workflow name: ", true, 20);

    private final StatusView m_workflowNameStatus = new StatusView(400);

    private final DialogComponentButtonGroup m_existsOption =
        new DialogComponentButtonGroup(createExistsOptionModel(), "If exists", false, ExistsOption.values());

    private final DialogComponentBoolean m_createSnapshot =
        new DialogComponentBoolean(createCreateSnapshotModel(), "Create Snapshot");

    private final DialogComponentString m_snapshotMessage =
        new DialogComponentString(createSnapshotMessageModel(), "Snapshot comment: ", false, 30);

    private final DialogComponentIONodes m_ioNodes = new DialogComponentIONodes(createIONodesModel(), 1);

    // lazily initialized
    private ChangeListener m_workflowNameChangeListener;

    DeployWorkflowNodeDialog() {
        m_workflowGrp.getPanel().setMaximumSize(new Dimension(m_workflowGrp.getPanel().getMaximumSize().width,
            m_workflowGrp.getPanel().getPreferredSize().height));
        m_customName.getComponentPanel().setToolTipText("Name of the workflow directory or file to be written");

        m_useCustomName.getModel().addChangeListener(e -> enableDisableCustomName());
        m_createSnapshot.getModel().addChangeListener(e -> enableDisableSnapshotMessage());
        @SuppressWarnings("unchecked")
        final JComboBox<String> workflowGrp = (JComboBox<String>)(m_workflowGrp.getPanel().getComponent(0));

        workflowGrp.addActionListener(e -> {
            final String s = (String)workflowGrp.getSelectedItem();
            if (s != null) {
                workflowGrp.setSelectedItem(s.replace(WORKFLOW_GRP_PREFIX, ""));
            }
            updateWorkflowGrpStatus();
        });

        final Component editor = workflowGrp.getEditor().getEditorComponent();
        if (editor instanceof JTextComponent) {
            ((JTextComponent)editor).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void changedUpdate(final DocumentEvent e) {
                    updateWorkflowGrpStatus();
                }

                @Override
                public void insertUpdate(final DocumentEvent e) {
                    updateWorkflowGrpStatus();
                }

                @Override
                public void removeUpdate(final DocumentEvent e) {
                    updateWorkflowGrpStatus();
                }
            });
        }

        final Box optionsTab = Box.createVerticalBox();
        optionsTab.add(Box.createVerticalStrut(20));
        final Box folderBox = Box.createHorizontalBox();
        folderBox.add(new JLabel("Folder: "));
        folderBox.add(m_workflowGrp.getPanel());
        optionsTab.add(group("Choose folder on KNIME Server", m_info.getComponentPanel(), folderBox,
            m_createParent.getComponentPanel(), m_workflowGrpStatus.getStatusPanel()));
        optionsTab.add(Box.createVerticalStrut(20));
        optionsTab.add(group("Workflow", m_existsOption.getComponentPanel(), m_originalName.getComponentPanel(),
            m_useCustomName.getComponentPanel(), m_customName.getComponentPanel(), m_workflowNameStatus.getStatusPanel()));
        optionsTab.add(Box.createVerticalStrut(20));
        optionsTab.add(
            group("Deployment options", m_createSnapshot.getComponentPanel(), m_snapshotMessage.getComponentPanel()));
        addTab("Settings", optionsTab);

        addTab("Inputs and outputs", m_ioNodes.getComponentPanel());
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        settings.addString(WORKFLOW_GRP_CFG, m_workflowGrp.getSelection());
        m_createParent.saveSettingsTo(settings);
        m_useCustomName.saveSettingsTo(settings);
        m_customName.saveSettingsTo(settings);
        m_existsOption.saveSettingsTo(settings);
        m_createSnapshot.saveSettingsTo(settings);
        m_snapshotMessage.saveSettingsTo(settings);
        m_ioNodes.saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {

        final ConnectionInformation conInf =
            DeployWorkflowNodeModel.validateAndGetConnectionInformation(specs[0], NotConfigurableException::new);
        final WorkflowPortObjectSpec portObjectSpec =
            WorkflowWriterNodeModel.validateAndGetWorkflowPortObjectSpec(specs[1], NotConfigurableException::new);

        final URI uri = conInf.toURI();
        m_info.setText(uri.getScheme() + "://" + uri.getAuthority());
        m_workflowGrp.setConnectionInformation(conInf);
        m_workflowGrp.setSelection(settings.getString(WORKFLOW_GRP_CFG, PATH_SEPARATOR));
        m_createParent.loadSettingsFrom(settings, specs);
        m_originalName.setText(
            String.format("Default workflow name: %s", WorkflowWriterNodeModel.determineWorkflowName(portObjectSpec)));
        m_useCustomName.loadSettingsFrom(settings, specs);
        m_customName.loadSettingsFrom(settings, specs);
        m_existsOption.loadSettingsFrom(settings, specs);
        m_createSnapshot.loadSettingsFrom(settings, specs);
        m_snapshotMessage.loadSettingsFrom(settings, specs);
        m_ioNodes.loadSettingsFrom(settings, specs);

        enableDisableCustomName();
        enableDisableSnapshotMessage();
        updateWorkflowGrpStatus();
        updateWorkflowNameStatus(portObjectSpec);

        if (m_workflowNameChangeListener == null) {
            m_workflowNameChangeListener = e -> updateWorkflowNameStatus(portObjectSpec);
            m_useCustomName.getModel().addChangeListener(m_workflowNameChangeListener);
            m_customName.getModel().addChangeListener(m_workflowNameChangeListener);
        }
    }

    private void enableDisableCustomName() {
        m_customName.getModel().setEnabled(((SettingsModelBoolean)m_useCustomName.getModel()).getBooleanValue());
    }

    private void enableDisableSnapshotMessage() {
        m_snapshotMessage.getModel().setEnabled(((SettingsModelBoolean)m_createSnapshot.getModel()).getBooleanValue());
    }

    private void updateWorkflowGrpStatus() {
        final Optional<String> err = DeployWorkflowNodeModel.validateWorkflowGrp(m_workflowGrp.getSelection());
        if (err.isPresent()) {
            m_workflowGrpStatus.setStatus(new DefaultStatusMessage(StatusMessage.MessageType.ERROR, err.get()));
        } else {
            m_workflowGrpStatus.clearStatus();
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
