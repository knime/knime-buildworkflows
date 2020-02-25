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
 *   Feb 12, 2020 (hornm): created
 */
package org.knime.buildworkflows.combiner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.capture.WorkflowFragment.Port;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.util.Pair;

/**
 * Represents the connections between two workflows (i.e. connections between the output ports of the first and input
 * ports of the second workflow).
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
class ConnectionMap {

    private static final String CFG_NUM_CONNECTED_PORTS = "num_connected_ports";

    private static final String CFG_IN_PORT = "in_port_";

    private static final String CFG_OUT_PORT = "out_port_";

    private static final String CFG_PORT_INDEX = "index";

    private static final String CFG_NODE_ID = "node_id";

    private List<Pair<PortID, PortID>> m_connections = null;

    /**
     * A new connection map.
     *
     * @param connections connections represented as a list of pairs of ports
     */
    ConnectionMap(final List<Pair<PortID, PortID>> connections) {
        m_connections = connections;
    }

    /**
     * For deserialization or a simple pair-wise connected map.
     */
    ConnectionMap() {
        //
    }

    /**
     * Gets all configured connections for the given output and input ports.
     *
     * @param out the available output ports to get the connections for
     * @param in the available input ports to get the connections for
     * @return the connections represented as a list of pairs of out-ports connected to in-ports
     * @throws InvalidSettingsException if the given ports couldn't be connected appropriately
     */
    List<Pair<PortID, PortID>> getConnectionsFor(final List<Port> out, final List<Port> in)
        throws InvalidSettingsException {
        List<Pair<PortID, PortID>> res = new ArrayList<>();
        if (m_connections == null) {
            CheckUtils.checkSetting(out.size() == in.size(),
                "Can't pair-wise connect ports: The number of output and input ports differ");
            for (int i = 0; i < out.size(); i++) {
                CheckUtils.checkSetting(out.get(i).getType().get().equals(in.get(i).getType().get()),
                    "Can't pair-wise connect ports: types of some ports differ.");
            }
            for (int i = 0; i < out.size(); i++) {
                res.add(Pair.create(out.get(i).getID(), in.get(i).getID()));
            }
        } else {
            res = m_connections.stream().filter(c -> contains(c.getFirst(), out) && contains(c.getSecond(), in))
                .collect(Collectors.toList());
        }
        return res;
    }

    private static boolean contains(final PortID id, final List<Port> l) {
        return l.stream().anyMatch(p -> p.getID().equals(id));
    }

    /**
     * Gets the output port for a given input port as configured by this connection map.
     *
     * @param in the input port to get the output port for - must be contained in the 'ins'-list
     * @param outs list of all available output ports
     * @param ins the list of all available input ports
     * @return the output port or an empty optional if there is no mapping
     */
    Optional<PortID> getOutPortForInPort(final PortID in, final List<PortID> outs, final List<PortID> ins) {
        assert ins.contains(in);
        if (m_connections == null) {
            //default pair-wise connection
            int idx = ins.indexOf(in);
            if (idx == -1 || outs.size() <= idx) {
                return Optional.empty();
            } else {
                return Optional.of(outs.get(idx));
            }
        } else {
            return m_connections.stream().filter(p -> p.getSecond().equals(in))
                .filter(p -> outs.contains(p.getFirst()) && ins.contains(p.getSecond())).map(Pair::getFirst)
                .findFirst();
        }
    }

    /**
     * Saves this connection map to a settings object.
     *
     * @param settings
     */
    void save(final NodeSettingsWO settings) {
        if (m_connections == null) {
            settings.addInt(CFG_NUM_CONNECTED_PORTS, -1);
            return;
        }
        settings.addInt(CFG_NUM_CONNECTED_PORTS, m_connections.size());
        for (int i = 0; i < m_connections.size(); i++) {
            NodeSettingsWO portSettings = settings.addNodeSettings(CFG_IN_PORT + i);
            portSettings.addString(CFG_NODE_ID, m_connections.get(i).getSecond().getNodeIDSuffix().toString());
            portSettings.addInt(CFG_PORT_INDEX, m_connections.get(i).getSecond().getIndex());
            portSettings = settings.addNodeSettings(CFG_OUT_PORT + i);
            portSettings.addString(CFG_NODE_ID, m_connections.get(i).getFirst().getNodeIDSuffix().toString());
            portSettings.addInt(CFG_PORT_INDEX, m_connections.get(i).getFirst().getIndex());
        }
    }

    /**
     * Loads this connection map from a settings object.
     *
     * @param settings
     * @throws InvalidSettingsException
     */
    void load(final NodeSettingsRO settings) throws InvalidSettingsException {
        int numPorts = settings.getInt(CFG_NUM_CONNECTED_PORTS);
        if (numPorts == -1) {
            return;
        }
        m_connections = new ArrayList<>(numPorts);
        for (int i = 0; i < numPorts; i++) {
            NodeSettingsRO portSettings = settings.getNodeSettings(CFG_IN_PORT + i);
            PortID inPort = new PortID(NodeIDSuffix.fromString(portSettings.getString(CFG_NODE_ID)),
                portSettings.getInt(CFG_PORT_INDEX));
            portSettings = settings.getNodeSettings(CFG_OUT_PORT + i);
            PortID outPort = new PortID(NodeIDSuffix.fromString(portSettings.getString(CFG_NODE_ID)),
                portSettings.getInt(CFG_PORT_INDEX));
            m_connections.add(Pair.create(outPort, inPort));
        }
    }
}
