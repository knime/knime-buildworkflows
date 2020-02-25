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
 *   Dec 9, 2019 (Mark Ortmann, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.buildworkflows.combiner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.data.DataTable;
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
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.NodeUIInformation;
import org.knime.core.node.workflow.WorkflowCopyContent;
import org.knime.core.node.workflow.WorkflowLock;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowFragment.Port;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.util.Pair;

/**
 * Merges several {@link WorkflowPortObject} objects into a single {@link WorkflowPortObject}.
 *
 * @author Mark Ortmann, KNIME GmbH, Berlin, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowCombinerNodeModel extends NodeModel {

    static final String CFG_CONNECTION_MAPS = "connection_maps";

    private NodeID m_wfmID;

    private ConnectionMaps m_connectionMaps = ConnectionMaps.PAIR_WISE_CONNECTION_MAPS;

    /**
     * Constructor.
     *
     * @param portsConfiguration the ports configuration
     */
    WorkflowCombinerNodeModel(final PortsConfiguration portsConfiguration) {
        super(portsConfiguration.getInputPorts(), portsConfiguration.getOutputPorts());
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final WorkflowFragment[] inWorkflowFragments = Arrays.stream(inSpecs)
            .map(s -> ((WorkflowPortObjectSpec)s).getWorkflowFragment()).toArray(WorkflowFragment[]::new);
        for (int i = 1; i < inSpecs.length; i++) {
            canConnect(inWorkflowFragments[i - 1], inWorkflowFragments[i], i - 1);
        }
        return null;
    }

    /**
     * Checks whether two workflow fragments can be connected.
     *
     * @param pred the predecessor workflow fragment
     * @param succ the successor workflow fragment
     * @param connectionIdx the index of the workflow fragment connection
     * @throws InvalidSettingsException if the workflow fragments couldn't be connected completely
     */
    private void canConnect(final WorkflowFragment pred, final WorkflowFragment succ, final int connectionIdx)
        throws InvalidSettingsException {
        m_connectionMaps.getConnectionMap(connectionIdx).getConnectionsFor(pred.getOutputPorts(), succ.getInputPorts());
    }

    @Override
    protected PortObject[] execute(final PortObject[] input, final ExecutionContext exec) throws Exception {
        final WorkflowPortObjectSpec[] inWorkflowSpecs =
            Arrays.stream(input).map(s -> ((WorkflowPortObject)s).getSpec()).toArray(WorkflowPortObjectSpec[]::new);

        // clear if needed - should not be necessary
        clear();

        // create the metanode storing the combined workflow
        WorkflowManager wfm = createWFM();
        // store the id so we can remove that node in case something goes wrong or we reset/dispose the node
        m_wfmID = wfm.getID();

        //transfer the editor settings from the first fragment
        //the loaded workflow will be disposed in the copy-method below
        wfm.setEditorUIInformation(inWorkflowSpecs[0].getWorkflowFragment().loadWorkflow().getEditorUIInformation());

        try {
            // copy and paste all fragments to the new wfm
            final WorkflowFragmentMeta[] inWorkflowFragments = Arrays.stream(inWorkflowSpecs)
                .map(s -> copy(wfm, s.getWorkflowFragment())).toArray(WorkflowFragmentMeta[]::new);

            final Set<NodeIDSuffix> objectReferenceReaderNodes =
                new HashSet<>(inWorkflowFragments[0].m_objectReferenceReaderNodes);

            for (int i = 1; i < inWorkflowFragments.length; i++) {
                connect(wfm, m_connectionMaps.getConnectionMap(i - 1), inWorkflowFragments[i - 1].m_outputPorts,
                    inWorkflowFragments[i - 1].m_outIdMapping, inWorkflowFragments[i].m_inputPorts,
                    inWorkflowFragments[i].m_inIdMapping);
                objectReferenceReaderNodes.addAll(inWorkflowFragments[i].m_objectReferenceReaderNodes);
            }

            WorkflowPortObject firstWorkflowPortObject = (WorkflowPortObject)input[0];
            Map<PortID, DataTable> inputData = inWorkflowFragments[0].m_inputPorts.stream()
                .filter(p -> firstWorkflowPortObject.getInputDataFor(p.getID()).isPresent()).collect(
                    Collectors.toMap(Port::getID, p -> firstWorkflowPortObject.getInputDataFor(p.getID()).get()));

            String workflowName = inWorkflowSpecs[0].getWorkflowName(); //TODO make configurable
            List<Port> newInputPorts = collectAndMapAllRemainingInputPorts(inWorkflowFragments);
            List<Port> newOutputPorts = collectAndMapAllRemainingOutputPorts(inWorkflowFragments);
            Map<PortID, String> newInputPortNamesMap =
                collectAndMapAllRemainingInputPortNames(inWorkflowSpecs, inWorkflowFragments);
            Map<PortID, String> newOutputPortNamesMap =
                collectAndMapAllRemainingOutputPortNames(inWorkflowSpecs, inWorkflowFragments);
            return new PortObject[]{new WorkflowPortObject(new WorkflowPortObjectSpec(
                new WorkflowFragment(wfm, newInputPorts, newOutputPorts, objectReferenceReaderNodes), workflowName,
                newInputPortNamesMap, newOutputPortNamesMap), inputData)};
        } catch (final Exception e) {
            // in case something goes wrong ensure that newly created metanode/component is removed
            clear();
            throw (e);
        }
    }

    private static WorkflowManager createWFM() {
        return WorkflowManager.EXTRACTED_WORKFLOW_ROOT.createAndAddSubWorkflow(new PortType[0], new PortType[0],
            "workflow_combiner");
    }

    private static WorkflowFragmentMeta copy(final WorkflowManager wfm, final WorkflowFragment toCopy) {
        // copy and paste the workflow fragment into the new wfm
        // calculate the mapping between the toCopy node ids and the new node ids
        final HashMap<NodeIDSuffix, NodeIDSuffix> inIdMapping = new HashMap<>();
        final HashMap<NodeIDSuffix, NodeIDSuffix> outIdMapping = new HashMap<>();
        final HashSet<NodeIDSuffix> objectReferenceReaderNodes = new HashSet<>();

        final WorkflowManager toCopyWFM = toCopy.loadWorkflow();
        try (WorkflowLock lock = wfm.lock()) {
            int[] wfmBoundingBox = NodeUIInformation.getBoundingBoxOf(wfm.getNodeContainers());
            final int yOffset = wfmBoundingBox[1]; // top
            final int xOffset = wfmBoundingBox[2]; // far right

            final NodeID[] ids = toCopyWFM.getNodeContainers().stream().map(c -> c.getID()).toArray(NodeID[]::new);
            WorkflowCopyContent.Builder sourceContent =
                WorkflowCopyContent.builder().setNodeIDs(ids).setIncludeInOutConnections(false);
            sourceContent.setPositionOffset(new int[] {xOffset, yOffset});
            final WorkflowCopyContent pastedContent = wfm.copyFromAndPasteHere(toCopyWFM, sourceContent.build());

            // store the new ids
            final NodeID[] newIds = pastedContent.getNodeIDs();
            for (int i = 0; i < ids.length; i++) {
                final NodeIDSuffix toCopyID = NodeIDSuffix.create(toCopyWFM.getID(), ids[i]);
                final List<Port> ports = toCopy.getInputPorts();
                Optional<Port> inPort =
                    ports.stream().filter(p -> p.getID().getNodeIDSuffix().equals(toCopyID)).findAny();
                if (toCopy.getPortObjectReferenceReaderNodes().contains(toCopyID)) {
                    objectReferenceReaderNodes.add(NodeIDSuffix.create(wfm.getID(), newIds[i]));
                }

                if (inPort.isPresent()) {
                    inIdMapping.put(toCopyID, NodeIDSuffix.create(wfm.getID(), newIds[i]));
                }
                Optional<Port> outPort = toCopy.getOutputPorts().stream()
                    .filter(p -> p.getID().getNodeIDSuffix().equals(toCopyID)).findAny();
                if (outPort.isPresent()) {
                    outIdMapping.put(toCopyID, NodeIDSuffix.create(wfm.getID(), newIds[i]));
                }
            }
        } finally {
            toCopy.disposeWorkflow();
        }

        // return the new fragment metadata
        WorkflowFragmentMeta res = new WorkflowFragmentMeta();
        res.m_inputPorts = new ArrayList<>(toCopy.getInputPorts());
        res.m_inIdMapping = inIdMapping;
        res.m_outputPorts = new ArrayList<>(toCopy.getOutputPorts());
        res.m_outIdMapping = outIdMapping;
        res.m_objectReferenceReaderNodes = objectReferenceReaderNodes;
        return res;
    }

    /**
     * Adds the configured connections (represented by a {@link ConnectionMap}) to a workflow manager.
     *
     * The output and input ports that have been connected are removed from the supplied output and input port lists!
     *
     * @param wfm the workflow manager to add the connections to
     * @param connectionMap the chosen connections
     * @param outPorts will be modified - connected outports are removed!
     * @param outIdMapping the mapping to the actual (new) node id
     * @param inPorts will be modified - connected inputs are removed!
     * @param inIdMapping the mapping to the actual (new) node id
     * @throws InvalidSettingsException
     */
    private static void connect(final WorkflowManager wfm, final ConnectionMap connectionMap, final List<Port> outPorts,
        final Map<NodeIDSuffix, NodeIDSuffix> outIdMapping, final List<Port> inPorts,
        final Map<NodeIDSuffix, NodeIDSuffix> inIdMapping) throws InvalidSettingsException {
        List<Pair<PortID, PortID>> connectionsToAdd = connectionMap.getConnectionsFor(outPorts, inPorts);
        for (Pair<PortID, PortID> connection : connectionsToAdd) {
            final PortID outPort = connection.getFirst();
            final PortID inPort = connection.getSecond();
            NodeIDSuffix outNodeId = outIdMapping.get(outPort.getNodeIDSuffix());
            NodeIDSuffix inNodeId = inIdMapping.get(inPort.getNodeIDSuffix());
            wfm.addConnection(outNodeId.prependParent(wfm.getID()), outPort.getIndex(),
                inNodeId.prependParent(wfm.getID()), inPort.getIndex());
            outPorts.removeIf(p -> p.getID().equals(outPort));
            inPorts.removeIf(p -> p.getID().equals(inPort));
        }
    }

    private static List<Port> collectAndMapAllRemainingInputPorts(final WorkflowFragmentMeta[] fragments) {
        List<Port> inputPorts = new ArrayList<>();
        for (WorkflowFragmentMeta f : fragments) {
            for (Port p : f.m_inputPorts) {
                inputPorts.add(getMappedPort(p, f.m_inIdMapping));
            }
        }
        return inputPorts;
    }

    private static List<Port> collectAndMapAllRemainingOutputPorts(final WorkflowFragmentMeta[] fragments) {
        List<Port> outputPorts = new ArrayList<>();
        for (WorkflowFragmentMeta f : fragments) {
            for (Port p : f.m_outputPorts) {
                outputPorts.add(getMappedPort(p, f.m_outIdMapping));
            }
        }
        return outputPorts;
    }

    private static Map<PortID, String> collectAndMapAllRemainingInputPortNames(
        final WorkflowPortObjectSpec[] workflowSpecs, final WorkflowFragmentMeta[] fragments) {
        Map<PortID, String> allInputNamesMap = new HashMap<>();
        for (int i = 0; i < fragments.length; i++) {
            Map<PortID, String> inputPortNamesMap = workflowSpecs[i].getInputPortNamesMap();
            for (Port p : fragments[i].m_inputPorts) {
                if (inputPortNamesMap.containsKey(p.getID())) {
                    allInputNamesMap.put(getMappedPortID(p, fragments[i].m_inIdMapping),
                        inputPortNamesMap.get(p.getID()));
                }
            }
        }
        return allInputNamesMap;
    }

    private static Map<PortID, String> collectAndMapAllRemainingOutputPortNames(
        final WorkflowPortObjectSpec[] workflowSpecs, final WorkflowFragmentMeta[] fragments) {
        Map<PortID, String> allOutputNamesMap = new HashMap<>();
        for (int i = 0; i < fragments.length; i++) {
            Map<PortID, String> outputPortNamesMap = workflowSpecs[i].getOutputPortNamesMap();
            for (Port p : fragments[i].m_outputPorts) {
                if (outputPortNamesMap.containsKey(p.getID())) {
                    allOutputNamesMap.put(getMappedPortID(p, fragments[i].m_outIdMapping),
                        outputPortNamesMap.get(p.getID()));
                }
            }
        }
        return allOutputNamesMap;
    }

    private static Port getMappedPort(final Port p, final Map<NodeIDSuffix, NodeIDSuffix> inIdMapping) {
        return new Port(getMappedPortID(p, inIdMapping), p.getType().orElse(null), p.getSpec().orElse(null));
    }

    private static PortID getMappedPortID(final Port p, final Map<NodeIDSuffix, NodeIDSuffix> inIdMapping) {
        return new PortID(inIdMapping.get(p.getID().getNodeIDSuffix()), p.getID().getIndex());
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
        NodeSettingsWO cmSettings = settings.addNodeSettings(CFG_CONNECTION_MAPS);
        m_connectionMaps.save(cmSettings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        if (settings.containsKey(CFG_CONNECTION_MAPS)) {
            NodeSettingsRO cmSettings = settings.getNodeSettings(CFG_CONNECTION_MAPS);
            new ConnectionMaps().load(cmSettings);
        }
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_connectionMaps = new ConnectionMaps();
        if (settings.containsKey(CFG_CONNECTION_MAPS)) {
            NodeSettingsRO cmSettings = settings.getNodeSettings(CFG_CONNECTION_MAPS);
            m_connectionMaps.load(cmSettings);
        }
    }

    @Override
    protected void reset() {
        clear();
    }

    @Override
    protected void onDispose() {
        clear();
        super.onDispose();
    }

    private void clear() {
        if (m_wfmID != null) {
            WorkflowManager.EXTRACTED_WORKFLOW_ROOT.removeNode(m_wfmID);
        }
        m_wfmID = null;
    }

    /**
     * Internal helper class to be able to summarize and return the metadata of a workflow fragment.
     */
    private static class WorkflowFragmentMeta {

        List<Port> m_inputPorts;

        Map<NodeIDSuffix, NodeIDSuffix> m_inIdMapping;

        List<Port> m_outputPorts;

        Map<NodeIDSuffix, NodeIDSuffix> m_outIdMapping;

        Set<NodeIDSuffix> m_objectReferenceReaderNodes;

    }

}
