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
 *   May 5, 2025 (hornm): created
 */
package org.knime.buildworkflows.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.knime.buildworkflows.reader.WorkflowReaderNodeModel;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.dialog.InputNode;
import org.knime.core.node.dialog.OutputNode;
import org.knime.core.node.dialog.SubNodeDescriptionProvider;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.tool.ToolValue.ToolPort;
import org.knime.core.node.tool.WorkflowToolCell;
import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowSegment;
import org.knime.core.node.workflow.capture.WorkflowSegment.Input;
import org.knime.core.node.workflow.capture.WorkflowSegment.Output;
import org.knime.core.node.workflow.capture.WorkflowSegment.PortID;
import org.knime.core.util.JsonUtil;
import org.knime.core.util.LockFailedException;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.ChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.column.ColumnChoicesProvider;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;
import org.knime.core.webui.node.impl.WebUINodeModel;
import org.knime.filehandling.core.connections.location.FSPathProvider;
import org.knime.filehandling.core.connections.location.MultiFSPathProviderFactory;
import org.knime.filehandling.core.data.location.FSLocationValue;

import jakarta.json.JsonObject;

/**
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class Workflows2ToolsNodeFactory extends WebUINodeFactory {

    private static WebUINodeConfiguration CONFIG = WebUINodeConfiguration.builder() //
        .name("Workflows To Tools") //
        .icon(null) //
        .shortDescription("TODO") //
        .fullDescription("TODO") //
        .modelSettingsClass(Worklfows2ToolsNodeSettings.class) //
        .addInputTable("Paths", "TODO") //
        .addOutputPort("Tools", BufferedDataTable.TYPE, "TODO") //
        .build();

    public Workflows2ToolsNodeFactory() {
        super(CONFIG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeModel createNodeModel() {
        return new WebUINodeModel<Worklfows2ToolsNodeSettings>(CONFIG, Worklfows2ToolsNodeSettings.class) {

            /**
             * {@inheritDoc}
             */
            @Override
            protected DataTableSpec[] configure(final DataTableSpec[] inSpecs,
                final Worklfows2ToolsNodeSettings modelSettings) throws InvalidSettingsException {
                return new DataTableSpec[]{createColumnRearranger(null, inSpecs[0], modelSettings).createSpec()};
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec,
                final Worklfows2ToolsNodeSettings modelSettings) throws Exception {
                var spec = inData[0].getSpec();
                var rearranger = createColumnRearranger(exec, spec, modelSettings);
                return new BufferedDataTable[]{exec.createColumnRearrangeTable(inData[0], rearranger, exec)};
            }

            private ColumnRearranger createColumnRearranger(final ExecutionContext exec, final DataTableSpec spec,
                final Worklfows2ToolsNodeSettings settings) throws InvalidSettingsException {
                if (settings.m_columnName == null) {
                    throw new InvalidSettingsException("No path column selected");
                }
                var rearranger = new ColumnRearranger(spec);
                var pathColumnIndex = spec.findColumnIndex(settings.m_columnName);
                if (pathColumnIndex == -1) {
                    throw new InvalidSettingsException("No column found for name " + settings.m_columnName);
                }
                rearranger.replace(new CellFactory() {
                    private final MultiFSPathProviderFactory m_multiFSPathProviderFactory =
                        new MultiFSPathProviderFactory(null);

                    @Override
                    public DataCell[] getCells(final DataRow row) {
                        var fsLocation = ((FSLocationValue)row.getCell(pathColumnIndex)).getFSLocation();
                        try (final FSPathProvider pathProvider = m_multiFSPathProviderFactory
                            .getOrCreateFSPathProviderFactory(fsLocation).create(fsLocation)) {
                            final var fsPath = pathProvider.getPath();
                            var wfTempFolder = WorkflowReaderNodeModel.toLocalWorkflowDir(fsPath, null);
                            var wfm = WorkflowReaderNodeModel.readWorkflow(wfTempFolder.getTempFileOrFolder().toFile(),
                                exec, createMessageBuilder());
                            var wsInputs = new ArrayList<WorkflowSegment.Input>();
                            var wsOutputs = new ArrayList<WorkflowSegment.Output>();
                            var toolInputs = new ArrayList<ToolPort>();
                            var toolOutputs = new ArrayList<ToolPort>();
                            removeAndCollectContainerInputsAndOutputs(wfm, wsInputs, wsOutputs,
                                toolInputs, toolOutputs);
                            var ws = new WorkflowSegment(wfm, wsInputs, wsOutputs, Set.of());

                            // extract parameter-schema from config nodes
                            var configNodes = wfm.getConfigurationNodes(false);
                            var paramSchema = JsonUtil.getProvider().createObjectBuilder();
                            for (var configNodeEntry : configNodes.entrySet()) {
                                var paramName = configNodeEntry.getKey();
                                var dialogNode = configNodeEntry.getValue();
                                var value = dialogNode.getDefaultValue().toJson();
                                var valueWithDescription =
                                    JsonUtil.getProvider().createObjectBuilder((JsonObject)value);
                                valueWithDescription.add("description",
                                    ((SubNodeDescriptionProvider)dialogNode.getDialogRepresentation())
                                        .getDescription());
                                paramSchema.add(paramName, valueWithDescription);
                            }

                            try {
                                return new DataCell[]{
                                    new WorkflowToolCell(wfm.getName(), wfm.getMetadata().getDescription().orElse(""),
                                        paramSchema.build().toString(), toolInputs.toArray(ToolPort[]::new),
                                        toolOutputs.toArray(ToolPort[]::new), ws)};
                            } finally {
                                ws.serializeAndDisposeWorkflow();
                                wfTempFolder.close();
                            }
                        } catch (IOException | InvalidSettingsException | CanceledExecutionException
                                | UnsupportedWorkflowVersionException | LockFailedException | KNIMEException e) {
                            // TODO Auto-generated catch block
                            throw new RuntimeException(e);
                        }
                    }

                    private static void removeAndCollectContainerInputsAndOutputs(final WorkflowManager wfm,
                        final List<WorkflowSegment.Input> wsInputs, final List<WorkflowSegment.Output> wsOutputs,
                        final List<ToolPort> toolInputs, final List<ToolPort> toolOutputs) {
                        List<NodeID> nodesToRemove = new ArrayList<>();
                        var messageOutput = new AtomicReference<WorkflowSegment.Output>();
                        for (NodeContainer nc : wfm.getNodeContainers()) {
                            if (nc instanceof NativeNodeContainer nnc && (collectInputs(wfm, wsInputs, toolInputs, nnc)
                                || collectOutputs(wfm, wsOutputs, toolOutputs, nnc))) {
                                nodesToRemove.add(nnc.getID());
                            }
                        }
                        nodesToRemove.forEach(wfm::removeNode);

                    }

                    private static boolean collectOutputs(final WorkflowManager wfm,
                        final List<WorkflowSegment.Output> wsOutputs, final List<ToolPort> toolOutputs,
                        final NativeNodeContainer nnc) {
                        if (nnc.getNodeModel() instanceof OutputNode outputNode) {
                            var outputData = outputNode.getExternalOutput();
                            for (ConnectionContainer cc : wfm.getIncomingConnectionsFor(nnc.getID())) {
                                var outPort = wfm.getNodeContainer(cc.getSource()).getOutPort(cc.getSourcePort());
                                var outputId = outputData.getID();
                                // TODO hack
                                if (outputId.startsWith("message")) {
                                    wsOutputs.add(new Output(outPort.getPortType(), new DataTableSpec("message output"),
                                        new PortID(NodeIDSuffix.create(wfm.getID(), cc.getSource()),
                                            cc.getSourcePort())));
                                } else {
                                    wsOutputs.add(new Output(outPort.getPortType(), null, new PortID(
                                        NodeIDSuffix.create(wfm.getID(), cc.getSource()), cc.getSourcePort())));
                                    toolOutputs.add(new ToolPort(outPort.getPortType().getName(), outputId,
                                        outputData.getDescription().orElse(null),
                                        specToString(outPort.getPortObjectSpec())));
                                }
                            }
                            return true;
                        } else {
                            return false;
                        }
                    }

                    private static boolean collectInputs(final WorkflowManager wfm,
                        final List<WorkflowSegment.Input> wsInputs, final List<ToolPort> toolInputs,
                        final NativeNodeContainer nnc) {
                        if (nnc.getNodeModel() instanceof InputNode inputNode) {
                            var inputData = inputNode.getInputData();
                            for (var i = 0; i < nnc.getNrOutPorts(); i++) {
                                Set<PortID> ports = wfm.getOutgoingConnectionsFor(nnc.getID(), i).stream().map(
                                    cc -> new PortID(NodeIDSuffix.create(wfm.getID(), cc.getDest()), cc.getDestPort()))
                                    .collect(Collectors.toSet());
                                if (!ports.isEmpty()) {
                                    var outPort = nnc.getOutPort(i);
                                    wsInputs.add(new Input(outPort.getPortType(), null, ports));
                                    toolInputs.add(new ToolPort(outPort.getPortType().getName(), inputData.getID(),
                                        inputData.getDescription().orElse(null),
                                        specToString(outPort.getPortObjectSpec())));
                                }
                            }
                            return true;
                        } else {
                            return false;
                        }
                    }

                    private static final String specToString(final PortObjectSpec spec) {
                        if (spec instanceof DataTableSpec tableSpec) {
                            var map = Map.of( //
                                "name", tableSpec.getName(), //
                                "columns", tableSpec.stream().map(colSpec -> Map.of( //
                                    "name", colSpec.getName(), //
                                    "type", colSpec.getType().getName())).toList() //
                            );
                            return JsonUtil.getProvider().createObjectBuilder(map).build().toString();
                        } else {
                            return null;
                        }
                    }

                    @Override
                    public DataColumnSpec[] getColumnSpecs() {
                        return new DataColumnSpec[]{
                            new DataColumnSpecCreator("Tools", WorkflowToolCell.TYPE).createSpec()};
                    }

                    @Override
                    public void setProgress(final int curRowNr, final int rowCount, final RowKey lastKey,
                        final ExecutionMonitor exec) {
                        // TODO Auto-generated method stub
                    }

                }, 0);
                return rearranger;
            }

        };

    }

    private static class Worklfows2ToolsNodeSettings implements DefaultNodeSettings {

        @Widget(title = "Workflow paths", description = "TODO")
        @ChoicesProvider(PathColumnChoice.class)
        String m_columnName;

        static class PathColumnChoice implements ColumnChoicesProvider {

            /**
             * {@inheritDoc}
             */
            @Override
            public List<DataColumnSpec> columnChoices(final DefaultNodeSettingsContext context) {
                return context.getDataTableSpec(0)
                    .map(tableSpec -> tableSpec.stream()
                        .filter(colSpec -> colSpec.getType().isCompatible(FSLocationValue.class)).toList())
                    .orElse(List.of());
            }

        }

    }

}
