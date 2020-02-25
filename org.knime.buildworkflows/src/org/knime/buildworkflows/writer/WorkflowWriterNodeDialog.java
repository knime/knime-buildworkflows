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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentLabel;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.util.FileUtil;
import org.knime.filehandling.core.defaultnodesettings.FileSystemChoice.Choice;
import org.knime.filehandling.core.node.portobject.writer.PortObjectWriterNodeDialog;

/**
 * Dialog for the workflow writer node.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowWriterNodeDialog extends PortObjectWriterNodeDialog<WorkflowWriterNodeConfig> {

    private final DialogComponentLabel m_originalName;

    private final DialogComponentBoolean m_useCustomName;

    private final DialogComponentString m_customName;

    private final DialogComponentBoolean m_archive;

    private final DialogComponentBoolean m_openAfterWrite;

    private final DialogComponentIONodes m_ioNodes;

    WorkflowWriterNodeDialog(final PortsConfiguration portsConfig, final String fileChooserHistoryId) {
        super(portsConfig, new WorkflowWriterNodeConfig(), fileChooserHistoryId, JFileChooser.DIRECTORIES_ONLY);

        final GridBagConstraints gbc = createAndInitGBC();
        m_archive = new DialogComponentBoolean(getConfig().isArchive(), "Export workflow as knwf archive");
        m_archive.setToolTipText("If a workflow archive (.knwf) should be written instead of a directory");
        m_openAfterWrite = new DialogComponentBoolean(getConfig().isOpenAfterWrite(), "Open after write");
        final JPanel writeOptionsPanel = new JPanel(new GridBagLayout());
        writeOptionsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Write Options"));
        gbc.gridy++;
        writeOptionsPanel.add(m_archive.getComponentPanel(), gbc);
        gbc.gridy++;
        writeOptionsPanel.add(m_openAfterWrite.getComponentPanel(), gbc);
        addAdditionalPanel(writeOptionsPanel);

        gbc.gridy = 0;
        m_originalName = new DialogComponentLabel("");
        m_useCustomName = new DialogComponentBoolean(getConfig().isUseCustomName(), "Use custom workflow name");
        m_customName = new DialogComponentString(getConfig().getCustomName(), "Custom workflow name: ", true, 30);
        m_customName.setToolTipText("Name of the workflow directory or file to be written");
        final JPanel customNamePanel = new JPanel(new GridBagLayout());
        customNamePanel
            .setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Workflow Name"));
        customNamePanel.add(m_originalName.getComponentPanel(), gbc);
        gbc.gridy++;
        customNamePanel.add(m_useCustomName.getComponentPanel(), gbc);
        gbc.gridy++;
        customNamePanel.add(m_customName.getComponentPanel(), gbc);
        addAdditionalPanel(customNamePanel);


        m_ioNodes = new DialogComponentIONodes(getConfig().getIONodes(), 0);
        addTab("Inputs & Outputs", m_ioNodes.getComponentPanel());
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        super.saveSettingsTo(settings);
        m_useCustomName.saveSettingsTo(settings);
        m_customName.saveSettingsTo(settings);
        m_archive.saveSettingsTo(settings);
        m_openAfterWrite.saveSettingsTo(settings);
        m_ioNodes.saveSettingsTo(settings);

        final WorkflowWriterNodeConfig config = getConfig();
        if (config.isUseCustomName().getBooleanValue()) {
            final String customName = config.getCustomName().getStringValue();
            if (customName.length() == 0) {
                throw new InvalidSettingsException("Custom workflow name must not be empty.");
            }
            if (FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(customName).find()) {
                throw new InvalidSettingsException(String.format("Custom workflow name must not contain any control characters or any of the characters %s.",
                    FileUtil.ILLEGAL_FILENAME_CHARS));
            }
        }
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        super.loadSettingsFrom(settings, specs);
        m_useCustomName.loadSettingsFrom(settings, specs);
        m_customName.loadSettingsFrom(settings, specs);
        m_archive.loadSettingsFrom(settings, specs);
        m_openAfterWrite.loadSettingsFrom(settings, specs);
        m_ioNodes.loadSettingsFrom(settings, specs);

        final WorkflowPortObjectSpec portObject = (WorkflowPortObjectSpec)specs[getPortObjectIndex()];
        final String workflowName = portObject.getWorkflowName();
        final String escapedName = FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(workflowName).replaceAll("_");
        final WorkflowWriterNodeConfig config = getConfig();
        final SettingsModelString customName = config.getCustomName();
        if (customName.getStringValue().isEmpty()) {
            customName.setStringValue(escapedName);
        }
        m_originalName.setText(String.format("Default workflow name: %s", workflowName));

        final boolean isCustomURL =
            config.getFileChooserModel().getFileSystemChoice().getType() == Choice.CUSTOM_URL_FS;
        final SettingsModelBoolean archive = config.isArchive();
        config.getOverwriteModel().setEnabled(!isCustomURL);
        config.getCreateDirectoryModel().setEnabled(!isCustomURL);
        config.getTimeoutModel().setEnabled(isCustomURL);
        archive.setEnabled(!isCustomURL);
        config.isOpenAfterWrite().setEnabled(!archive.getBooleanValue());
        customName.setEnabled(config.isUseCustomName().getBooleanValue());
    }

}
