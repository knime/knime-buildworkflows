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
 *   Feb 25, 2026 (magnus): created
 */
package org.knime.buildworkflows.writer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ArrayPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.widget.message.TextMessage;

/**
 * Utility class for the workflow writer 2 node parameters.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
final class WorkflowWriter2NodeParameterUtil {

    private WorkflowWriter2NodeParameterUtil() {
        // Utility class
    }

    static final String CFG_NUM_INPUTS = "num_inputs";

    static final String CFG_NUM_OUTPUTS = "num_outputs";

    static final String CFG_KEY_NODE_CONFIG_CLASS = "node_config_class";

    static final String CFG_KEY_NODE_CONFIG = "node_config";

    static final String CFG_KEY_PARAM_NAME = "param_name";

    /** The name of the fixed port object input port group. */
    static final String PORT_OBJECT_INPUT_GRP_NAME = "Port Object";

    static final class AlwaysTrue implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.always();
        }

    }

    abstract static class IONodesProvider<R> implements StateProvider<R[]> {

        private Class<? extends IOIDsProvider> m_ioIdsProviderClass;

        private Class<? extends ParameterReference<R[]>> m_ioNodesRefClass;

        private Function<String, R> m_createDefaultIONodeParamsFunction;

        private Function<String, R> m_createNoneIONodeParamsFunction;

        private Function<R, String> m_extrcatIOIDFunction;

        private IntFunction<R[]> m_generateIONodeParamsArray;

        protected IONodesProvider(final Class<? extends IOIDsProvider> ioIdsProviderClass,
            final Class<? extends ParameterReference<R[]>> ioNodesRefClass,
            final Function<String, R> createDefaultIONodeParamsFunction,
            final Function<String, R> createNoneIONodeParamsFunction,
            final Function<R, String> extrcatIOIDFunction,
            final IntFunction<R[]> generateIONodeParamsArray) {
            m_ioIdsProviderClass = ioIdsProviderClass;
            m_ioNodesRefClass = ioNodesRefClass;
            m_createDefaultIONodeParamsFunction = createDefaultIONodeParamsFunction;
            m_createNoneIONodeParamsFunction = createNoneIONodeParamsFunction;
            m_extrcatIOIDFunction = extrcatIOIDFunction;
            m_generateIONodeParamsArray = generateIONodeParamsArray;
        }

        Supplier<List<String>> m_ioIdsSupplier;

        Supplier<R[]> m_ioNodesSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_ioIdsSupplier = initializer.computeFromProvidedState(m_ioIdsProviderClass);
            m_ioNodesSupplier = initializer.getValueSupplier(m_ioNodesRefClass);
        }

        @Override
        public R[] computeState(final NodeParametersInput parametersInput) {
            final var ioIds = m_ioIdsSupplier.get();
            if (ioIds == null || ioIds.isEmpty()) {
                return m_generateIONodeParamsArray.apply(0);
            }
            // If there are missing input IDs in the existing io nodes, then we have to insert a "none" configuration
            // which is visible in the dialog but is not saved as part of the configuration.
            return provideDefaultOrFillInMissings(m_ioNodesSupplier.get(), ioIds);
        }

        private R[] provideDefaultOrFillInMissings(final R[] existingIONodes,
            final List<String> ioIds) {
            if (existingIONodes == null || existingIONodes.length == 0) {
                return ioIds.stream().map(id -> m_createDefaultIONodeParamsFunction.apply(id))
                    .toArray(m_generateIONodeParamsArray);
            }
            Map<String, R> existingById = Stream.of(existingIONodes)
                    .collect(Collectors.toMap(n -> m_extrcatIOIDFunction.apply(n), Function.identity()));
            return ioIds.stream()
                .map(id -> existingById.getOrDefault(id, m_createNoneIONodeParamsFunction.apply(id)))
                .toArray(m_generateIONodeParamsArray);
        }

    }

    static final class WorkflowPortObjectSpecProvider implements StateProvider<WorkflowPortObjectSpec> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
        }

        @Override
        public WorkflowPortObjectSpec computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            final var workflowInputPortIndex =
                parametersInput.getPortsConfiguration().getInputPortLocation().get(PORT_OBJECT_INPUT_GRP_NAME)[0];
            final var portObjectSpecOpt = parametersInput.getInPortSpec(workflowInputPortIndex);
            if (portObjectSpecOpt.isEmpty()) {
                return null;
            }
            return (WorkflowPortObjectSpec)portObjectSpecOpt.get();
        }

    }

    abstract static class IOIDsProvider implements StateProvider<List<String>> {

        private Supplier<WorkflowPortObjectSpec> m_workflowPortObjectSpecSupplier;

        private final Function<WorkflowPortObjectSpec, Map<String, ?>> m_extractor;

        protected IOIDsProvider(final Function<WorkflowPortObjectSpec, Map<String, ?>> extractor) {
            m_extractor = extractor;
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_workflowPortObjectSpecSupplier =
                    initializer.computeFromProvidedState(WorkflowPortObjectSpecProvider.class);
        }

        @Override
        public List<String> computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            final var spec = m_workflowPortObjectSpecSupplier.get();
            if (spec == null) {
                return List.of();
            }
            return m_extractor.apply(spec)
                    .keySet()
                    .stream()
                    .toList();
        }

    }

    abstract static class MissingPortsMessage implements StateProvider<Optional<TextMessage.Message>> {

        private final Function<WorkflowPortObjectSpec, List<String>> m_portsFunction;

        private final String m_portType;

        protected MissingPortsMessage(final Function<WorkflowPortObjectSpec, List<String>> portsFunction,
            final String portType) {
            m_portsFunction = portsFunction;
            m_portType = portType;
        }

        Supplier<WorkflowPortObjectSpec> m_workflowPortObjectSpecSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_workflowPortObjectSpecSupplier =
                initializer.computeFromProvidedState(WorkflowPortObjectSpecProvider.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            final var workflowPortObjectSpec = m_workflowPortObjectSpecSupplier.get();
            if (workflowPortObjectSpec == null) {
                return Optional.empty();
            }
            List<String> ports = m_portsFunction.apply(workflowPortObjectSpec);
            if (ports.isEmpty()) {
                return Optional.of(new TextMessage.Message("No workflow %s ports available".formatted(m_portType),
                    "The connected workflow does not have any %s ports".formatted(m_portType),
                    TextMessage.MessageType.INFO));
            }
            return Optional.empty();
        }

    }

    abstract static class IONodesArrayPersistor<R> implements ArrayPersistor<Integer, R> {

        private String m_cfgKeyNumberIONodes;
        private String m_cfgKeyIONode;
        private String m_cfgKeyIOID;

        private Function<R, String> m_extractIOIDFunction;
        private Function<R, IONodeConfig> m_extractIOConfigClassFunction;
        private Function<R, String> m_extractParameterNameFunction;

        private Supplier<R> m_iONodeParamsSupplier;

        protected IONodesArrayPersistor(final String cfgKeyNumberIONodes, final String cfgKeyIONode,
            final String cfgKeyIOID, final Function<R, String> extractIOIDFunction,
            final Function<R, IONodeConfig> extractIOConfigClassFunction,
            final Function<R, String> extractParameterNameFunction,
            final Supplier<R> ioNodeParamSupplier) {
            m_cfgKeyNumberIONodes = cfgKeyNumberIONodes;
            m_cfgKeyIONode = cfgKeyIONode;
            m_cfgKeyIOID = cfgKeyIOID;
            m_extractIOIDFunction = extractIOIDFunction;
            m_extractIOConfigClassFunction = extractIOConfigClassFunction;
            m_extractParameterNameFunction = extractParameterNameFunction;
            m_iONodeParamsSupplier = ioNodeParamSupplier;
        }

        @Override
        public int getArrayLength(final NodeSettingsRO nodeSettings) throws InvalidSettingsException {
            return nodeSettings.getInt(m_cfgKeyNumberIONodes, 0);
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public R createElementSaveDTO(final int index) {
            return m_iONodeParamsSupplier.get();
        }

        @Override
        public void save(final List<R> savedElements, final NodeSettingsWO nodeSettings) {
            final var nonNoneIONodes =
                savedElements.stream().filter(n -> m_extractIOConfigClassFunction.apply(n) != null).toList();
            nodeSettings.addInt(m_cfgKeyNumberIONodes, nonNoneIONodes.size());
            if (!nonNoneIONodes.isEmpty()) {
                saveWorkflowInputs(nonNoneIONodes, nodeSettings);
            }
        }

        private void saveWorkflowInputs(final List<R> savedElements,
            final NodeSettingsWO nodeSettings) {
            for (int i = 0; i < savedElements.size(); i++) {
                R ioNode = savedElements.get(i);
                NodeSettingsWO node = nodeSettings.addNodeSettings(m_cfgKeyIONode + i);
                node.addString(CFG_KEY_NODE_CONFIG_CLASS,
                    m_extractIOConfigClassFunction.apply(ioNode).getClass().getCanonicalName());
                node.addString(m_cfgKeyIOID, m_extractIOIDFunction.apply(ioNode));
                NodeSettingsWO config = node.addNodeSettings(CFG_KEY_NODE_CONFIG);
                config.addString(CFG_KEY_PARAM_NAME, m_extractParameterNameFunction.apply(ioNode));
            }
        }

    }

}
