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
 *   Feb 18, 2020 (hornm): created
 */
package org.knime.buildworkflows.combiner;

import java.util.Map;

import javax.swing.JComboBox;

import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;

/**
 * Helper class to represent a {@link PortID} with an optional name, e.g., to be used in dialog's {@link JComboBox}es.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public final class PortIDWithName {

    /**
     * Represents the none-selection, i.e. not port selected.
     */
    public static final PortIDWithName NONE_SELECTION = new PortIDWithName(null, null);

    private Map<PortID, String> m_nameMap;

    private PortID m_id;

    /**
     * @param id
     * @param nameMap
     */
    public PortIDWithName(final PortID id, final Map<PortID, String> nameMap) {
        m_id = id;
        m_nameMap = nameMap;
    }

    /**
     * @return the id
     */
    public PortID getID() {
        return m_id;
    }

    @Override
    public String toString() {
        if (this == NONE_SELECTION) {
            return "<NONE>";
        }
        return m_nameMap.containsKey(m_id) ? m_nameMap.get(m_id) : m_id.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (this == NONE_SELECTION) {
            return -1;
        }
        return m_id.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == NONE_SELECTION) {
            return this == obj ? true : false;
        }
        if (!(obj instanceof PortIDWithName)) {
            return false;
        }
        PortIDWithName other = (PortIDWithName)obj;
        return m_id.equals(other.m_id);
    }
}