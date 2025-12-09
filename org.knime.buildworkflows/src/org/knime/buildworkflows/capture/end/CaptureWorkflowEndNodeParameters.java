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

package org.knime.buildworkflows.capture.end;

import static org.knime.core.node.workflow.capture.WorkflowPortObjectSpec.ensureInputIDsCount;
import static org.knime.core.node.workflow.capture.WorkflowPortObjectSpec.ensureOutputIDsCount;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowCaptureOperation;
import org.knime.core.node.workflow.capture.WorkflowSegment.Input;
import org.knime.core.node.workflow.capture.WorkflowSegment.Output;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ArrayPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ElementFieldPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArray;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArrayElement;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.array.ArrayWidget.ElementLayout;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.DefaultProvider;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsPositiveIntegerValidation;
import org.knime.node.parameters.widget.text.TextInputWidget;
import org.knime.node.parameters.widget.text.util.ColumnNameValidationUtils.ColumnNameValidation;

/**
 * Node parameters for Capture Workflow End.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
class CaptureWorkflowEndNodeParameters implements NodeParameters {

    @Advanced
    @Section(title = "Input Port IDs", description = "Customization of the unique identifiers for each input port.")
    interface InputIDSection {
    }

    @Advanced
    @Section(title = "Output Port IDs", description = "Customization of the unique identifiers for each output port.")
    interface OutputIDSection {
    }

    @Widget(title = "Store input tables", description = """
            If checked the input tables at the extracted workflow's input ports (table ports only!) will be stored, too.
            """)
    @Persist(configKey = CaptureWorkflowEndNodeModel.CFG_ADD_INPUT_DATA)
    @ValueReference(StoreInputTablesRef.class)
    boolean m_storeInputTables;

    @Widget(title = "Maximum number of rows to store", description = """
            The maximum number of rows to be stored as example input data table at each workflow table input port.
            """)
    @Persist(configKey = CaptureWorkflowEndNodeModel.CFG_MAX_NUM_ROWS)
    @NumberInputWidget(minValidation = IsPositiveIntegerValidation.class)
    @Effect(predicate = StoreInputTablesPredicate.class, type = EffectType.SHOW)
    int m_maxNumRows = 10;

    @Widget(title = "Propagate variables", description = """
            If selected, variables defined (or modified) within the <i>Capture</i> block are propagated downstream of
            the <i>Capture Workflow End</i> node. In most cases users will want to check this box (which is also the
            default). Previous versions of KNIME did not have this option and variables were always limited in scope and
            not visible downstream.
            """)
    @Persist(configKey = CaptureWorkflowEndNodeModel.CFG_EXPORT_VARIABLES)
    @Migration(PropagateVariablesMigration.class)
    boolean m_propagateVariables = true;

    static final class PropagateVariablesMigration implements DefaultProvider<Boolean> {

        @Override
        public Boolean getDefault() {
            return false;
        }

    }

    @Widget(title = "Disconnect links of components and metanodes", description = """
            If enabled, the links on linked components and metanodes contained in the captured workflow segment will be
            removed upon capture.
            """)
    @Persist(configKey = CaptureWorkflowEndNodeModel.CFG_DO_REMOVE_TEMPLATE_LINKS)
    boolean m_disconnectTemplateLinks;

    @Widget(title = "Custom workflow name",
            description = "A custom name for the captured workflow. If left empty, the original name will be taken.")
    @Persist(configKey = CaptureWorkflowEndNodeModel.CFG_CUSTOM_WORKFLOW_NAME)
    String m_customWorkflowName = "";

    @Layout(InputIDSection.class)
    @PersistWithin({CaptureWorkflowEndNodeModel.CFG_INPUT_IDS})
    @PersistArray(InputIDsArrayPersistor.class)
    @Widget(title = "Input IDs", description = """
            Customization of the unique identifiers for each input port.
            """)
    @ArrayWidget(elementLayout = ElementLayout.VERTICAL_CARD, hasFixedSize = true)
    @ValueProvider(InputIDsArrayProvider.class)
    @ValueReference(InputIDsArrayRef.class)
    InputID[] m_inputIDs = new InputID[0];

    @Layout(OutputIDSection.class)
    @PersistWithin({CaptureWorkflowEndNodeModel.CFG_OUTPUT_IDS})
    @PersistArray(OuputIDsArrayPersistor.class)
    @Widget(title = "Output IDs", description = """
            Customization of the unique identifiers for each output port.
            """)
    @ArrayWidget(elementLayout = ElementLayout.VERTICAL_CARD, hasFixedSize = true)
    @ValueProvider(OutputIDsArrayProvider.class)
    @ValueReference(OutputIDsArrayRef.class)
    OutputID[] m_outputIDs = new OutputID[0];

    static final class StoreInputTablesRef implements BooleanReference {
    }

    static final class InputIDsArrayRef implements ParameterReference<InputID[]> {
    }

    static final class OutputIDsArrayRef implements ParameterReference<OutputID[]> {
    }

    private static final class StoreInputTablesPredicate implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getPredicate(StoreInputTablesRef.class);
        }

    }

    static final class InputIDsArrayProvider implements StateProvider<InputID[]> {

        Supplier<InputID[]> m_inputIDsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_inputIDsSupplier = initializer.getValueSupplier(InputIDsArrayRef.class);
        }

        @Override
        public InputID[] computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {

            NodeContainer nc = NodeContext.getContext().getNodeContainer();
            if (nc == null) {
                throw new StateComputationFailureException();
            }

            WorkflowCaptureOperation captureOp;
            try {
                captureOp = nc.getParent().createCaptureOperationFor(nc.getID());

                //get 'connected' inputs only
                List<Input> inputs = captureOp.getInputs().stream().filter(Input::isConnected)
                        .collect(Collectors.toList());

                final var currentInputIDs = Optional.ofNullable(m_inputIDsSupplier.get()).orElse(new InputID[]{});
                var customInputIDs = Arrays.stream(currentInputIDs)
                        .map(inputID -> inputID.m_inputID).collect(Collectors.toList());
                customInputIDs = ensureInputIDsCount(customInputIDs, inputs.size());
                return customInputIDs.stream().map(InputID::new).toArray(InputID[]::new);
            } catch (Exception e) {
                throw new StateComputationFailureException();
            }
        }

    }

    static final class OutputIDsArrayProvider implements StateProvider<OutputID[]> {

        Supplier<OutputID[]> m_outputIDsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_outputIDsSupplier = initializer.getValueSupplier(OutputIDsArrayRef.class);
        }

        @Override
        public OutputID[] computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            NodeContainer nc = NodeContext.getContext().getNodeContainer();
            if (nc == null) {
                throw new StateComputationFailureException();
            }

            WorkflowCaptureOperation captureOp;
            try {
                captureOp = nc.getParent().createCaptureOperationFor(nc.getID());

                //get 'connected' outputs only
                List<Output> outputs =
                        captureOp.getOutputs().stream().filter(Output::isConnected).collect(Collectors.toList());

                final var currentOutputIDs = Optional.ofNullable(m_outputIDsSupplier.get()).orElse(new OutputID[]{});
                var customOutputIDs = Arrays.stream(currentOutputIDs)
                        .map(outputID -> outputID.m_outputID).collect(Collectors.toList());
                customOutputIDs = ensureOutputIDsCount(customOutputIDs, outputs.size());
                return customOutputIDs.stream().map(OutputID::new).toArray(OutputID[]::new);
            } catch (Exception e) {
                throw new StateComputationFailureException();
            }
        }

    }

    static final class InputIDsArrayPersistor implements ArrayPersistor<Integer, InputID> {

        @Override
        public int getArrayLength(final NodeSettingsRO nodeSettings) throws InvalidSettingsException {
            return nodeSettings.getInt(CaptureWorkflowEndNodeModel.CFG_NUM_IDS, 0);
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public InputID createElementSaveDTO(final int index) {
            return new InputID();
        }

        @Override
        public void save(final List<InputID> savedElements, final NodeSettingsWO nodeSettings) {
            final var inputPortIds = savedElements.stream().map(inputPort -> inputPort.m_inputID).toList();
            for (int i = 0; i < inputPortIds.size(); i++) {
                nodeSettings.addString(CaptureWorkflowEndNodeModel.CFG_ID_PREFIX + i, inputPortIds.get(i));
            }
            nodeSettings.addInt(CaptureWorkflowEndNodeModel.CFG_NUM_IDS, inputPortIds.size());
        }

    }

    static final class OuputIDsArrayPersistor implements ArrayPersistor<Integer, OutputID> {

        @Override
        public int getArrayLength(final NodeSettingsRO nodeSettings) throws InvalidSettingsException {
            return nodeSettings.getInt(CaptureWorkflowEndNodeModel.CFG_NUM_IDS, 0);
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public OutputID createElementSaveDTO(final int index) {
            return new OutputID();
        }

        @Override
        public void save(final List<OutputID> savedElements, final NodeSettingsWO nodeSettings) {
            final var outputPortIds = savedElements.stream().map(outputPort -> outputPort.m_outputID).toList();
            for (int i = 0; i < outputPortIds.size(); i++) {
                nodeSettings.addString(CaptureWorkflowEndNodeModel.CFG_ID_PREFIX + i, outputPortIds.get(i));
            }
            nodeSettings.addInt(CaptureWorkflowEndNodeModel.CFG_NUM_IDS, outputPortIds.size());
        }

    }

    static final class InputID implements NodeParameters {

        InputID() {
            this("Input");
        }

        InputID(final String inputPortName) {
            m_inputID = inputPortName;
        }

        @Widget(title = "Input port", description = "")
        @TextInputWidget(patternValidation = ColumnNameValidation.class)
        @PersistArrayElement(InputIDPersistor.class)
        String m_inputID;

        static final class InputIDPersistor implements ElementFieldPersistor<String, Integer, InputID> {

            @Override
            public String load(final NodeSettingsRO nodeSettings, final Integer loadContext)
                throws InvalidSettingsException {
                return nodeSettings.getString(CaptureWorkflowEndNodeModel.CFG_ID_PREFIX + loadContext, "Input");
            }

            @Override
            public void save(final String param, final InputID saveDTO) {
                saveDTO.m_inputID = param;
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[][]{{CaptureWorkflowEndNodeModel.CFG_ID_PREFIX + ARRAY_INDEX_PLACEHOLDER}};
            }

        }

    }

    static final class OutputID implements NodeParameters {

        OutputID() {
            this("Output");
        }

        OutputID(final String outputPortName) {
            m_outputID = outputPortName;
        }

        @Widget(title = "Output port", description = "")
        @TextInputWidget(patternValidation = ColumnNameValidation.class)
        @PersistArrayElement(OutputIDPersistor.class)
        String m_outputID;

        static final class OutputIDPersistor implements ElementFieldPersistor<String, Integer, OutputID> {

            @Override
            public String load(final NodeSettingsRO nodeSettings, final Integer loadContext)
                throws InvalidSettingsException {
                return nodeSettings.getString(CaptureWorkflowEndNodeModel.CFG_ID_PREFIX + loadContext, "Output");
            }

            @Override
            public void save(final String param, final OutputID saveDTO) {
                saveDTO.m_outputID = param;
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[][]{{CaptureWorkflowEndNodeModel.CFG_ID_PREFIX + ARRAY_INDEX_PLACEHOLDER}};
            }

        }

    }

}
