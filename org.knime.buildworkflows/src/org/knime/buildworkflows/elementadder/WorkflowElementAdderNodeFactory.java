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

import java.util.List;
import java.util.Set;

import org.knime.core.data.IntValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.extension.InvalidNodeFactoryExtensionException;
import org.knime.core.node.extension.NodeFactoryProvider;
import org.knime.core.node.extension.NodeSpecCollectionProvider;
import org.knime.core.node.workflow.AnnotationData;
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
            ports.addInputTable("Nodes", "TODO");
            ports.addOutputPort("Modified Workflow", "TODO", WorkflowPortObject.TYPE);
        }).model(model -> model.parametersClass(WorkflowElementAdderNodeFactory.Parameters.class) //
            .configure(WorkflowElementAdderNodeFactory::configure) //
            .execute(WorkflowElementAdderNodeFactory::execute));

    static class Parameters implements NodeParameters {

        @Widget(title = "Factory ID Column", description = "TODO")
        @ChoicesProvider(StringColumnsProvider.class)
        String m_factoryIdColumn;

        @Widget(title = "X Position Column", description = "TODO")
        @ChoicesProvider(IntColumnsProvider.class)
        String m_xPositionColumn;

        @Widget(title = "Y Position Column", description = "TODO")
        @ChoicesProvider(IntColumnsProvider.class)
        String m_yPositionColumn;

        @Widget(title = "Comment", description = "TODO")
        @ChoicesProvider(StringColumnsProvider.class)
        String m_commentColumn;

    }

    static void configure(final ConfigureInput in, final ConfigureOutput out) {
        out.setOutSpec(0, null);
    }

    static void execute(final ExecuteInput in, final ExecuteOutput out) {
        var nodes = in.getInTable(1);

        var params = in.<Parameters> getParameters();

        var factoryIdColumnIndex = nodes.getSpec().findColumnIndex(params.m_factoryIdColumn);
        var xPositionColumnIndex = nodes.getSpec().findColumnIndex(params.m_xPositionColumn);
        var yPositionColumnIndex = nodes.getSpec().findColumnIndex(params.m_yPositionColumn);
        var commentColumnIndex = nodes.getSpec().findColumnIndex(params.m_commentColumn);

        var wfm = ((WorkflowPortObject)in.getInPortObject(0)).getSpec().getWorkflowSegment().loadWorkflow();
        for (var row : nodes) {
            var factoryId = ((StringValue)row.getCell(factoryIdColumnIndex)).getStringValue();
            var xPosition = ((IntValue)row.getCell(xPositionColumnIndex)).getIntValue();
            var yPosition = ((IntValue)row.getCell(yPositionColumnIndex)).getIntValue();
            var factoryClassName =
                NodeSpecCollectionProvider.getInstance().getNodes().get(factoryId).factory().className();
            // TODO factory settings?
            try {
                var factory = NodeFactoryProvider.getInstance().getNodeFactory(factoryClassName).orElseThrow();
                var nodeId = wfm.createAndAddNode(factory);
                var nc = wfm.getNodeContainer(nodeId);
                nc.setUIInformation(NodeUIInformation.builder().setNodeLocation(xPosition, yPosition, 0, 0).build());
                if (commentColumnIndex >= 0) {
                    var comment = ((StringValue)row.getCell(commentColumnIndex)).getStringValue();
                    var data = new AnnotationData();
                    data.setText(comment);
                    nc.getNodeAnnotation().copyFrom(data, false);
                }

            } catch (InstantiationException | IllegalAccessException | InvalidNodeFactoryExtensionException e) {
                // TODO
                throw new RuntimeException(e);
            }

        }

        var segment = new WorkflowSegment(wfm, List.of(), List.of(), Set.of());
        out.setOutData(
            new WorkflowPortObject(new WorkflowPortObjectSpec(segment, "workflow name TODO", List.of(), List.of())));
    }

    static class IntColumnsProvider extends CompatibleColumnsProvider {

        protected IntColumnsProvider() {
            super(IntValue.class);
        }

        @Override
        public int getInputTableIndex() {
            return 1;
        }
    }

    static class StringColumnsProvider extends CompatibleColumnsProvider {

        protected StringColumnsProvider() {
            super(StringValue.class);
        }

        @Override
        public int getInputTableIndex() {
            return 1;
        }
    }

    /**
     * TODO
     */
    public WorkflowElementAdderNodeFactory() {
        super(NODE);
    }

}
