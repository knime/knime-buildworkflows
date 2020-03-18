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

import static org.knime.buildworkflows.executor.WorkflowExecutorNodeModel.CFG_DEBUG;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.ConfigurableNodeFactory.ConfigurableNodeDialog;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.ModifiableNodeCreationConfiguration;
import org.knime.core.node.context.ports.ExtendablePortGroup;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.capture.WorkflowFragment.Input;
import org.knime.core.node.workflow.capture.WorkflowFragment.Output;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;

/**
 * Workflow executor's dialog.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
class WorkflowExecutorNodeDialogPane extends NodeDialogPane implements ConfigurableNodeDialog {

    private ModifiableNodeCreationConfiguration m_nodeCreationConfig;

    private WorkflowPortObjectSpec m_workflowSpec;

    private boolean m_portConfigChanged = false;

    private final JButton m_button;

    private final JLabel m_portAdjustmentLabel;

    private final JCheckBox m_debug;

    WorkflowExecutorNodeDialogPane() {
        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        addTab("Options", options);
        JPanel ports = new JPanel(new BorderLayout());
        ports.setMinimumSize(new Dimension(options.getWidth(), 0));
        setBorder(ports, "Inputs & outputs adjustment");
        m_button = new JButton("Auto-adjust ports (carried out on apply)");
        m_portAdjustmentLabel = new JLabel("");
        JPanel tmp = new JPanel();
        tmp.add(m_portAdjustmentLabel);
        ports.add(tmp, BorderLayout.NORTH);
        tmp = new JPanel();
        tmp.add(m_button);
        ports.add(tmp, BorderLayout.SOUTH);
        m_button.addActionListener(l -> {
            m_portConfigChanged = true;
            m_button.setEnabled(false);
            adoptNodeCreationConfig();
        });
        options.add(ports);

        JPanel debug = new JPanel();
        setBorder(debug, "Debugging");
        m_debug = new JCheckBox("Show executing workflow fragment", false);
        debug.add(m_debug);
        options.add(debug);
    }

    private static void setBorder(final JPanel p, final String label) {
        p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), label));
    }

    private void adoptNodeCreationConfig() {
        if (m_workflowSpec != null && m_nodeCreationConfig != null) {
            ExtendablePortGroup inputConfig = (ExtendablePortGroup)m_nodeCreationConfig.getPortConfig().get()
                .getGroup(WorkflowExecutorNodeFactory.INPUT_PORT_GROUP);
            while (inputConfig.hasConfiguredPorts()) {
                inputConfig.removeLastPort();
            }
            for (Input input : m_workflowSpec.getInputs().values()) {
                //make sure it's not an optional port
                PortType type = PortTypeRegistry.getInstance().getPortType(input.getType().get().getPortObjectClass());
                inputConfig.addPort(type);
            }

            ExtendablePortGroup outputConfig = (ExtendablePortGroup)m_nodeCreationConfig.getPortConfig().get()
                .getGroup(WorkflowExecutorNodeFactory.OUTPUT_PORT_GROUP);
            while (outputConfig.hasConfiguredPorts()) {
                outputConfig.removeLastPort();
            }
            for (Output output : m_workflowSpec.getOutputs().values()) {
                outputConfig.addPort(output.getType().get());
            }

            m_portConfigChanged = true;
        }
    }

    private void refreshPortAdjustment(final WorkflowPortObjectSpec spec) {
        NodeContainer nc = NodeContext.getContext().getNodeContainer();
        if (nc == null) {
            m_portAdjustmentLabel.setText("Not a local workflow");
            m_button.setEnabled(false);
        }
        try {
            WorkflowExecutorNodeModel.checkPortCompatibility(spec, nc);
            m_portAdjustmentLabel.setText("Node ports match the workflow inputs and outputs.");
            m_button.setEnabled(false);
        } catch (InvalidSettingsException e) {
            String inputs = spec.getWorkflowFragment().getConnectedInputs().stream()
                .map(i -> i.getType().get().getName()).collect(Collectors.joining(", "));
            String outputs = spec.getWorkflowFragment().getConnectedOutputs().stream()
                .map(i -> i.getType().get().getName()).collect(Collectors.joining(", "));
            String txt = "<html>The node ports are not in line with the workflow inputs and outputs."
                + "<br/>Expected inputs: "
                + inputs
                + "<br/>Expected outputs: "
                + outputs
                + "</html>";
            m_portAdjustmentLabel.setText(txt);
            m_button.setEnabled(true);
        }
    }

    @Override
    public Optional<ModifiableNodeCreationConfiguration> getNewNodeCreationConfiguration() {
        return Optional.ofNullable(m_portConfigChanged ? m_nodeCreationConfig : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentNodeCreationConfiguration(final ModifiableNodeCreationConfiguration nodeCreationConfig) {
        m_nodeCreationConfig = nodeCreationConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        m_workflowSpec = ((WorkflowPortObjectSpec)specs[0]);
        m_portConfigChanged = false;
        m_button.setEnabled(true);
        refreshPortAdjustment(m_workflowSpec);
        m_debug.setSelected(settings.getBoolean(CFG_DEBUG, false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        settings.addBoolean(CFG_DEBUG, m_debug.isSelected());
    }

}
