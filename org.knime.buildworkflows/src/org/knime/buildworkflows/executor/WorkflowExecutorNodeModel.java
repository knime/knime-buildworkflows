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
package org.knime.buildworkflows.executor;

import static java.util.stream.Collectors.toList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.knime.buildworkflows.util.BuildWorkflowsUtil;
import org.knime.core.data.DataRow;
import org.knime.core.data.filestore.FileStorePortObject;
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
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.FlowVariable.Scope;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeUIInformation;
import org.knime.core.node.workflow.SingleNodeContainer;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.WorkflowCopyContent;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowFragment.Input;
import org.knime.core.node.workflow.capture.WorkflowFragment.Output;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.node.workflow.virtual.parchunk.VirtualParallelizedChunkNodeInput;
import org.knime.core.node.workflow.virtual.parchunk.VirtualParallelizedChunkPortObjectInNodeFactory;
import org.knime.core.node.workflow.virtual.parchunk.VirtualParallelizedChunkPortObjectInNodeModel;
import org.knime.core.node.workflow.virtual.parchunk.VirtualParallelizedChunkPortObjectOutNodeFactory;
import org.knime.core.node.workflow.virtual.parchunk.VirtualParallelizedChunkPortObjectOutNodeModel;
import org.knime.core.util.Pair;

