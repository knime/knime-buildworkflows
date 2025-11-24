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
 * ------------------------------------------------------------------------
 */

package org.knime.buildworkflows.executor;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.context.ports.ExtendablePortGroup;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.node.workflow.capture.WorkflowSegment.Input;
import org.knime.core.node.workflow.capture.WorkflowSegment.Output;
import org.knime.core.webui.node.dialog.NodeDialog.OnApplyNodeModifier;
import org.knime.core.webui.node.dialog.defaultdialog.internal.button.ButtonActionHandler;
import org.knime.core.webui.node.dialog.defaultdialog.internal.button.ButtonChange;
import org.knime.core.webui.node.dialog.defaultdialog.internal.button.ButtonState;
import org.knime.core.webui.node.dialog.defaultdialog.internal.button.ButtonWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.button.IncrementAndApplyOnClick;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.message.TextMessage.SimpleTextMessageProvider;

/**
 * Node parameters for Workflow Executor.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
class WorkflowExecutorNodeParameters implements NodeParameters {

    static class UpdatePortsOnApplyModifier implements OnApplyNodeModifier {

        @Override
        public void onApply(final NativeNodeContainer nnc, final NodeSettingsRO previousModelSettings,
            final NodeSettingsRO updatedModelSettings, final NodeSettingsRO previousViewSettings,
            final NodeSettingsRO updatedViewSettings) {

            var previous = previousModelSettings.getInt("updatePorts", -1);
            var updated = updatedModelSettings.getInt("updatePorts", -1);
            if (updated <= previous) {
                // 'update ports' button was not clicked
                return;
            }

            var wfm = nnc.getParent();
            var cc = wfm.getIncomingConnectionFor(nnc.getID(), 1);
            if (cc == null) {
                return;
            }
            var spec = (WorkflowPortObjectSpec)wfm.getNodeContainer(cc.getSource()).getOutPort(cc.getSourcePort())
                .getPortObjectSpec();
            var nodeCreationConfig = nnc.getNode().getCopyOfCreationConfig().orElse(null);
            if (nodeCreationConfig == null) {
                return;
            }

            ExtendablePortGroup inputConfig = (ExtendablePortGroup)nodeCreationConfig.getPortConfig().get()
                .getGroup(WorkflowExecutorNodeFactory.INPUT_PORT_GROUP);
            while (inputConfig.hasConfiguredPorts()) {
                inputConfig.removeLastPort();
            }
            for (Input input : spec.getInputs().values()) {
                //make sure it's not an optional port
                PortType type = PortTypeRegistry.getInstance().getPortType(input.getType().get().getPortObjectClass());
                inputConfig.addPort(type);
            }

            ExtendablePortGroup outputConfig = (ExtendablePortGroup)nodeCreationConfig.getPortConfig().get()
                .getGroup(WorkflowExecutorNodeFactory.OUTPUT_PORT_GROUP);
            while (outputConfig.hasConfiguredPorts()) {
                outputConfig.removeLastPort();
            }
            for (Output output : spec.getOutputs().values()) {
                outputConfig.addPort(output.getType().get());
            }

            wfm.replaceNode(nnc.getID(), nodeCreationConfig);
        }

    }

    @TextMessage(NoWorkflowMessage.class)
    Void m_noWorkflowMessage;

    @TextMessage(PortsRequireUpdateMessage.class)
    Void m_portsRequireUpdateMessage;

    @Widget(title = "Update Ports",
        description = "Updates the input and output ports of this node to match the connected workflow segment. "
            + "Use this button if the connected workflow segment has changed its ports and the node shows a port "
            + "mismatch error.")
    @ButtonWidget(actionHandler = UpdatePortsButtonActionHandler.class)
    @IncrementAndApplyOnClick
    Integer m_updatePorts = -1;

    @Widget(title = "Update links of components and metanodes",
        description = "If enabled, linked components and metanodes contained in the given workflow segment "
            + "will be updated before execution.")
    @Persist(configKey = "do_update_template_links")
    boolean m_doUpdateTemplateLinks = false;

    @Widget(title = "Execute entire workflow",
        description = "If checked, the entire workflow is executed and failures in the workflow will cause "
            + "this executor node to fail. If unchecked, the executor node will only execute the nodes "
            + "required to generate the output. This can lead to unexpected behavior where unconnected "
            + "side branches are not executed. This option is disabled for workflows older than version "
            + "5.5 due to backwards compatibility. It is enabled by default for newly created instances. "
            + "If uncertain, keep this option checked.",
        advanced = true)
    @Persist(configKey = "do_execute_entire_workflow")
    boolean m_doExecuteAllNodes = true;

    @Widget(title = "Show executing workflow segment (debugging)",
        description = "If enabled, the executing workflow will be visible as part of a metanode next to this node. "
            + "By stepping into the metanode the ongoing execution can be inspected. If the workflow fails "
            + "to execute and this option is enabled, the metanode will remain in order to identify the problem. "
            + "If disabled, the actual executing workflow will not be visible.",
        advanced = true)
    @Persist(configKey = WorkflowExecutorNodeModel.CFG_DEBUG)
    boolean m_debug = false;

    static class NoWorkflowMessage implements SimpleTextMessageProvider {

        @Override
        public boolean showMessage(final NodeParametersInput context) {
            return context.getInPortSpec(0).map(WorkflowPortObjectSpec.class::cast).isEmpty();
        }

        @Override
        public String title() {
            return "No workflow connected";
        }

        @Override
        public String description() {
            return "Connect the workflow input port.";
        }

        @Override
        public MessageType type() {
            return MessageType.INFO;
        }

    }

    static class PortsRequireUpdateMessage implements SimpleTextMessageProvider {

        @Override
        public boolean showMessage(final NodeParametersInput context) {
            var spec = context.getInPortSpec(0).map(WorkflowPortObjectSpec.class::cast).orElse(null);
            if (spec == null) {
                return false;
            }
            try {
                WorkflowExecutorNodeModel.checkPortCompatibility(spec, NodeContext.getContext().getNodeContainer());
                return false;
            } catch (InvalidSettingsException e) {
                return true;
            }
        }

        @Override
        public String title() {
            return "Ports need to be updated";
        }

        @Override
        public String description() {
            return "The node ports do not match the connected workflow segment's ports. "
                + "Click the 'Update Ports' button to update the ports of this node.";
        }

        @Override
        public MessageType type() {
            return MessageType.INFO;
        }

    }

    enum UpdatePortsButtonState {
            @ButtonState(text = "Update Ports", disabled = true)
            DISABLED,

            @ButtonState(text = "Update Ports", disabled = false)
            ENABLED
    }

    static class UpdatePortsButtonActionHandler
        implements ButtonActionHandler<Integer, WorkflowExecutorNodeParameters, UpdatePortsButtonState> {

        @Override
        public ButtonChange<Integer, UpdatePortsButtonState> initialize(final Integer currentValue,
            final NodeParametersInput context) {
            var spec = context.getInPortSpec(0).map(WorkflowPortObjectSpec.class::cast).orElse(null);
            UpdatePortsButtonState state;
            if (spec == null) {
                state = UpdatePortsButtonState.DISABLED;
            } else {
                try {
                    WorkflowExecutorNodeModel.checkPortCompatibility(spec, NodeContext.getContext().getNodeContainer());
                    state = UpdatePortsButtonState.DISABLED;
                } catch (InvalidSettingsException e) {
                    state = UpdatePortsButtonState.ENABLED;
                }
            }
            return new ButtonChange<>(currentValue, state);
        }

        @Override
        public ButtonChange<Integer, UpdatePortsButtonState> invoke(final UpdatePortsButtonState state,
            final WorkflowExecutorNodeParameters settings, final NodeParametersInput context) {
            throw new UnsupportedOperationException("Must not be called because of IncrementAndApplyOnClick.");
        }

        @Override
        public Class<UpdatePortsButtonState> getStateMachine() {
            return UpdatePortsButtonState.class;
        }

    }

}
