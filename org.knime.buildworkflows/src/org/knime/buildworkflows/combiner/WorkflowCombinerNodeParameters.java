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
 *   Sep 23, 2025 (AI Migration): created
 */
package org.knime.buildworkflows.combiner;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.PersistorFactory;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.widget.message.TextMessage;
/**
 * Node parameters (modern UI) for the Workflow Combiner node. Replaces the legacy dialog.
 *
 * The legacy dialog allowed users to configure how successive workflow segments are connected. Each pair of neighboring
 * workflow segments (workflow i and workflow i+1) can have a custom ConnectionMap. If not specified, a simple pair-wise
 * port connection is assumed. These maps were previously serialized inside a sub-config with key
 * {@link WorkflowCombinerNodeModel#CFG_CONNECTION_MAPS}.
 *
 * In the Modern UI we currently expose the effective connection strategy only as an informational message (actual
 * interactive editing of connection maps is deferred). The persisted structure is kept 100% backwards-compatible so
 * that existing workflows load unchanged. Once a dedicated widget for editing ConnectionMaps exists, it can replace the
 * placeholder here without changing persistence.
 */
public final class WorkflowCombinerNodeParameters implements NodeParameters {

    /**
     * Holds all configured connection maps between subsequent workflow inputs. If null or empty, simple pair-wise
     * mapping is applied implicitly (see {@link ConnectionMaps#PAIR_WISE_CONNECTION_MAPS}).
     */
    ConnectionMaps connectionMaps = ConnectionMaps.PAIR_WISE_CONNECTION_MAPS; // package scope so model could access if ever needed

    /**
     * Informational text shown to user (placeholder until an editor widget for connection maps is available).
     */
    @Widget(title = "Workflow Connections",
        description = "Currently the node connects subsequent workflow segments using either the stored custom connection maps (if any) or a simple pair-wise strategy. Existing workflows keep their exact behavior.")
    @TextMessage()
    Void info; // shown as read-only message

    /** Empty constructor required by framework. */
    public WorkflowCombinerNodeParameters() {
        // defaults already set in field declarations
    }


    static final class InputPreviewMessage implements StateProvider<Optional<TextMessage.Message>> {

    }
    /** Custom persistor to remain backward compatible with legacy dialog serialization. */
    @Persistor(WorkflowCombinerNodeParameters.ConnectionMapsPersistorFactory.class)
    static final class ConnectionMapsPersistor implements NodeParametersPersistor {

        @Override
        public void save(final NodeParameters params, final NodeSettingsWO settings) throws InvalidSettingsException {
            var s = (WorkflowCombinerNodeParameters)params;
            if (s.connectionMaps != null && s.connectionMaps != ConnectionMaps.PAIR_WISE_CONNECTION_MAPS) {
                NodeSettingsWO cmSettings = settings.addNodeSettings(WorkflowCombinerNodeModel.CFG_CONNECTION_MAPS);
                s.connectionMaps.save(cmSettings);
            }
        }

        @Override
        public void load(final NodeParameters params, final NodeSettingsRO settings) throws InvalidSettingsException {
            var s = (WorkflowCombinerNodeParameters)params;
            s.connectionMaps = ConnectionMaps.PAIR_WISE_CONNECTION_MAPS;
            if (settings.containsKey(WorkflowCombinerNodeModel.CFG_CONNECTION_MAPS)) {
                NodeSettingsRO cmSettings = settings.getNodeSettings(WorkflowCombinerNodeModel.CFG_CONNECTION_MAPS);
                var cm = new ConnectionMaps();
                cm.load(cmSettings);
                s.connectionMaps = cm;
            }
        }

        @Override
        public String[] getConfigPaths() {
            return new String[]{WorkflowCombinerNodeModel.CFG_CONNECTION_MAPS};
        }
    }

    /** Factory indirection required by annotation processing. */
    public static final class ConnectionMapsPersistorFactory implements PersistorFactory {
        @Override
        public Persistor create() {
            return new ConnectionMapsPersistor();
        }
    }

    @Override
    public void validate() throws InvalidSettingsException {
        // nothing to validate: connection maps either deserialize or default.
    }
}