/**
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
class WorkflowExecutorNodeModel extends NodeModel {

    static final String CFG_DEBUG = "debug";

    private WorkflowExecutable m_executable;

    private boolean m_debug = false;

    WorkflowExecutorNodeModel(final PortsConfiguration portsConf) {
        super(portsConf.getInputPorts(), portsConf.getOutputPorts());
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        WorkflowPortObjectSpec wpos = (WorkflowPortObjectSpec)inSpecs[0];
        NodeContainer nc = NodeContext.getContext().getNodeContainer();
        CheckUtils.checkArgumentNotNull(nc, "Not a local workflow");
        checkPortCompatibility(wpos, nc);
        return null;
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        WorkflowPortObject wpo = (WorkflowPortObject)inObjects[0];
        WorkflowExecutable we = createWorkflowExecutable(wpo.getSpec());
        m_executable = we;
        boolean success = false;
        try {
            exec.setMessage("Executing workflow fragment '" + wpo.getSpec().getWorkflowName() + "'");
            Pair<PortObject[], List<FlowVariable>> output =
                we.executeWorkflow(Arrays.copyOfRange(inObjects, 1, inObjects.length), exec);
            if (output.getFirst() == null || Arrays.stream(output.getFirst()).anyMatch(Objects::isNull)) {
                throw new IllegalStateException("Execution didn't finish successfully");
            }

            exec.setMessage("Transferring result data");
            PortObject[] portObjects = new PortObject[output.getFirst().length];
            for (int i = 0; i < output.getFirst().length; i++) {
                PortObject po = output.getFirst()[i];
                if (po instanceof BufferedDataTable) {
                    // copy data tables
                    // This is required in order to copy the data tables from the node(s) within the metanode
                    // the workflow fragment has been executed in into this node. The data tables to copy could
                    // be 'spread' over multiple nodes (i.e. via back-references, e.g. column appender) and this
                    // operation also turns those into one single table stored with this node.
                    BufferedDataTable bdt = (BufferedDataTable)po;
                    BufferedDataContainer container = exec.createDataContainer((bdt).getDataTableSpec());
                    for (DataRow row : bdt) {
                        container.addRowToTable(row);
                    }
                    container.close();
                    portObjects[i] = container.getTable();
                } else if (po instanceof FileStorePortObject) {
                    //copy file store port objects
                    //TODO is there a way to reduce the memory footprint of the copy operation?
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    PortUtil.writeObjectToStream(po, out, exec);
                    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
                    portObjects[i] = PortUtil.readObjectFromStream(in, exec);
                } else {
                    // port objects are always copied and this is already a copy
                    portObjects[i] = po;
                }
            }

            //push flow variables
            for (FlowVariable fv : output.getSecond()) {
                pushFlowVariable(fv);
            }

            success = true;
            return portObjects;
        } finally {
            if (!m_debug || success) {
                disposeWorkflowExecutable();
            } else {
                m_executable.cancel();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void pushFlowVariable(final FlowVariable fv) {
        pushFlowVariable(fv.getName(), (VariableType<T>)fv.getVariableType(), (T)fv.getValue(fv.getVariableType()));
    }

    private WorkflowExecutable createWorkflowExecutable(final WorkflowPortObjectSpec spec)
        throws InvalidSettingsException {
        disposeWorkflowExecutable();
        NodeContainer nc = NodeContext.getContext().getNodeContainer();
        CheckUtils.checkArgumentNotNull(nc, "Not a local workflow");
        checkPortCompatibility(spec, nc);
        m_executable = new WorkflowExecutable(spec, nc, m_debug);
        return m_executable;
    }

    private void disposeWorkflowExecutable() {
        if (m_executable != null) {
            m_executable.dispose();
            m_executable = null;
        }
    }

    /**
     * Checks for compatibility of the node ports and the workflow inputs/outputs. The flow variable ports (0th index at
     * input and output) and workflow port (1st input) are not taken into account.
     *
     * @param spec
     * @param nc
     * @throws InvalidSettingsException if not compatible
     */
    static void checkPortCompatibility(final WorkflowPortObjectSpec spec, final NodeContainer nc)
        throws InvalidSettingsException {
        String configMessage = "Node needs to be re-configured.";
        List<PortType> wfInputs =
            spec.getWorkflowFragment().getConnectedInputs().stream().map(i -> i.getType().get()).collect(toList());
        List<PortType> nodeInputs =
            IntStream.range(2, nc.getNrInPorts()).mapToObj(i -> nc.getInPort(i).getPortType()).collect(toList());
        if (!wfInputs.equals(nodeInputs)) {
            throw new InvalidSettingsException(
                "The node inputs don't match with the workflow inputs. " + configMessage);
        }

        List<PortType> wfOutputs =
            spec.getWorkflowFragment().getConnectedOutputs().stream().map(i -> i.getType().get()).collect(toList());
        List<PortType> nodeOutputs =
            IntStream.range(1, nc.getNrOutPorts()).mapToObj(i -> nc.getOutPort(i).getPortType()).collect(toList());
        if (!wfOutputs.equals(nodeOutputs)) {
            throw new InvalidSettingsException(
                "The node outputs don't match with the workflow outputs. " + configMessage);
        }
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        //
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        //
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addBoolean(CFG_DEBUG, m_debug);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        //
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_debug = settings.getBoolean(CFG_DEBUG);
    }

    @Override
    protected void reset() {
        if (!m_debug) {
            disposeWorkflowExecutable();
        }
    }

    private static class WorkflowExecutable {

        private WorkflowManager m_wfm;

        private NodeID m_virtualStartID;

        private NodeID m_virtualEndID;

        private NodeContainer m_thisNode;

        WorkflowExecutable(final WorkflowPortObjectSpec wfSpec, final NodeContainer thisNode, final boolean debug) {
            m_thisNode = thisNode;
            m_wfm = thisNode.getParent().createAndAddSubWorkflow(new PortType[0], new PortType[0],
                (debug ? "Debug: " : "") + wfSpec.getWorkflowName());
            if (!debug) {
                m_wfm.hideInUI();
            }
            NodeUIInformation startUIPlain = thisNode.getUIInformation();
            if (startUIPlain != null) {
                NodeUIInformation startUI =
                    NodeUIInformation.builder(startUIPlain).translate(new int[]{60, -60, 0, 0}).build();
                m_wfm.setUIInformation(startUI);
            }

            WorkflowFragment wf = wfSpec.getWorkflowFragment();
            WorkflowManager wfm = wf.loadWorkflow();
            NodeID[] ids = wfm.getNodeContainers().stream().map(NodeContainer::getID).toArray(NodeID[]::new);
            m_wfm.copyFromAndPasteHere(wfm, WorkflowCopyContent.builder().setNodeIDs(ids).build());
            wf.disposeWorkflow();

            //add virtual in node
            List<Input> inputs = wf.getConnectedInputs();
            PortType[] inTypes =
                inputs.stream().map(i -> getNonOptionalType(i.getType().get())).toArray(s -> new PortType[s]);
            int[] wfBounds = NodeUIInformation.getBoundingBoxOf(m_wfm.getNodeContainers());
            m_virtualStartID = m_wfm.createAndAddNode(new VirtualParallelizedChunkPortObjectInNodeFactory(inTypes));
            Pair<Integer, int[]> pos = BuildWorkflowsUtil.getInputOutputNodePositions(wfBounds, 1, true);
            m_wfm.getNodeContainer(m_virtualStartID).setUIInformation(
                NodeUIInformation.builder().setNodeLocation(pos.getFirst(), pos.getSecond()[0], -1, -1).build());

            //add virtual out node
            List<Output> outputs = wf.getConnectedOutputs();
            PortType[] outTypes =
                outputs.stream().map(o -> getNonOptionalType(o.getType().get())).toArray(s -> new PortType[s]);
            m_virtualEndID = m_wfm.createAndAddNode(new VirtualParallelizedChunkPortObjectOutNodeFactory(outTypes));
            pos = BuildWorkflowsUtil.getInputOutputNodePositions(wfBounds, 1, false);
            m_wfm.getNodeContainer(m_virtualEndID).setUIInformation(
                NodeUIInformation.builder().setNodeLocation(pos.getFirst(), pos.getSecond()[0], -1, -1).build());

            //connect virtual in
            for (int i = 0; i < inputs.size(); i++) {
                for (PortID p : inputs.get(i).getConnectedPorts()) {
                    m_wfm.addConnection(m_virtualStartID, i + 1, p.getNodeIDSuffix().prependParent(m_wfm.getID()),
                        p.getIndex());
                }
            }

            //connect virtual out
            for (int i = 0; i < outputs.size(); i++) {
                PortID p = outputs.get(i).getConnectedPort().get();
                m_wfm.addConnection(p.getNodeIDSuffix().prependParent(m_wfm.getID()), p.getIndex(), m_virtualEndID,
                    i + 1);
            }
        }

        private static PortType getNonOptionalType(final PortType p) {
            return PortTypeRegistry.getInstance().getPortType(p.getPortObjectClass());
        }

        /**
         * Executes the workflow fragment.
         *
         * @param inputData the input data to be used for execution
         * @param exec for cancellation
         * @return the resulting port objects and flow variables
         * @throws InterruptedException
         * @throws CanceledExecutionException
         */
        Pair<PortObject[], List<FlowVariable>> executeWorkflow(final PortObject[] inputData,
            final ExecutionMonitor exec) throws InterruptedException, CanceledExecutionException {
            VirtualParallelizedChunkPortObjectInNodeModel inNM =
                (VirtualParallelizedChunkPortObjectInNodeModel)((NativeNodeContainer)m_wfm
                    .getNodeContainer(m_virtualStartID)).getNodeModel();
            inNM.setVirtualNodeInput(new VirtualParallelizedChunkNodeInput(inputData,
                collectOutputFlowVariablesFromUpstreamNodes(m_thisNode), 0));

            m_wfm.executeUpToHere(m_virtualEndID);
            while (m_wfm.getNodeContainerState().isExecutionInProgress()) {
                m_wfm.waitWhileInExecution(1, TimeUnit.SECONDS);
                exec.checkCanceled();
            }

            NativeNodeContainer nnc = (NativeNodeContainer)m_wfm.getNodeContainer(m_virtualEndID);
            VirtualParallelizedChunkPortObjectOutNodeModel outNM =
                (VirtualParallelizedChunkPortObjectOutNodeModel)nnc.getNodeModel();
            return Pair.create(outNM.getOutObjects(), getFlowVariablesFromNC(nnc).collect(toList()));
        }

        void dispose() {
            cancel();
            m_wfm.getParent().removeNode(m_wfm.getID());
        }

        void cancel() {
            if (m_wfm.getNodeContainerState().isExecutionInProgress()) {
                m_wfm.cancelExecution(m_wfm);
            }
        }

        private static Stream<FlowVariable> getFlowVariablesFromNC(final NodeContainer nc) {
            if (nc instanceof SingleNodeContainer) {
                return ((SingleNodeContainer)nc).createOutFlowObjectStack().getAllAvailableFlowVariables().values()
                    .stream().filter(fv -> fv.getScope() == Scope.Flow);
            } else {
                return Stream.empty();
            }
        }

        /*
         * Essentially only take the flow variables coming in via the 2nd to nth input port (and ignore flow var (0th)
         * and workflow (1st) port). Otherwise those will always take precedence what we don't want.
         */
        private static List<FlowVariable> collectOutputFlowVariablesFromUpstreamNodes(final NodeContainer thisNode) {
            //skip flow var (0th) and workflow (1st) input port
            WorkflowManager wfm = thisNode.getParent();
            List<FlowVariable> res = new ArrayList<>();
            for (int i = 2; i < thisNode.getNrInPorts(); i++) {
                ConnectionContainer cc = wfm.getIncomingConnectionFor(thisNode.getID(), i);
                NodeContainer nc = wfm.getNodeContainer(cc.getSource());
                getFlowVariablesFromNC(nc).forEach(res::add);
            }
            return res;
        }
    }

}
