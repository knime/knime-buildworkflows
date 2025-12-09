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
package org.knime.buildworkflows.capture.end;

import static org.knime.node.impl.description.PortDescription.dynamicPort;
import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;

/**
 * The node factory for the Capture Workflow End node.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public final class CaptureWorkflowEndNodeFactory extends ConfigurableNodeFactory<CaptureWorkflowEndNodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final PortsConfigurationBuilder b = new PortsConfigurationBuilder();
        b.addExtendablePortGroup("Captured workflow outputs", p -> true);
        b.addFixedOutputPortGroup("Workflow", WorkflowPortObject.TYPE);
        return Optional.of(b);
    }

    @Override
    protected CaptureWorkflowEndNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new CaptureWorkflowEndNodeModel(getPortConfig(creationConfig));
    }

    private static PortsConfiguration getPortConfig(final NodeCreationConfiguration creationConfig) {
        final Optional<? extends PortsConfiguration> portConfig = creationConfig.getPortConfig();
        assert portConfig.isPresent();
        return portConfig.get();
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<CaptureWorkflowEndNodeModel> createNodeView(final int viewIndex,
        final CaptureWorkflowEndNodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }
    private static final String NODE_NAME = "Capture Workflow End";

    private static final String NODE_ICON = "./capture_end.png";

    private static final String SHORT_DESCRIPTION = """
            Marks the end of a to be captured workflow segment.
            """;

    private static final String FULL_DESCRIPTION = """
            Marks the end of a to be captured workflow segment. The entire workflow within the scope of a 'Capture
                Workfow Start'- and 'Capture Workflow End'-node is captured and available at the workflow output port of
                this node. Nodes that have out-going connections to a node that is part of the scope but aren't part of
                the scope themselves are represented as static inputs (but not captured).
            """;

    private static final List<PortDescription> INPUT_PORTS = List.of(
            dynamicPort("Captured workflow outputs", "Captured workflow segment outputs", """
                Outputs of the captured workflow segment.
                """)
    );

    private static final List<PortDescription> OUTPUT_PORTS = List.of(
            fixedPort("Captured Workflow Port Object", """
                A workflow port object that represents the captured workflow.
                """),
            dynamicPort("Captured workflow outputs", "Outputs of captured workflow", """
                The outputs of the captured workflow group.
                """)
    );

    /**
     * {@inheritDoc}
     * @since 5.10
     */
    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, CaptureWorkflowEndNodeParameters.class);
    }

    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription(
            NODE_NAME,
            NODE_ICON,
            INPUT_PORTS,
            OUTPUT_PORTS,
            SHORT_DESCRIPTION,
            FULL_DESCRIPTION,
            List.of(),
            CaptureWorkflowEndNodeParameters.class,
            null,
            NodeType.ScopeEnd,
            List.of(),
            null
        );
    }

    /**
     * {@inheritDoc}
     * @since 5.10
     */
    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, CaptureWorkflowEndNodeParameters.class));
    }

}
