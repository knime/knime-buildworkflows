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
 *   Feb 14, 2020 (hornm): created
 */
package org.knime.buildworkflows.capture.end;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.knime.core.node.workflow.capture.WorkflowFragment.Port;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;

/**
 * Panel to optionally assign names to ports.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("serial")
class PortNamesPanel extends JPanel {

    private Map<PortID, JTextField> m_inputNames;

    private Map<PortID, JTextField> m_outputNames;

    PortNamesPanel(final List<Port> inputPorts, final List<Port> outputPorts, final Map<PortID, String> initInPortNames,
        final Map<PortID, String> initOutPortNames) {
        JPanel inputs = new JPanel();
        setBorder(inputs, "Input ports names");
        m_inputNames = fillPanel(inputs, inputPorts, initInPortNames);
        JPanel outputs = new JPanel();
        setBorder(outputs, "Output ports names");
        m_outputNames = fillPanel(outputs, outputPorts, initOutPortNames);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(inputs);
        add(outputs);
    }

    private static void setBorder(final JPanel p, final String title) {
        p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
    }

    private static Map<PortID, JTextField> fillPanel(final JPanel p, final List<Port> ports,
        final Map<PortID, String> initPortNames) {
        p.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = 1;
        Map<PortID, JTextField> res = new HashMap<>(ports.size());
        for (Port port : ports) {
            c.insets = new Insets(15, 0, 0, 0);
            c.gridy++;
            p.add(new JLabel(port.toString() + ":"), c);
            c.insets = new Insets(0, 0, 0, 0);
            JTextField text = new JTextField(12);
            if (initPortNames.containsKey(port.getID())) {
                text.setText(initPortNames.get(port.getID()));
            }
            c.gridy++;
            p.add(text, c);
            res.put(port.getID(), text);
        }
        return res;
    }

    Map<PortID, String> getInPortNames() {
        return m_inputNames.entrySet().stream().filter(e -> !e.getValue().getText().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getText()));
    }

    Map<PortID, String> getOutPortNames() {
        return m_outputNames.entrySet().stream().filter(e -> !e.getValue().getText().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getText()));
    }
}
