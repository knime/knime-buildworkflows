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
 *   Feb 3, 2020 (hornm): created
 */
package org.knime.buildworkflows.writer;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.knime.core.data.DataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.DialogNode;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeUIInformation;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;

/**
 * Represents the (likely reduced) configuration of input and output nodes. Also provides the functionality to
 * programmatically add the pre-configured input/output nodes to a workflow.
 *
 * Possible future TODO: split into model, view, etc.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
abstract class IONodeConfig {

    // vertical distance between newly added input and output nodes
    private static final int NODE_DIST = 120;

    private static final int NODE_WIDTH = 124;

    private static final int NODE_HEIGHT = 83;

    /**
     * Helper to add, connect and configure input or output nodes.
     *
     * @param wfm the workflow to add the nodes to
     * @param inputs the inputs to connect
     * @param outputs the outputs to connect
     * @param idToConfigMap gives the node-config for a 'configured' input/output
     * @param in whether input or output nodes are to be added
     * @param wfBounds the workflow's bounding box
     * @throws InvalidSettingsException if the configuration failed
     */
    static void addConnectAndConfigureIONodes(final WorkflowManager wfm, final Collection<String> inputOrOutputIDs,
        final Function<String, Stream<PortID>> idToPortsMap,
        final Function<String, ? extends IONodeConfig> idToConfigMap,
        final Function<String, DataTable> idToInputDataMap, final boolean in, final int[] wfBounds)
        throws InvalidSettingsException {

        int numNodes = inputOrOutputIDs.size();
        int y_bb_center = (int)Math.round((wfBounds[3] - wfBounds[1]) / 2.0 + wfBounds[1]);
        int y_offset = (int)Math.floor(y_bb_center - ((numNodes - 1) * NODE_DIST) / 2.0 - NODE_HEIGHT / 2.0);
        int x_pos = (int)Math.round((in ? wfBounds[0] - NODE_DIST : wfBounds[2] + NODE_DIST) - NODE_WIDTH / 2.0);
        int i = 0;
        for (String id : inputOrOutputIDs) {
            idToConfigMap.apply(id).addConnectAndConfigureNode(wfm, idToPortsMap.apply(id), x_pos,
                //add and configure
                (int)Math.floor(y_offset + i * NODE_DIST), idToInputDataMap.apply(id));
            i++;
        }
    }

    private String m_paramName;

    private JTextField m_dlgParamName;

    private JPanel m_panel;

    /**
     * Gets the already created or creates a {@link JPanel} to configure the respective node.
     *
     * @return the panel
     */
    JPanel getOrCreateJPanel() {
        if (m_panel == null) {
            m_panel = new JPanel();
            m_panel.add(new JLabel("Paramter name"));
            m_dlgParamName = new JTextField(20);
            m_dlgParamName.setText("param");
            m_dlgParamName.setText(m_paramName);
            m_paramName = null;
            m_panel.add(m_dlgParamName);
        }
        return m_panel;
    }

    /**
     * @return the name of the node (will, e.g., appear in the drop-down list of the respective dialog component)
     */
    abstract String getNodeName();

    /**
     * Adds the respective node to the given workflow at the given position, connects and configures it.
     *
     * @param wfm the workflow to add to
     * @param ports the ports to connect to
     * @param x the x coordinate
     * @param y the y coordinate
     * @param inputData optional input data used to configure a node
     * @return the id of the new node
     * @throws InvalidSettingsException if the configuration failed
     */
    protected NodeID addConnectAndConfigureNode(final WorkflowManager wfm, final Stream<PortID> ports, final int x, final int y,
        final DataTable inputData) throws InvalidSettingsException {
        //add
        NodeID nodeID = wfm.createAndAddNode(createNodeFactory());
        NodeContainer nc = wfm.getNodeContainer(nodeID);
        nc.setUIInformation(NodeUIInformation.builder().setNodeLocation(x, y, -1, -1).build());

        //connect
        ports.forEach(p -> addConnection(wfm, p, nodeID));

        //config
        NodeSettings settings = new NodeSettings("root");
        wfm.saveNodeSettings(nodeID, settings);
        NodeSettingsWO modelSettings = settings.addNodeSettings("model");
        if (this instanceof InputNodeConfig) {
            ((InputNodeConfig)this).saveActualNodeSettingsTo(modelSettings, inputData);
        } else {
            saveActualNodeSettingsTo(modelSettings);
        }
        wfm.loadNodeSettings(nodeID, settings);

        return nodeID;
    }

    /**
     * @return the node factory instance of the represented node
     */
    protected abstract NodeFactory<? extends NodeModel> createNodeFactory();

    /**
     * Saves the configuration as node settings as required to pre-configure the respective node.
     *
     * @param settings the object to store the settings into
     *
     * @throws InvalidSettingsException if the configuration failed
     */
    protected abstract void saveActualNodeSettingsTo(NodeSettingsWO settings) throws InvalidSettingsException;

    /**
     * Connects the node to the given port.
     *
     * @param wfm the parent workflow
     * @param p the port to connect to/from
     * @param nodeID the node to connect
     */
    protected abstract void addConnection(WorkflowManager wfm, PortID p, NodeID nodeID);

    /**
     * All container input and output nodes have one configuration in common: the parameter name.
     *
     * @return the configured parameter name
     */
    protected String getParameterName() {
        if (m_dlgParamName != null) {
            return m_dlgParamName.getText();
        } else {
            return m_paramName;
        }
    }

    void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString("param_name", getParameterName());
    }

    void loadSettingsFrom(final NodeSettingsRO settings) {
        m_paramName = settings.getString("param_name", "");
    }

    void validateSettingsBeforeSave() throws InvalidSettingsException {
        String param = getParameterName();
        CheckUtils.checkSetting(StringUtils.isNotEmpty(param), "parameter name must not be null or empty");
        CheckUtils.checkSetting(DialogNode.PARAMETER_NAME_PATTERN.matcher(param).matches(),
            "Parameter doesn't match pattern - must start with character, followed by other characters, digits, "
                + "or single dashes or underscores:\n  Input: %s\n  Pattern: %s",
            param, DialogNode.PARAMETER_NAME_PATTERN.pattern());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof IONodeConfig)) {
            return false;
        }
        IONodeConfig other = (IONodeConfig)obj;
        return new EqualsBuilder().append(getNodeName(), other.getNodeName())
            .append(getParameterName(), other.getParameterName()).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(getNodeName()).append(getParameterName()).build();
    }
}