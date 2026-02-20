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
 *   Feb 17, 2026 (magnus): created
 */
package org.knime.buildworkflows.writer;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.knime.buildworkflows.writer.WorkflowWriter2NodeParameterUtil.AlwaysTrue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ElementFieldPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArrayElement;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.HorizontalLayout;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.Label;

/**
 * {@link NodeParameters} for the workflow output nodes.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
final class WorkflowOutputNodeParameters implements NodeParameters {

    static final String CFG_KEY_OUTPUT_NODE = "output_node_";

    static final String CFG_KEY_OUTPUT_ID = "output_id";

    @HorizontalLayout
    interface IdAndConfigClassLayout {
    }

    @After(IdAndConfigClassLayout.class)
    interface NodeConfigLayout {
    }

    @Layout(IdAndConfigClassLayout.class)
    @Widget(title = "Output ID", description = "The identifier for this output port.")
    @PersistArrayElement(OutputNodeElementPersistor.class)
    @Effect(predicate = AlwaysTrue.class, type = EffectType.DISABLE)
    String m_outputId;

    @Layout(IdAndConfigClassLayout.class)
    @Widget(title = "Node type", description = "The type of node to add for this output.")
    @PersistArrayElement(OutputNodeConfigClassPersistor.class)
    @ValueReference(NodeConfigClassRef.class)
    OutputNodeConfigType m_nodeConfigClass = OutputNodeConfigType.NONE;

    static final class NodeConfigClassRef implements ParameterReference<OutputNodeConfigType> {
    }

    @Layout(NodeConfigLayout.class)
    @Widget(title = "Parameter name", description = "The name of the output node parameter.")
    @PersistArrayElement(OutputNodeConfigPersistor.class)
    @ValueProvider(ParameterNameProvider.class)
    @ValueReference(ParameterNameRef.class)
    @Effect(predicate = IsNoneInputNodeConfigType.class, type = EffectType.HIDE)
    String m_parameterName;

    static final class ParameterNameRef implements ParameterReference<String> {
    }

    static final class IsNoneInputNodeConfigType implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(NodeConfigClassRef.class).isOneOf(OutputNodeConfigType.NONE);
        }

    }

    static final class ParameterNameProvider implements StateProvider<String> {

        Supplier<OutputNodeConfigType> m_outputNodeConfigType;

        Supplier<String> m_parameterNameSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_outputNodeConfigType = initializer.computeFromValueSupplier(NodeConfigClassRef.class);
            m_parameterNameSupplier = initializer.getValueSupplier(ParameterNameRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            String paramName = m_parameterNameSupplier.get();
            if (paramName == null || paramName.isEmpty()) {
                if (m_outputNodeConfigType.get() == OutputNodeConfigType.NONE) {
                    throw new StateComputationFailureException();
                }
                return m_outputNodeConfigType.get().getOutputNodeConfig().getDefaultParameterName();
            }
            throw new StateComputationFailureException();
        }

    }

    static final class OutputNodeElementPersistor
        implements ElementFieldPersistor<String, Integer, WorkflowOutputNodeParameters> {

        @Override
        public String load(final NodeSettingsRO nodeSettings, final Integer loadContext)
            throws InvalidSettingsException {
            NodeSettingsRO node = nodeSettings.getNodeSettings(CFG_KEY_OUTPUT_NODE + loadContext);
            return node.getString(CFG_KEY_OUTPUT_ID, "");
        }

        @Override
        public void save(final String param, final WorkflowOutputNodeParameters saveDTO) {
            saveDTO.m_outputId = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY_OUTPUT_NODE + ARRAY_INDEX_PLACEHOLDER, CFG_KEY_OUTPUT_ID}};
        }

    }

    static final class OutputNodeConfigClassPersistor
        implements ElementFieldPersistor<OutputNodeConfigType, Integer, WorkflowOutputNodeParameters> {

        @Override
        public OutputNodeConfigType load(final NodeSettingsRO nodeSettings, final Integer loadContext)
            throws InvalidSettingsException {
            NodeSettingsRO node = nodeSettings.getNodeSettings(CFG_KEY_OUTPUT_NODE + loadContext);
            return OutputNodeConfigType.getFromValue(
                node.getString(WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG_CLASS, ""));
        }

        @Override
        public void save(final OutputNodeConfigType param, final WorkflowOutputNodeParameters saveDTO) {
            saveDTO.m_nodeConfigClass = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY_OUTPUT_NODE + ARRAY_INDEX_PLACEHOLDER,
                WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG_CLASS}};
        }

    }

    static final class OutputNodeConfigPersistor
        implements ElementFieldPersistor<String, Integer, WorkflowOutputNodeParameters> {

        @Override
        public String load(final NodeSettingsRO nodeSettings, final Integer loadContext)
            throws InvalidSettingsException {
            NodeSettingsRO node = nodeSettings.getNodeSettings(CFG_KEY_OUTPUT_NODE + loadContext);
            NodeSettingsRO config = node.getNodeSettings(WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG);
            return config.getString(WorkflowWriter2NodeParameterUtil.CFG_KEY_PARAM_NAME, "");
        }

        @Override
        public void save(final String param, final WorkflowOutputNodeParameters saveDTO) {
            saveDTO.m_parameterName = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY_OUTPUT_NODE + ARRAY_INDEX_PLACEHOLDER,
                WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG,
                WorkflowWriter2NodeParameterUtil.CFG_KEY_PARAM_NAME}};
        }

    }

    WorkflowOutputNodeParameters() {
    }

    WorkflowOutputNodeParameters(final String outputId, final OutputNodeConfigType nodeConfigClass,
        final String parameterName) {
        m_outputId = outputId;
        m_nodeConfigClass = nodeConfigClass;
        m_parameterName = parameterName;
    }

    enum OutputNodeConfigType {

            @Label(value = "None")
            NONE("none"), //

            @Label(value = WorkflowOutputNodeConfig.NODE_NAME)
            WORKFLOW_OUTPUT(WorkflowOutputNodeConfig.class.getCanonicalName()), //

            @Label(value = TableOutputNodeConfig.NODE_NAME)
            TABLE_OUTPUT(TableOutputNodeConfig.class.getCanonicalName()), //

            @Label(value = RowOutputNodeConfig.NODE_NAME)
            ROW_OUTPUT(RowOutputNodeConfig.class.getCanonicalName());

        private final String m_nodeConfigClass;

        private final OutputNodeConfig m_outputNodeConfig;

        OutputNodeConfigType(final String nodeConfigClass) {
            m_nodeConfigClass = nodeConfigClass;
            if (WorkflowOutputNodeConfig.class.getCanonicalName().equals(nodeConfigClass)) {
                m_outputNodeConfig = new WorkflowOutputNodeConfig();
            } else if (TableOutputNodeConfig.class.getCanonicalName().equals(nodeConfigClass)) {
                m_outputNodeConfig = new TableOutputNodeConfig();
            } else if (RowOutputNodeConfig.class.getCanonicalName().equals(nodeConfigClass)) {
                m_outputNodeConfig = new RowOutputNodeConfig();
            } else {
                m_outputNodeConfig = null;
            }
        }

        String getNodeConfigClass() {
            return m_nodeConfigClass;
        }

        OutputNodeConfig getOutputNodeConfig() {
            return m_outputNodeConfig;
        }

        static OutputNodeConfigType getFromValue(final String value) throws InvalidSettingsException {
            for (final OutputNodeConfigType type : values()) {
                if (type.getNodeConfigClass().equals(value)) {
                    return type;
                }
            }
            throw new InvalidSettingsException(createInvalidSettingsExceptionMessage(value));
        }

        private static String createInvalidSettingsExceptionMessage(final String name) {
            var values = Arrays.stream(OutputNodeConfigType.values()).map(type -> type.getNodeConfigClass())
                .collect(Collectors.joining(", "));
            return String.format("Invalid value '%s'. Possible values: %s", name, values);
        }

    }

}
