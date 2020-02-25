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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

import org.knime.core.node.workflow.capture.WorkflowFragment.Port;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.util.Pair;

/**
 * UI to choose/'draw' connections between the output and input ports of a workflow.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("serial")
class WorkflowConnectPanel extends JPanel {


    private final List<JComboBox<PortIDWithName>> m_selectedOutPorts;

    private final List<PortID> m_inPortsWF2;

    /**
     * Creates a new connection panel.
     *
     * @param outPortsWF1 the out ports of the first workflow (to be connected to the inports of the second workflow)
     * @param inPortsWF2 the in ports of the second workflow
     * @param connectionMap the original connection map to initialize the selected connections
     * @param title a custom title for the panel (titled border)
     * @param extraLabel a extra label appearing at the top - e.g. the names of the workflows to be connected
     * @param outPortNames optional custom names for the output ports
     * @param inPortNames optional custom names for the input ports
     */
    WorkflowConnectPanel(final List<Port> outPortsWF1, final List<Port> inPortsWF2, final ConnectionMap connectionMap,
        final String title, final String extraLabel, final Map<PortID, String> outPortNames,
        final Map<PortID, String> inPortNames) {
        m_inPortsWF2 = inPortsWF2.stream().map(Port::getID).collect(Collectors.toList());
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
        setLayout(new BorderLayout());
        JPanel selection = new JPanel(new GridLayout(inPortsWF2.size(), 1));
        m_selectedOutPorts = new ArrayList<>(inPortsWF2.size());
        for (int i = 0; i < inPortsWF2.size(); i++) {
            JPanel panel = new JPanel();
            panel.add(new JLabel("Connect ports: "));
            final int j = i;
            List<PortID> compatibleOutPorts =
                outPortsWF1.stream().filter(p -> p.getType().get().equals(inPortsWF2.get(j).getType().get()))
                    .map(Port::getID).collect(Collectors.toList());
            JComboBox<PortIDWithName> cbox = new JComboBox<>();
            cbox.addItem(PortIDWithName.NONE_SELECTION);
            compatibleOutPorts.forEach(p -> cbox.addItem(new PortIDWithName(p, outPortNames)));
            //set selected item
            Optional<PortID> selectedOutPort = connectionMap.getOutPortForInPort(inPortsWF2.get(i).getID(), compatibleOutPorts, m_inPortsWF2);
            if (selectedOutPort.isPresent()) {
                cbox.setSelectedItem(new PortIDWithName(selectedOutPort.get(), outPortNames));
            } else {
                cbox.setSelectedItem(PortIDWithName.NONE_SELECTION);
            }
            m_selectedOutPorts.add(cbox);
            panel.add(cbox);
            panel.add(new JLabel(" to " + new PortIDWithName(inPortsWF2.get(i).getID(), inPortNames)));
            selection.add(panel);
        }
        add(selection, BorderLayout.CENTER);
        JPanel extraLabelPanel = new JPanel(new BorderLayout());
        extraLabelPanel.setBorder(BorderFactory.createEtchedBorder(BevelBorder.LOWERED));
        JLabel extraJLabel = new JLabel(extraLabel, SwingConstants.CENTER);
        extraLabelPanel.add(extraJLabel, BorderLayout.CENTER);
        extraJLabel.setForeground(Color.GRAY);
        add(extraLabelPanel, BorderLayout.NORTH);
    }

    /**
     * @return creates a {@link ConnectionMap} from the user choice
     */
    ConnectionMap createConnectionMap() {
        List<Pair<PortID, PortID>> l = new ArrayList<>();
        for (int i = 0; i < m_inPortsWF2.size(); i++) {
            if (m_selectedOutPorts.get(i).getSelectedItem() != PortIDWithName.NONE_SELECTION) {
                l.add(Pair.create(((PortIDWithName)m_selectedOutPorts.get(i).getSelectedItem()).getID(),
                    m_inPortsWF2.get(i)));
            }
        }
        return new ConnectionMap(l);
    }
}
