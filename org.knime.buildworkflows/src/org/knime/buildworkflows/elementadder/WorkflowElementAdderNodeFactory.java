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
 *   Dec 22, 2025 (hornm): created
 */
package org.knime.buildworkflows.elementadder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.IntValue;
import org.knime.core.data.MissingCell;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeModel;
import org.knime.core.node.extension.InvalidNodeFactoryExtensionException;
import org.knime.core.node.extension.NodeFactoryProvider;
import org.knime.core.node.extension.NodeSpecCollectionProvider;
import org.knime.core.node.workflow.AnnotationData;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.NodeUIInformation;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.node.workflow.capture.WorkflowSegment;
import org.knime.node.DefaultModel.ConfigureInput;
import org.knime.node.DefaultModel.ConfigureOutput;
import org.knime.node.DefaultModel.ExecuteInput;
import org.knime.node.DefaultModel.ExecuteOutput;
import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.util.CompatibleColumnsProvider;

/**
 * TODO
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class WorkflowElementAdderNodeFactory extends DefaultNodeFactory {

    private static final DefaultNode NODE = DefaultNode.create().name("Workflow Element Adder") //
        .icon(null) //
        .shortDescription("TODO") //
        .fullDescription("TODO") //
        .sinceVersion(5, 10, 0) //
        .ports(ports -> {
            ports.addInputPort("Workflow", "TODO", WorkflowPortObject.TYPE);
            ports.addInputTable("Nodes to add/update", "TODO");
            ports.addInputTable("Connections to add", "TODO");
            ports.addOutputPort("Modified Workflow", "TODO", WorkflowPortObject.TYPE);
            ports.addOutputTable("Nodes", "TODO");
        }).model(model -> model.parametersClass(WorkflowElementAdderNodeFactory.Parameters.class) //
            .configure(WorkflowElementAdderNodeFactory::configure) //
            .execute(WorkflowElementAdderNodeFactory::execute));

    static class Parameters implements NodeParameters {

        @Section(title = "Nodes", description = "TODO")
        interface Nodes {

        }

        @Widget(title = "Factory- or node-ID column", description = "TODO")
        @ChoicesProvider(StringColumnsProvider1.class)
        @Layout(Nodes.class)
        String m_factoryOrNodeIdColumn;

        @Widget(title = "X position column", description = "TODO")
        @ChoicesProvider(IntColumnsProvider1.class)
        @Layout(Nodes.class)
        String m_xPositionColumn;

        @Widget(title = "Y position column", description = "TODO")
        @ChoicesProvider(IntColumnsProvider1.class)
        @Layout(Nodes.class)
        String m_yPositionColumn;

        @Widget(title = "Comment", description = "TODO")
        @ChoicesProvider(StringColumnsProvider1.class)
        @Layout(Nodes.class)
        String m_commentColumn;

        @Section(title = "Connections", description = "TODO")
        @After(Nodes.class)
        interface Connections {

        }

        @Widget(title = "Source node ID column", description = "TODO")
        @ChoicesProvider(StringColumnsProvider2.class)
        @Layout(Connections.class)
        String m_sourceNodeIdColumn;

        @Widget(title = "Source port index column", description = "TODO")
        @ChoicesProvider(IntColumnsProvider2.class)
        @Layout(Connections.class)
        String m_sourcePortIndexColumn;

        @Widget(title = "Destination node ID column", description = "TODO")
        @ChoicesProvider(StringColumnsProvider2.class)
        @Layout(Connections.class)
        String m_destinationNodeIdColumn;

        @Widget(title = "Destination port index column", description = "TODO")
        @ChoicesProvider(IntColumnsProvider2.class)
        @Layout(Connections.class)
        String m_destinationPortIndexColumn;

    }

    static void configure(final ConfigureInput in, final ConfigureOutput out) {
        out.setOutSpecs(null, nodesTableSpec());
    }

    static DataTableSpec nodesTableSpec() {
        return new DataTableSpec(new String[]{"factory id", "x position", "y position", "comment"},
            new DataType[]{StringCell.TYPE, IntCell.TYPE, IntCell.TYPE, StringCell.TYPE});
    }

    static void execute(final ExecuteInput in, final ExecuteOutput out) {
        var params = in.<Parameters> getParameters();

        var nodes = in.getInTable(1);
        var factoryOrNodeIdColumnIndex = nodes.getSpec().findColumnIndex(params.m_factoryOrNodeIdColumn);
        var xPositionColumnIndex = nodes.getSpec().findColumnIndex(params.m_xPositionColumn);
        var yPositionColumnIndex = nodes.getSpec().findColumnIndex(params.m_yPositionColumn);
        var commentColumnIndex = nodes.getSpec().findColumnIndex(params.m_commentColumn);

        var wfm = ((WorkflowPortObject)in.getInPortObject(0)).getSpec().getWorkflowSegment().loadWorkflow();
        if (factoryOrNodeIdColumnIndex >= 0) {
            for (var nodeRow : nodes) {
                var factoryOrNodeId = ((StringValue)nodeRow.getCell(factoryOrNodeIdColumnIndex)).getStringValue();
                NodeContainer nc = null;
                try {
                    var nodeId = NodeIDSuffix.fromString(factoryOrNodeId);
                    nc = wfm.getNodeContainer(nodeId.prependParent(wfm.getID()));
                } catch (IllegalArgumentException e) {
                    // not a node id, continue
                }

                if (nc == null) {
                    var factoryClassName =
                        NodeSpecCollectionProvider.getInstance().getNodes().get(factoryOrNodeId).factory().className();
                    NodeFactory<NodeModel> factory;
                    try {
                        factory = NodeFactoryProvider.getInstance().getNodeFactory(factoryClassName).orElseThrow();
                        // TODO factory settings?
                    } catch (InstantiationException | IllegalAccessException | InvalidNodeFactoryExtensionException e) {
                        // TODO
                        throw new RuntimeException(e);
                    }
                    var nodeId = wfm.createAndAddNode(factory);
                    nc = wfm.getNodeContainer(nodeId);
                }

                if (xPositionColumnIndex >= 0 || yPositionColumnIndex >= 0) {
                    var xPosition = ((IntValue)nodeRow.getCell(xPositionColumnIndex)).getIntValue();
                    var yPosition = ((IntValue)nodeRow.getCell(yPositionColumnIndex)).getIntValue();
                    nc.setUIInformation(
                        NodeUIInformation.builder().setNodeLocation(xPosition, yPosition, 0, 0).build());
                }
                if (commentColumnIndex >= 0) {
                    var comment = ((StringValue)nodeRow.getCell(commentColumnIndex)).getStringValue();
                    var data = new AnnotationData();
                    data.setText(comment);
                    nc.getNodeAnnotation().copyFrom(data, false);
                }
            }
        }

        var connections = in.getInTable(2);
        var sourceNodeIdColumnIndex = connections.getSpec().findColumnIndex(params.m_sourceNodeIdColumn);
        var sourcePortIndexColumnIndex = connections.getSpec().findColumnIndex(params.m_sourcePortIndexColumn);
        var destinationNodeIdColumnIndex = connections.getSpec().findColumnIndex(params.m_destinationNodeIdColumn);
        var destinationPortIndexColumnIndex =
            connections.getSpec().findColumnIndex(params.m_destinationPortIndexColumn);
        if (sourceNodeIdColumnIndex >= 0 && sourcePortIndexColumnIndex >= 0 && destinationNodeIdColumnIndex >= 0
            && destinationPortIndexColumnIndex >= 0) {
            for (var connectionRow : connections) {
                try {
                    var sourceNodeId = NodeIDSuffix
                        .fromString(((StringValue)connectionRow.getCell(sourceNodeIdColumnIndex)).getStringValue())
                        .prependParent(wfm.getID());
                    var sourcePortIndex = ((IntValue)connectionRow.getCell(sourcePortIndexColumnIndex)).getIntValue();
                    var destinationNodeId = NodeIDSuffix
                        .fromString(((StringValue)connectionRow.getCell(destinationNodeIdColumnIndex)).getStringValue())
                        .prependParent(wfm.getID());
                    var destinationPortIndex =
                        ((IntValue)connectionRow.getCell(destinationPortIndexColumnIndex)).getIntValue();
                    wfm.addConnection(sourceNodeId, sourcePortIndex, destinationNodeId, destinationPortIndex);
                } catch (Exception e) {
                    // TODO
                    out.setWarningMessage("Some connections could not be added");
                }
            }
        }

        var container = in.getExecutionContext().createDataContainer(nodesTableSpec());
        for (var nc : wfm.getNodeContainers()) {
            var factory =
                nc instanceof NativeNodeContainer nnc ? new StringCell(nnc.getNode().getFactory().getClass().getName())
                    : new MissingCell("not a native node");
            var bounds = nc.getUIInformation().getBounds();
            var row = new DefaultRow(new RowKey(NodeIDSuffix.create(wfm.getID(), nc.getID()).toString()), factory,
                new IntCell(bounds[0]), new IntCell(bounds[1]), new StringCell(nc.getNodeAnnotation().getText()));
            container.addRowToTable(row);
        }
        container.close();

        var segment = new WorkflowSegment(wfm, List.of(), List.of(), Set.of());
        out.setOutData(
            new WorkflowPortObject(new WorkflowPortObjectSpec(segment, "workflow name TODO", List.of(), List.of())),
            container.getTable());
        try {
            segment.serializeAndDisposeWorkflow();
        } catch (IOException e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    static class IntColumnsProvider1 extends CompatibleColumnsProvider {

        protected IntColumnsProvider1() {
            super(IntValue.class);
        }

        @Override
        public int getInputTableIndex() {
            return 1;
        }
    }

    static class StringColumnsProvider1 extends CompatibleColumnsProvider {

        protected StringColumnsProvider1() {
            super(StringValue.class);
        }

        @Override
        public int getInputTableIndex() {
            return 1;
        }
    }

    static class IntColumnsProvider2 extends CompatibleColumnsProvider {

        protected IntColumnsProvider2() {
            super(IntValue.class);
        }

        @Override
        public int getInputTableIndex() {
            return 2;
        }
    }

    static class StringColumnsProvider2 extends CompatibleColumnsProvider {

        protected StringColumnsProvider2() {
            super(StringValue.class);
        }

        @Override
        public int getInputTableIndex() {
            return 2;
        }
    }

    /**
     * TODO
     */
    public WorkflowElementAdderNodeFactory() {
        super(NODE);
    }

}
