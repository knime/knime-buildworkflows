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

package org.knime.buildworkflows.reader;

import java.util.Optional;
import java.util.function.Predicate;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.HorizontalLayout;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;

/**
 * Node parameters for Workflow Reader.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
class WorkflowReaderNodeParameters implements NodeParameters {

    @Persist(configKey = "workflow-chooser")
    WorkflowSelectionParameters m_workflowSelection = new WorkflowSelectionParameters();

    @Widget(title = "Custom workflow name", //
        description = "An optional custom name for the workflow. If not specified, the original name will be kept.")
    @Persistor(WorkflowNamePersistor.class)
    Optional<String> m_workflowName = Optional.empty();

    static final class WorkflowNamePersistor implements NodeParametersPersistor<Optional<String>> {

        static final String CFG_KEY = "custom-name";

        @Override
        public Optional<String> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return Optional.ofNullable(settings.getString(CFG_KEY, null)).filter(Predicate.not(String::isEmpty));
        }

        @Override
        public void save(final Optional<String> param, final NodeSettingsWO settings) {
            settings.addString(CFG_KEY, param.orElse(""));
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY}};
        }

    }

    @Widget(title = "Remove input and output nodes", //
        description = """
                If checked, all <i>Container Input *</i> and <i>Container Output *</i> nodes are removed and \
                implicitly represented. This allows one to, e.g., combine the resulting workflow segment with \
                other workflow segments via the new implicit input and output ports.
                """)
    @Persist(configKey = "remove-io-nodes")
    @ValueReference(RemoveIONodes.class)
    boolean m_removeIONodes;

    static final class RemoveIONodes implements BooleanReference {
    }

    @HorizontalLayout
    interface InputOutputIdPrefixes {

    }

    @Widget(title = "Input ID prefix", //
        description = "Prefix to be used as ids for the implicit input ports of the workflow segment.")
    @Persist(configKey = "input-id-prefix")
    @Effect(predicate = RemoveIONodes.class, type = EffectType.ENABLE)
    @Layout(InputOutputIdPrefixes.class)
    String m_inputIdPrefix = "input";

    @Widget(title = "Output ID prefix", //
        description = "Prefix to be used as ids for the implicit output ports of the workflow segment.")
    @Persist(configKey = "output-id-prefix")
    @Effect(predicate = RemoveIONodes.class, type = EffectType.ENABLE)
    @Layout(InputOutputIdPrefixes.class)
    String m_outputIdPrefix = "output";

    @After(InputOutputIdPrefixes.class)
    interface ExportVariableWorkflowGotReset {

    }

    @Widget(title = "Output flow variable indicating whether workflow got reset during loading", //
        description = """
                If selected, the node creates and exports a flow variable <tt>workflow_got_reset</tt> denoting \
                whether the workflow was (partially) executed prior import. As soon as the original workflow had \
                at least one executed node, this property will be <tt>true</tt>. Note that the workflow in the \
                output will be reset in either case.
                """)
    @Layout(ExportVariableWorkflowGotReset.class)
    boolean m_exportVariableWorkflowGotReset;

}
