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
 * {@link NodeParameters} for the workflow input nodes.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
final class WorkflowInputNodeParameters implements NodeParameters {

    static final String CFG_KEY_INPUT_NODE = "input_node_";

    static final String CFG_KEY_INPUT_ID = "input_id";

    @HorizontalLayout
    interface IdAndConfigClassLayout {
    }

    @After(IdAndConfigClassLayout.class)
    interface NodeConfigLayout {
    }

    @Layout(IdAndConfigClassLayout.class)
    @Widget(title = "Input ID", description = "The identifier for this input port.")
    @PersistArrayElement(InputNodeElementPersistor.class)
    @Effect(predicate = AlwaysTrue.class, type = EffectType.DISABLE)
    String m_inputId;

    @Layout(IdAndConfigClassLayout.class)
    @Widget(title = "Node type", description = "The type of node to add for this input.")
    @PersistArrayElement(InputNodeConfigClassPersistor.class)
    @ValueReference(NodeConfigClassRef.class)
    InputNodeConfigType m_nodeConfigClass = InputNodeConfigType.NONE;

    static final class NodeConfigClassRef implements ParameterReference<InputNodeConfigType> {
    }

    @Layout(NodeConfigLayout.class)
    @Widget(title = "Parameter name", description = "The name of the input node parameter.")
    @PersistArrayElement(InputNodeConfigPersistor.class)
    @ValueProvider(ParameterNameProvider.class)
    @ValueReference(ParameterNameRef.class)
    @Effect(predicate = IsNoneInputNodeConfigType.class, type = EffectType.HIDE)
    String m_parameterName;

    static final class ParameterNameRef implements ParameterReference<String> {
    }

    static final class IsNoneInputNodeConfigType implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(NodeConfigClassRef.class).isOneOf(InputNodeConfigType.NONE);
        }

    }

    static final class ParameterNameProvider implements StateProvider<String> {

        Supplier<InputNodeConfigType> m_inputNodeConfigType;

        Supplier<String> m_parameterNameSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputNodeConfigType = initializer.computeFromValueSupplier(NodeConfigClassRef.class);
            m_parameterNameSupplier = initializer.getValueSupplier(ParameterNameRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            String paramName = m_parameterNameSupplier.get();
            if (paramName == null || paramName.isEmpty()) {
                if (m_inputNodeConfigType.get() == InputNodeConfigType.NONE) {
                    throw new StateComputationFailureException();
                }
                return m_inputNodeConfigType.get().getInputNodeConfig().getDefaultParameterName();
            }
            throw new StateComputationFailureException();
        }

    }

    static final class InputNodeElementPersistor
        implements ElementFieldPersistor<String, Integer, WorkflowInputNodeParameters> {

        @Override
        public String load(final NodeSettingsRO nodeSettings, final Integer loadContext)
            throws InvalidSettingsException {
            NodeSettingsRO node = nodeSettings.getNodeSettings(CFG_KEY_INPUT_NODE + loadContext);
            return node.getString(CFG_KEY_INPUT_ID, "");
        }

        @Override
        public void save(final String param, final WorkflowInputNodeParameters saveDTO) {
            saveDTO.m_inputId = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY_INPUT_NODE + ARRAY_INDEX_PLACEHOLDER, CFG_KEY_INPUT_ID}};
        }

    }

    static final class InputNodeConfigClassPersistor
        implements ElementFieldPersistor<InputNodeConfigType, Integer, WorkflowInputNodeParameters> {

        @Override
        public InputNodeConfigType load(final NodeSettingsRO nodeSettings, final Integer loadContext)
            throws InvalidSettingsException {
            NodeSettingsRO node = nodeSettings.getNodeSettings(CFG_KEY_INPUT_NODE + loadContext);
            return InputNodeConfigType.getFromValue(
                node.getString(WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG_CLASS, ""));
        }

        @Override
        public void save(final InputNodeConfigType param, final WorkflowInputNodeParameters saveDTO) {
            saveDTO.m_nodeConfigClass = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY_INPUT_NODE + ARRAY_INDEX_PLACEHOLDER,
                WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG_CLASS}};
        }

    }

    static final class InputNodeConfigPersistor
        implements ElementFieldPersistor<String, Integer, WorkflowInputNodeParameters> {

        @Override
        public String load(final NodeSettingsRO nodeSettings, final Integer loadContext)
            throws InvalidSettingsException {
            NodeSettingsRO node = nodeSettings.getNodeSettings(CFG_KEY_INPUT_NODE + loadContext);
            NodeSettingsRO config = node.getNodeSettings(WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG);
            return config.getString(WorkflowWriter2NodeParameterUtil.CFG_KEY_PARAM_NAME, "");
        }

        @Override
        public void save(final String param, final WorkflowInputNodeParameters saveDTO) {
            saveDTO.m_parameterName = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CFG_KEY_INPUT_NODE + ARRAY_INDEX_PLACEHOLDER,
                WorkflowWriter2NodeParameterUtil.CFG_KEY_NODE_CONFIG,
                WorkflowWriter2NodeParameterUtil.CFG_KEY_PARAM_NAME}};
        }

    }

    WorkflowInputNodeParameters() {
    }

    WorkflowInputNodeParameters(final String inputId, final InputNodeConfigType nodeConfigClass,
        final String parameterName) {
        m_inputId = inputId;
        m_nodeConfigClass = nodeConfigClass;
        m_parameterName = parameterName;
    }

    enum InputNodeConfigType {

            @Label(value = "None")
            NONE("none"), //

            @Label(value = WorkflowInputNodeConfig.NODE_NAME)
            WORKFLOW_INPUT(WorkflowInputNodeConfig.class.getCanonicalName()), //

            @Label(value = TableInputNodeConfig.NODE_NAME)
            TABLE_INPUT(TableInputNodeConfig.class.getCanonicalName()), //

            @Label(value = RowInputNodeConfig.NODE_NAME)
            ROW_INPUT(RowInputNodeConfig.class.getCanonicalName());

        private final String m_nodeConfigClass;

        private final InputNodeConfig m_inputNodeConfig;

        InputNodeConfigType(final String nodeConfigClass) {
            m_nodeConfigClass = nodeConfigClass;
            if (WorkflowInputNodeConfig.class.getCanonicalName().equals(nodeConfigClass)) {
                m_inputNodeConfig = new WorkflowInputNodeConfig();
            } else if (TableInputNodeConfig.class.getCanonicalName().equals(nodeConfigClass)) {
                m_inputNodeConfig = new TableInputNodeConfig();
            } else if (RowInputNodeConfig.class.getCanonicalName().equals(nodeConfigClass)) {
                m_inputNodeConfig = new RowInputNodeConfig();
            } else {
                m_inputNodeConfig = null;
            }
        }

        String getNodeConfigClass() {
            return m_nodeConfigClass;
        }

        InputNodeConfig getInputNodeConfig() {
            return m_inputNodeConfig;
        }

        static InputNodeConfigType getFromValue(final String value) throws InvalidSettingsException {
            for (final InputNodeConfigType type : values()) {
                if (type.getNodeConfigClass().equals(value)) {
                    return type;
                }
            }
            throw new InvalidSettingsException(createInvalidSettingsExceptionMessage(value));
        }

        private static String createInvalidSettingsExceptionMessage(final String name) {
            var values = Arrays.stream(InputNodeConfigType.values()).map(type -> type.getNodeConfigClass())
                .collect(Collectors.joining(", "));
            return String.format("Invalid value '%s'. Possible values: %s", name, values);
        }

    }

}
