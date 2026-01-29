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

package org.knime.buildworkflows.combiner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.node.workflow.capture.WorkflowSegment.Input;
import org.knime.core.node.workflow.capture.WorkflowSegment.Output;
import org.knime.core.util.Pair;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.ArrayWidgetInternal;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.WidgetInternal;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.booleanhelpers.DoNotPersistBoolean;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.NoneChoice;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.StringOrEnum;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.message.TextMessage.SimpleTextMessageProvider;

/**
 * Node parameters for Workflow Combiner.
 *
 * @author Robin Gerling, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
final class WorkflowCombinerNodeParameters implements NodeParameters {

    @TextMessage(MissingWorkflowMessage.class)
    Void m_missingWorkflowAtIndexMessage;

    private static final class MissingWorkflowMessage implements SimpleTextMessageProvider {

        @Override
        public boolean showMessage(final NodeParametersInput parametersInput) {
            return hasMissingWorkflows(parametersInput);
        }

        @Override
        public String title() {
            return "Some input ports don't have a workflow available.";
        }

        @Override
        public String description() {
            return "Connect workflows to all input ports to configure workflow and port connections.";
        }

        @Override
        public MessageType type() {
            return MessageType.INFO;
        }

    }

    private static boolean hasMissingWorkflows(final NodeParametersInput parametersInput) {
        return Arrays.stream(parametersInput.getInPortSpecs()).anyMatch(Objects::isNull);
    }

    private static final class HideWorkflowConnections implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(ctx -> hasMissingWorkflows(ctx));
        }
    }

    @Widget(title = "Workflow connections",
        description = "For each pair of consecutive workflows (in the order of the workflow ports), select if and how"
            + " to connect the outputs of the first workflow to the inputs of the second workflow.")
    @ArrayWidget(hasFixedSize = true)
    @ArrayWidgetInternal(isSectionLayout = true, titleProvider = WorkflowConnection.WFConnTitleProvider.class)
    @PersistWithin(WorkflowCombinerNodeModel.CFG_CONNECTION_MAPS)
    @Persistor(WorkflowConnectionsPersistor.class)
    @ValueReference(WorkflowConnectionsReference.class)
    @ValueProvider(WorkflowConnectionsProvider.class)
    @Effect(predicate = HideWorkflowConnections.class, type = EffectType.HIDE)
    WorkflowConnection[] m_workflowConnections = {};

    private static final class WorkflowConnectionsReference implements ParameterReference<WorkflowConnection[]> {
    }

    private static final class WorkflowConnectionsProvider implements StateProvider<WorkflowConnection[]> {

        private Supplier<WorkflowConnection[]> m_workflowConnections;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_workflowConnections = initializer.getValueSupplier(WorkflowConnectionsReference.class);
        }

        @Override
        public WorkflowConnection[] computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            if (hasMissingWorkflows(parametersInput)) {
                throw new StateComputationFailureException();
            }
            final var inSpecs = parametersInput.getInPortSpecs();
            final var workflowConnections = m_workflowConnections.get();
            return IntStream.range(0, inSpecs.length - 1) //
                .mapToObj(i -> {
                    final var w1 = (WorkflowPortObjectSpec)inSpecs[i];
                    final var w2 = (WorkflowPortObjectSpec)inSpecs[i + 1];
                    return new WorkflowConnection(i,
                        i < workflowConnections.length
                            ? getPortConnections(w1, w2, workflowConnections[i].m_portConnections)
                            : createDefaultPortConnections(w1, w2));
                }).toArray(WorkflowConnection[]::new);
        }

        private static PortConnection[] getPortConnections(final WorkflowPortObjectSpec w1,
            final WorkflowPortObjectSpec w2, final PortConnection[] existingConnections) {
            final var outputs = w1.getOutputs();
            final var inputs = w2.getInputs();
            return w2.getInputIDs().stream() //
                .map(inputId -> Arrays.stream(existingConnections) //
                    .filter(pConn -> pConn.m_inputPortName.equals(inputId)
                        && pConn.m_outputPortName.getEnumChoice().isEmpty()
                        && outputs.containsKey(pConn.m_outputPortName.getStringChoice())
                        && outputs.get(pConn.m_outputPortName.getStringChoice()).getType()
                            .equals(inputs.get(inputId).getType()))
                    .findFirst() //
                    .orElse(new PortConnection(inputId))) //
                .toArray(PortConnection[]::new);

        }

        private static PortConnection[] createDefaultPortConnections(final WorkflowPortObjectSpec w1,
            final WorkflowPortObjectSpec w2) {
            final var inputs = w2.getInputs();
            final var inputIds = w2.getInputIDs();
            final var outputs = w1.getOutputs();
            return inputIds.stream() //
                .map(inputId -> inputIdToPortConnection(inputId, outputs, inputs)) //
                .toArray(PortConnection[]::new);
        }

        private static PortConnection inputIdToPortConnection(final String inputId, final Map<String, Output> outputs,
            final Map<String, Input> inputs) {
            return ConnectionMap //
                .getDefaultPairWiseConnection(inputId, outputs, inputs) //
                .map(outputId -> new PortConnection(outputId, inputId)) //
                .orElse(new PortConnection(inputId));
        }
    }

    static final class WorkflowConnection implements NodeParameters {

        WorkflowConnection() {
            // serialization
        }

        WorkflowConnection(final int index, final PortConnection[] portConnections) {
            m_index = index;
            m_portConnections = portConnections;
        }

        @Persistor(DoNotPersistInt.class)
        @ValueReference(WorkflowConnection.IndexReference.class)
        int m_index;

        @Persistor(DoNotPersistBoolean.class)
        @ValueReference(HasNoInputs.class)
        @ValueProvider(HasNoInputsProvider.class)
        boolean m_hasNoInputs;

        @TextMessage(WorkflowHasNoInputsMessage.class)
        Void m_hasNoInputsMessage;

        @Widget(title = "Port connections", description = """
                <ul><li>
                <b>Default configuration:</b> By default (i.e., if no manual configuration is applied), the inputs of \
                each workflow are automatically connected to the outputs of the predecessor workflow, i.e., the \
                workflow connected to the previous input port. The <tt>i</tt>-th output of workflow <tt>j</tt> is \
                connected to the <tt>i</tt>-th input of workflow <tt>j+1</tt>. If this default pairing of inputs and \
                outputs cannot be applied (for example, due to a mismatching number of inputs and outputs or \
                incompatible port types), the node cannot be executed and requires manual configuration.
                </li><li>
                <b>Manual configuration:</b> If the default configuration is not applicable or not desired, the \
                output-to-input pairing can be chosen manually. This is done by selecting the outputs that are to be \
                connected to inputs of the subsequent workflow. Only compatible port types are eligible.
                </li></ul>
                """)
        @ArrayWidget(hasFixedSize = true)
        @ArrayWidgetInternal(titleProvider = PortConnection.PortConnTitleProvider.class)
        @Effect(predicate = HasNoInputs.class, type = EffectType.HIDE)
        PortConnection[] m_portConnections = {};

        private static final class IndexReference implements ParameterReference<Integer> {
        }

        private static final class HasNoInputs implements BooleanReference {
        }

        private static final class WorkflowProvider implements StateProvider<WorkflowPortObjectSpec[]> {

            private Supplier<Integer> m_index;

            @Override
            public void init(final StateProviderInitializer initializer) {
                /**
                 * We could only use after open dialog here, but then, the titles would flicker. Because this is only
                 * one call and not complex, I think it is okay to also use before open dialog.<br>
                 * Before: for saved setting, the index is already set during load in the persistor<br>
                 * After: for new added workflow/port connections, the whole workflow connection element is set via
                 * {@link WorkflowConnectionsProvider} before open dialog, why we can only compute the title after the
                 * dialog is opened.<br>
                 * The same accounts to the {@link PortConnTitleProvider}.
                 */
                initializer.computeBeforeOpenDialog();
                initializer.computeAfterOpenDialog();
                m_index = initializer.getValueSupplier(IndexReference.class);
            }

            @Override
            public WorkflowPortObjectSpec[] computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
                if (hasMissingWorkflows(parametersInput)) {
                    throw new StateComputationFailureException();
                }
                final var inSpecs = parametersInput.getInPortSpecs();
                final var index = m_index.get();
                if (index + 1 >= inSpecs.length) {
                    throw new StateComputationFailureException();
                }
                return new WorkflowPortObjectSpec[]{(WorkflowPortObjectSpec)inSpecs[index],
                    (WorkflowPortObjectSpec)inSpecs[index + 1]};
            }
        }

        private abstract static class AbstractWorkflowProvider<T> implements StateProvider<T> {

            protected Supplier<WorkflowPortObjectSpec[]> m_workflowSpecs;

            @Override
            public void init(final StateProviderInitializer initializer) {
                m_workflowSpecs = initializer.computeFromProvidedState(WorkflowProvider.class);
            }

        }

        private static final class HasNoInputsProvider extends AbstractWorkflowProvider<Boolean> {
            @Override
            public Boolean computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
                return m_workflowSpecs.get()[1].getInputs().isEmpty();
            }
        }

        private static final class WFConnTitleProvider extends AbstractWorkflowProvider<String> {
            @Override
            public String computeState(final NodeParametersInput parametersInput) {
                final var wSpecs = m_workflowSpecs.get();
                return String.format("%s → %s", wSpecs[0].getWorkflowName(), wSpecs[1].getWorkflowName());
            }
        }

        private static final class WorkflowHasNoInputsMessage
            extends AbstractWorkflowProvider<Optional<TextMessage.Message>> {
            @Override
            public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
                final var w2 = m_workflowSpecs.get()[1];
                if (!w2.getInputIDs().isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new TextMessage.Message("No configuration required",
                    String.format("\"%s\" has no input ports.", w2.getWorkflowName()), MessageType.INFO));
            }
        }

    }

    static final class PortConnection implements NodeParameters {

        PortConnection() {
            // serialization
        }

        PortConnection(final String inputPortName) {
            m_inputPortName = inputPortName;
        }

        PortConnection(final String outputPortName, final String inputPortName) {
            m_outputPortName = new StringOrEnum<>(outputPortName);
            m_inputPortName = inputPortName;
        }

        @Persistor(DoNotPersistString.class)
        @ValueReference(InputPortNameReference.class)
        String m_inputPortName;

        @Widget(title = "Output port name", description = "The output port of the first workflow.")
        @WidgetInternal(
            hideControlInNodeDescription = "The description is already given in the port connections description.")
        @ChoicesProvider(OutputPortNameChoicesProvider.class)
        StringOrEnum<NoneChoice> m_outputPortName = new StringOrEnum<>(NoneChoice.NONE);

        private static final class InputPortNameReference implements ParameterReference<String> {
        }

        private static final class PortConnTitleProvider implements StateProvider<String> {

            private Supplier<String> m_inputPortName;

            @Override
            public void init(final StateProviderInitializer initializer) {
                /**
                 * see {@link WorkflowProvider}.
                 */
                initializer.computeBeforeOpenDialog();
                initializer.computeAfterOpenDialog();
                m_inputPortName = initializer.getValueSupplier(InputPortNameReference.class);
            }

            @Override
            public String computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
                return String.format("Input \"%s\"", m_inputPortName.get());
            }

        }

        private static final class OutputPortNameChoicesProvider implements StringChoicesProvider {

            private Supplier<Integer> m_index;

            private Supplier<String> m_inputPortName;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeAfterOpenDialog();
                m_index = initializer.getValueSupplier(WorkflowConnection.IndexReference.class);
                m_inputPortName = initializer.getValueSupplier(InputPortNameReference.class);
            }

            @Override
            public List<String> choices(final NodeParametersInput context) {
                final var index = m_index.get();
                final var spec1 = context.getInPortSpec(index);
                final var spec2 = context.getInPortSpec(index + 1);
                if (spec1.isEmpty() || spec2.isEmpty()) {
                    return List.of();
                }
                final var w2 = (WorkflowPortObjectSpec)spec2.get();
                final var w2Inputs = w2.getInputs();
                final var inputPortName = m_inputPortName.get();
                if (!w2Inputs.containsKey(inputPortName)) {
                    return List.of();
                }
                final var w2InType = w2Inputs.get(inputPortName).getType();
                final var w1 = (WorkflowPortObjectSpec)spec1.get();
                return w1.getOutputs().entrySet().stream() //
                    .filter(entry -> entry.getValue().getType().equals(w2InType)) //
                    .map(Map.Entry::getKey) //
                    .toList();
            }

        }
    }

    /**
     * Persists the workflow connections and their port connections.
     *
     * The old dialog only persisted port connections with a real output port selected (i.e. not "NONE"). Because we do
     * not have the input specs available during load, the node dialog will get dirty when not all inputs are assigned
     * to a real output port due to the length of the loaded connections being smaller than the length of the provided
     * connections, because we add array items for those none entries which are determined by the given specs in the
     * {@link WorkflowConnectionsProvider}. We can also not save "NONE" selections, because the node model fails due to
     * the input being {@code null} then.
     */
    private static final class WorkflowConnectionsPersistor implements NodeParametersPersistor<WorkflowConnection[]> {

        @Override
        public WorkflowConnection[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var maps = new ConnectionMaps();
            maps.load(settings);
            return IntStream.range(0, maps.getNumberOfConnectionMaps()) //
                .mapToObj(mapIndex -> {
                    final var map = maps.getConnectionMap(mapIndex);
                    final var connections = map.getConnections();
                    final var portConnections = connections.stream() //
                        .map(conn -> new PortConnection(conn.getFirst(), conn.getSecond())) //
                        .toArray(PortConnection[]::new);
                    return new WorkflowConnection(mapIndex, portConnections);
                }) //
                .toArray(WorkflowConnection[]::new);
        }

        @Override
        public void save(final WorkflowConnection[] param, final NodeSettingsWO settings) {
            final var connectionMaps = Arrays.stream(param).map(wfConn -> {
                final var portConnections = wfConn.m_portConnections;
                final var connectionsList = Arrays.stream(portConnections) //
                    .filter(portConn -> portConn.m_outputPortName.getEnumChoice().isEmpty()) //
                    .map(portConn -> Pair.create(portConn.m_outputPortName.getStringChoice(), portConn.m_inputPortName))
                    .toList();
                return new ConnectionMap(connectionsList);
            }).toArray(ConnectionMap[]::new);
            new ConnectionMaps(connectionMaps).save(settings);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }

    }

    private abstract static class DoNotPersist<T> implements NodeParametersPersistor<T> {

        private final T m_defaultValue;

        protected DoNotPersist(final T defaultValue) {
            m_defaultValue = defaultValue;
        }

        @Override
        public final T load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return m_defaultValue;
        }

        @Override
        public final void save(final T obj, final NodeSettingsWO settings) {
        }

        @Override
        public final String[][] getConfigPaths() {
            return new String[0][];
        }
    }

    private static final class DoNotPersistInt extends DoNotPersist<Integer> {
        DoNotPersistInt() {
            super(0);
        }

    }

    private static final class DoNotPersistString extends DoNotPersist<String> {
        DoNotPersistString() {
            super("");
        }

    }
}
