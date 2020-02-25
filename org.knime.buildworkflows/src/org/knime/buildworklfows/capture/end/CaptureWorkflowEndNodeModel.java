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
 *   Dec 9, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.buildworklfows.capture.end;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.knime.core.data.DataTable;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.container.filter.TableFilter;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.CaptureWorkflowEndNode;
import org.knime.core.node.workflow.CaptureWorkflowStartNode;
import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowFragment.Port;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;

/**
 * The node model of the Capture Workflow End node that marks the end of a captured workflow fragment.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class CaptureWorkflowEndNodeModel extends NodeModel implements CaptureWorkflowEndNode {

    static SettingsModelString createCustomWorkflowNameModel() {
        return new SettingsModelString("custom_workflow_name", "");
    }

    static SettingsModelBoolean createAddInputDataModel() {
        return new SettingsModelBoolean("add_input_data", false);
    }

    static SettingsModelIntegerBounded createMaxNumOfRowsModel() {
        return new SettingsModelIntegerBounded("max_num_rows", 10, 1, Integer.MAX_VALUE);
    }

    private final SettingsModelString m_customWorkflowName = createCustomWorkflowNameModel();

    private final SettingsModelBoolean m_addInputData = createAddInputDataModel();

    private final SettingsModelInteger m_maxNumRows = createMaxNumOfRowsModel();

    private WorkflowFragment m_lastFragment;

    private final Map<PortID, String> m_inPortNames = new HashMap<>();

    private final Map<PortID, String> m_outPortNames = new HashMap<>();

    /**
     * @param portsConfiguration the {@link PortsConfiguration} provided by the user
     */
    protected CaptureWorkflowEndNodeModel(final PortsConfiguration portsConfiguration) {
        super(portsConfiguration.getInputPorts(), portsConfiguration.getOutputPorts());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        checkForCaptureNodeStart();

        final NodeContainer container = NodeContext.getContext().getNodeContainer();
        final WorkflowManager manager = container.getParent();
        WorkflowFragment wff;
        try {
            wff = manager.createCaptureOperationFor(container.getID()).capture();
        } catch (Exception e) {
            throw new IllegalStateException("Capturing the workflow failed.", e);
        }
        removeFragment();
        m_lastFragment = wff;
        final WorkflowPortObjectSpec spec =
            new WorkflowPortObjectSpec(wff, getCustomWorkflowName(), m_inPortNames, m_outPortNames);
        return Stream.concat(Arrays.stream(inSpecs), Stream.of(spec)).toArray(PortObjectSpec[]::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        checkForCaptureNodeStart();
        Map<PortID, DataTable> inputData = null;
        if (m_addInputData.getBooleanValue()) {
            inputData = getInputData(m_lastFragment.getInputPorts(), m_maxNumRows.getIntValue(), exec);
        }
        final WorkflowPortObject po = new WorkflowPortObject(
            new WorkflowPortObjectSpec(m_lastFragment, getCustomWorkflowName(), m_inPortNames, m_outPortNames),
            inputData);
        return Stream.concat(Arrays.stream(inObjects), Stream.of(po)).toArray(PortObject[]::new);
    }

    private String getCustomWorkflowName() {
        return m_customWorkflowName.getStringValue().isEmpty() ? null : m_customWorkflowName.getStringValue();
    }

    /*
     * Retrieves the input tables for the given input ports (if they accept tables).
     */
    private static Map<PortID, DataTable> getInputData(final List<Port> inputPorts, final int numRowsToStore,
        final ExecutionContext exec) throws CanceledExecutionException {
        WorkflowManager wfm = NodeContext.getContext().getNodeContainer().getParent();
        Map<PortID, DataTable> inputData = new HashMap<>();
        for (Port p : inputPorts) {
            if (p.getType().isPresent() && p.getType().get().equals(BufferedDataTable.TYPE)) {
                ConnectionContainer cc = wfm.getIncomingConnectionFor(
                    p.getID().getNodeIDSuffix().prependParent(wfm.getID()), p.getID().getIndex());
                BufferedDataTable table = (BufferedDataTable)wfm.getNodeContainer(cc.getSource())
                    .getOutPort(cc.getSourcePort()).getPortObject();
                if (numRowsToStore > 0) {
                    BufferedDataContainer container = exec.createDataContainer(table.getDataTableSpec());
                    try (CloseableRowIterator iterator = table
                        .filter(TableFilter.filterRowsToIndex(Math.min(numRowsToStore, table.size()))).iterator()) {
                        exec.checkCanceled();
                        container.addRowToTable(iterator.next());
                    }
                    container.close();
                    table = container.getTable();
                }
                inputData.put(p.getID(), table);
            }
        }
        return inputData;
    }

    private void checkForCaptureNodeStart() throws InvalidSettingsException {
        if (!getScopeStartNode(CaptureWorkflowStartNode.class).isPresent()) {
            throw new InvalidSettingsException("No corresponding 'Capture Workflow Start' node found");
        }
    }

    private void removeFragment() {
        if (m_lastFragment != null) {
            m_lastFragment.disposeWorkflow();
            m_lastFragment = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        if (!m_customWorkflowName.getStringValue().isEmpty()) {
            m_customWorkflowName.saveSettingsTo(settings);
        }
        m_addInputData.saveSettingsTo(settings);
        m_maxNumRows.saveSettingsTo(settings);
        savePortNames(settings, m_inPortNames, m_outPortNames);
    }

    static void savePortNames(final NodeSettingsWO settings, final Map<PortID, String> inPortNames,
        final Map<PortID, String> outPortNames) {
        if (inPortNames.isEmpty() && outPortNames.isEmpty()) {
            return;
        }
        NodeSettingsWO subSettings = settings.addNodeSettings("port_names");
        int i = 0;
        subSettings.addInt("num_in_port_names", inPortNames.size());
        for (Entry<PortID, String> in : inPortNames.entrySet()) {
            NodeSettingsWO portSettings = subSettings.addNodeSettings("in_port_" + i);
            portSettings.addString("node_id", in.getKey().getNodeIDSuffix().toString());
            portSettings.addInt("index", in.getKey().getIndex());
            portSettings.addString("name", in.getValue());
            i++;
        }

        i = 0;
        subSettings.addInt("num_out_port_names", outPortNames.size());
        for (Entry<PortID, String> in : outPortNames.entrySet()) {
            NodeSettingsWO portSettings = subSettings.addNodeSettings("out_port_" + i);
            portSettings.addString("node_id", in.getKey().getNodeIDSuffix().toString());
            portSettings.addInt("index", in.getKey().getIndex());
            portSettings.addString("name", in.getValue());
            i++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_addInputData.validateSettings(settings);
        m_maxNumRows.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        if (settings.containsKey(m_customWorkflowName.getKey())) {
            m_customWorkflowName.loadSettingsFrom(settings);
        }
        m_addInputData.loadSettingsFrom(settings);
        m_maxNumRows.loadSettingsFrom(settings);
        m_inPortNames.clear();
        m_outPortNames.clear();
        loadAndFillPortNames(settings, m_inPortNames, m_outPortNames);
    }

    /**
     * Loads the port names and adds them to the supplied maps.
     *
     * @param settings to load the port names from
     * @param inPortNames the map the input port names are added to
     * @param outPortNames the map the output port names are added to
     * @throws InvalidSettingsException
     */
    static void loadAndFillPortNames(final NodeSettingsRO settings, final Map<PortID, String> inPortNames,
        final Map<PortID, String> outPortNames) throws InvalidSettingsException {
        if (!settings.containsKey("port_names")) {
            return;
        }
        NodeSettingsRO subSettings = settings.getNodeSettings("port_names");
        int num = subSettings.getInt("num_in_port_names");
        for (int i = 0; i < num; i++) {
            NodeSettingsRO portSettings = subSettings.getNodeSettings("in_port_" + i);
            inPortNames.put(
                new PortID(NodeIDSuffix.fromString(portSettings.getString("node_id")), portSettings.getInt("index")),
                portSettings.getString("name"));
        }

        num = subSettings.getInt("num_out_port_names");
        for (int i = 0; i < num; i++) {
            NodeSettingsRO portSettings = subSettings.getNodeSettings("out_port_" + i);
            outPortNames.put(
                new PortID(NodeIDSuffix.fromString(portSettings.getString("node_id")), portSettings.getInt("index")),
                portSettings.getString("name"));
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        removeFragment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDispose() {
        removeFragment();
    }

}
