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

import static org.knime.node.impl.description.PortDescription.dynamicPort;
import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.knime.buildworkflows.executor.WorkflowExecutorNodeParameters.UpdatePortsOnApplyModifier;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.util.EclipseUtil;
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
 * The workflow executor node to execute workflow segments 'in place', i.e. within the workflow where the segment have
 * been captured.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public class WorkflowExecutorNodeFactory extends ConfigurableNodeFactory<WorkflowExecutorNodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    static final String INPUT_PORT_GROUP = "Inputs";

    static final String OUTPUT_PORT_GROUP = "Outputs";

    private static final String NODE_NAME = "Workflow Executor";

    private static final String NODE_ICON = "./workflow_executor.png";

    private static final String SHORT_DESCRIPTION = """
            Executes a workflow as part of this workflow. The workflow to be executed is provided at the workflow
                input port.
            """;

    private static final String FULL_DESCRIPTION = """
            Executes a workflow provided at the workflow input port as part of this workflow.
            """;

    private static final List<PortDescription> INPUT_PORTS = List.of(fixedPort("Workflow", """
            The workflow segment to be executed.
            """), dynamicPort("Inputs", "Workflow inputs", """
            The inputs for the execution. Number and types of the ports must exactly match the inputs of the
            workflow provided at the workflow port.
            """));

    private static final List<PortDescription> OUTPUT_PORTS = List.of(dynamicPort("Outputs", "Workflow outputs", """
            The outputs of the execution. Number and types of the ports must exactly match the outputs of the
            workflow provided at the workflow port.
            """));

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final PortsConfigurationBuilder b = new PortsConfigurationBuilder();
        b.addFixedInputPortGroup("Workflow", WorkflowPortObject.TYPE);
        // The ports in these groups are controlled by the dialog when configuring the workflow to execute
        // to keep them in sync with the workflow to execute
        b.addNonInteractiveExtendableInputPortGroup(INPUT_PORT_GROUP, p -> true);
        b.addNonInteractiveExtendableOutputPortGroup(OUTPUT_PORT_GROUP, p -> true);
        return Optional.of(b);
    }

    @Override
    protected WorkflowExecutorNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new WorkflowExecutorNodeModel(getPortConfig(creationConfig));
    }

    private static PortsConfiguration getPortConfig(final NodeCreationConfiguration creationConfig) {
        final Optional<? extends PortsConfiguration> portConfig = creationConfig.getPortConfig();
        assert portConfig.isPresent();
        return portConfig.get();
    }

    @Override
    public boolean isPortConfigurableViaMenu() {
        // this is now redundant, since we made the extendable port groups non-interactive.
        return false;
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<WorkflowExecutorNodeModel> createNodeView(final int viewIndex,
        final WorkflowExecutorNodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        if (showWebUIDialog()) {
            return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
        } else {
            return new WorkflowExecutorNodeDialogPane();
        }

    }

    @Override
    public boolean hasNodeDialog() {
        return showWebUIDialog();
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, WorkflowExecutorNodeParameters.class,
            new UpdatePortsOnApplyModifier());
    }

    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription(NODE_NAME, NODE_ICON, INPUT_PORTS, OUTPUT_PORTS,
            SHORT_DESCRIPTION, FULL_DESCRIPTION, List.of(), WorkflowExecutorNodeParameters.class, null,
            NodeType.Manipulator, List.of(), null);
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, WorkflowExecutorNodeParameters.class));
    }

    private static boolean showWebUIDialog() {
        return EclipseUtil.currentUIPerspective().map(p -> p.equals("modern")).orElse(true);
    }

}
