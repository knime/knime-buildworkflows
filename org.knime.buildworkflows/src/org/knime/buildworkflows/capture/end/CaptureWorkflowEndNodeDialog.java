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
 *   Feb 11, 2020 (hornm): created
 */
package org.knime.buildworkflows.capture.end;

import static org.knime.buildworkflows.capture.end.CaptureWorkflowEndNodeModel.loadAndFillInputOutputIDs;
import static org.knime.buildworkflows.capture.end.CaptureWorkflowEndNodeModel.saveInputOutputIDs;
import static org.knime.core.node.workflow.capture.WorkflowPortObjectSpec.ensureInputIDsCount;
import static org.knime.core.node.workflow.capture.WorkflowPortObjectSpec.ensureOutputIDsCount;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowCaptureOperation;
import org.knime.core.node.workflow.capture.WorkflowFragment.Input;
import org.knime.core.node.workflow.capture.WorkflowFragment.Output;

/**
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
class CaptureWorkflowEndNodeDialog extends DefaultNodeSettingsPane {
    private static final NodeLogger LOGGER = NodeLogger.getLogger(CaptureWorkflowEndNodeDialog.class);

    private final SettingsModelIntegerBounded m_maxNumOfRowsModel;

    private final SettingsModelBoolean m_addInputDataModel;

    private final JPanel m_ioIds;

    private InputOutputIDsPanel m_idsPanel;

    CaptureWorkflowEndNodeDialog() {

        createNewGroup("Custom workflow name");
        addDialogComponent(new DialogComponentString(CaptureWorkflowEndNodeModel.createCustomWorkflowNameModel(),
            ""));
        closeCurrentGroup();

        createNewGroup("Input Data");
        m_addInputDataModel = CaptureWorkflowEndNodeModel.createAddInputDataModel();
        m_maxNumOfRowsModel = CaptureWorkflowEndNodeModel.createMaxNumOfRowsModel();
        m_addInputDataModel.addChangeListener(l -> {
            m_maxNumOfRowsModel.setEnabled(m_addInputDataModel.getBooleanValue());
        });
        addDialogComponent(new DialogComponentBoolean(m_addInputDataModel, "Store input tables"));
        addDialogComponent(new DialogComponentNumber(m_maxNumOfRowsModel, "Maximum numbers of rows to store", 1));
        closeCurrentGroup();

        m_ioIds = new JPanel(new BorderLayout());
        addTab("Input & Output IDs", m_ioIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadAdditionalSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        NodeContainer nc = NodeContext.getContext().getNodeContainer();
        if (nc == null) {
            throw new NotConfigurableException("No node context available.");
        }

        m_maxNumOfRowsModel.setEnabled(m_addInputDataModel.getBooleanValue());

        List<String> customInputIDs = new ArrayList<>();
        List<String> customOutputIDs = new ArrayList<>();
        try {
            loadAndFillInputOutputIDs(settings, customInputIDs, customOutputIDs);
        } catch (InvalidSettingsException e) {
            LOGGER.warn("Settings couldn't be load for dialog. Ignored.", e);
        }

        WorkflowCaptureOperation captureOp;
        try {
            captureOp = nc.getParent().createCaptureOperationFor(nc.getID());
            m_ioIds.removeAll();

            //get 'connected' inputs and outputs only
            List<Input> inputs = captureOp.getInputs().stream().filter(Input::isConnected).collect(Collectors.toList());
            List<Output> outputs =
                captureOp.getOutputs().stream().filter(Output::isConnected).collect(Collectors.toList());

            customInputIDs = ensureInputIDsCount(customInputIDs, inputs.size());
            customOutputIDs = ensureOutputIDsCount(customOutputIDs, outputs.size());
            m_idsPanel = new InputOutputIDsPanel(customInputIDs, customOutputIDs);
            m_ioIds.add(m_idsPanel, BorderLayout.CENTER);
        } catch (Exception e) {
            throw new NotConfigurableException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveAdditionalSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        List<String> inputIDs = m_idsPanel.getInputIDs();
        List<String> outputIDs = m_idsPanel.getOutputIDs();
        boolean inDup = hasDuplicates(inputIDs);
        boolean outDup = hasDuplicates(outputIDs);
        m_idsPanel.setInputsOutputsInvalid(inDup, outDup);
        if (inDup || outDup) {
            throw new InvalidSettingsException("Duplicate input or output IDs");
        }
        saveInputOutputIDs(settings, inputIDs, outputIDs);
    }

    private static boolean hasDuplicates(final List<String> ids) {
        Set<String> set = new HashSet<>(ids);
        return set.size() != ids.size();
    }
}
