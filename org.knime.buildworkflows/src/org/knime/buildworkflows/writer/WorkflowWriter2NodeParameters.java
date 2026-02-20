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

package org.knime.buildworkflows.writer;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.knime.buildworkflows.ExistsOption;
import org.knime.buildworkflows.writer.WorkflowInputNodeParameters.InputNodeConfigType;
import org.knime.buildworkflows.writer.WorkflowOutputNodeParameters.OutputNodeConfigType;
import org.knime.buildworkflows.writer.WorkflowWriter2NodeParameterUtil.IOIDsProvider;
import org.knime.buildworkflows.writer.WorkflowWriter2NodeParameterUtil.IONodesArrayPersistor;
import org.knime.buildworkflows.writer.WorkflowWriter2NodeParameterUtil.IONodesProvider;
import org.knime.buildworkflows.writer.WorkflowWriter2NodeParameterUtil.MissingPortsMessage;
import org.knime.buildworkflows.writer.WorkflowWriter2NodeParameterUtil.WorkflowPortObjectSpecProvider;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelectionWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSystemOption;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.SingleFileSelectionMode;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.WithFileSystem;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArray;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.array.ArrayWidget.ElementLayout;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.legacy.LegacyFileWriterWithCreateMissingFolders;
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
import org.knime.node.parameters.widget.choices.EnumChoice;
import org.knime.node.parameters.widget.choices.EnumChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.RadioButtonsWidget;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.message.TextMessage;

/**
 * Node parameters for Workflow Writer.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
final class WorkflowWriter2NodeParameters implements NodeParameters {

    static final String CFG_NUM_INPUTS = "num_inputs";

    static final String CFG_NUM_OUTPUTS = "num_outputs";

    @Section(title = "Deployment Options")
    interface DeploymentOptionsSection {
    }

    @Section(title = "Workflow Inputs and Outputs")
    @After(DeploymentOptionsSection.class)
    @Effect(predicate = WorkflowInputIsMissing.class, type = EffectType.SHOW)
    interface WorkflowInputsAndOutputsSection {
    }

    @Section(title = "Workflow Inputs")
    @After(WorkflowInputsAndOutputsSection.class)
    @Effect(predicate = WorkflowInputIsMissing.class, type = EffectType.HIDE)
    interface WorkflowInputsSection {
    }

    @Section(title = "Workflow Outputs")
    @After(WorkflowInputsSection.class)
    @Effect(predicate = WorkflowInputIsMissing.class, type = EffectType.HIDE)
    interface WorkflowOutputsSection {
    }

    @Persist(configKey = WorkflowWriterNodeConfig.CFG_FOLDER_CHOOSER)
    @Modification(LegacyFileWriterModifier.class)
    @ValueReference(DestinationFolderRef.class)
    LegacyFileWriterWithCreateMissingFolders m_folder = new LegacyFileWriterWithCreateMissingFolders();

    static final class DestinationFolderRef implements ParameterReference<LegacyFileWriterWithCreateMissingFolders> {
    }

    static final class LegacyFileWriterModifier implements LegacyFileWriterWithCreateMissingFolders.Modifier {

        @Override
        public void modify(final WidgetGroupModifier group) {
            final var fileSelection = findFileSelection(group);
            fileSelection.modifyAnnotation(Widget.class).withProperty("title", "Folder")//
                .withProperty("description", "Enter a path to a folder where the workflow should be written to.")
                .modify();
            fileSelection.addAnnotation(FileSelectionWidget.class).withProperty("value", SingleFileSelectionMode.FOLDER)
                .modify();
            fileSelection.addAnnotation(WithFileSystem.class).withProperty("value", new FileSystemOption[]{
                FileSystemOption.LOCAL, FileSystemOption.SPACE, FileSystemOption.EMBEDDED, FileSystemOption.CONNECTED})
                .modify();
        }

    }

    @Widget(title = "If exists", description = """
            Specify the behavior of the node in case the output file already exists.
            """)
    @ValueSwitchWidget
    @Persist(configKey = WorkflowWriterNodeConfig.CFG_EXISTS_OPTION)
    @ValueReference(ExistsOptionRef.class)
    ExistsOption m_existsOption = ExistsOption.FAIL;

    static final class ExistsOptionRef implements ParameterReference<ExistsOption> {
    }

    @Widget(title = "Use custom workflow name", description = """
            If checked, a custom workflow name is used as specified below.
            """)
    @Persist(configKey = WorkflowWriterNodeConfig.CFG_USE_CUSTOM_NAME)
    @ValueReference(UseCustomNameRef.class)
    boolean m_useCustomName;

    static final class UseCustomNameRef implements BooleanReference {
    }

    @Widget(title = "Custom workflow name", description = """
            A customizable name for the to-be-written workflow.
            """)
    @Persist(configKey = WorkflowWriterNodeConfig.CUSTOM_NAME)
    @Effect(predicate = UseCustomNameRef.class, type = EffectType.SHOW)
    String m_customName = "workflow";

    @Layout(DeploymentOptionsSection.class)
    @Widget(title = "Output", description = """
            Select how the workflow should be written.
            """)
    @RadioButtonsWidget
    @Persistor(OutputModePersistor.class)
    @ChoicesProvider(OutputModeChoicesProvider.class)
    @ValueProvider(OutputModeProvider.class)
    @ValueReference(OutputModeRef.class)
    OutputMode m_outputMode = OutputMode.WRITE;

    static final class OutputModeRef implements ParameterReference<OutputMode> {
    }

    @Layout(DeploymentOptionsSection.class)
    @Widget(title = "Update links of components and metanodes", description = """
            Whether to update linked metanodes and components before writing the workflow segment.
            If this is enabled and links are also set to be disconnected, the metanodes/components will first be
            updated and then disconnected.
            """)
    @Persist(configKey = WorkflowWriterNodeConfig.CFG_DO_UPDATE_LINKS)
    boolean m_doUpdateTemplateLinks = WorkflowWriterNodeConfig.DO_UPDATE_LINKS_DEFAULT;

    @Layout(DeploymentOptionsSection.class)
    @Widget(title = "Disconnect links of components and metanodes", description = """
            Whether to disconnect (remove) links of linked metanodes and components before writing the workflow
            segment.
            """)
    @Persist(configKey = WorkflowWriterNodeConfig.CFG_DO_REMOVE_LINKS)
    boolean m_doRemoveTemplateLinks = WorkflowWriterNodeConfig.DO_REMOVE_LINKS_DEFAULT;

    @Layout(WorkflowInputsAndOutputsSection.class)
    @TextMessage(MissingWorkflowInputMessage.class)
    Void m_missingWorkflowInputMessage;

    @Layout(WorkflowInputsSection.class)
    @TextMessage(MissingInputPortsMessage.class)
    Void m_missingInputPortsMessage;

    @Layout(WorkflowInputsSection.class)
    @Widget(title = "Add input nodes", description = """
            Allows one to add input nodes connected to the workflow inputs prior to writing. Depending on the selected
            node, there is a limited set of options available to pre-configure the node to be added. Furthermore, if
            tabular input data is stored with the input, it is used as example input data for added input nodes.
            """)
    @PersistWithin(WorkflowWriterNodeConfig.CFG_IO_NODES)
    @PersistArray(InputNodesArrayPersistor.class)
    @ArrayWidget(elementLayout = ElementLayout.VERTICAL_CARD, hasFixedSize = true)
    @ValueProvider(InputNodesProvider.class)
    @ValueReference(InputNodesRef.class)
    WorkflowInputNodeParameters[] m_inputNodes = new WorkflowInputNodeParameters[0];

    static final class InputNodesRef implements ParameterReference<WorkflowInputNodeParameters[]> {
    }

    @Layout(WorkflowOutputsSection.class)
    @TextMessage(MissingOutputPortsMessage.class)
    Void m_missingOutputPortsMessage;

    @Layout(WorkflowOutputsSection.class)
    @Widget(title = "Add output nodes", description = """
            Allows one to add output nodes connected to the workflow outputs prior to writing. Depending on the
            selected node, there is a limited set of options available to pre-configure the node to be added.
            """)
    @PersistWithin(WorkflowWriterNodeConfig.CFG_IO_NODES)
    @PersistArray(OutputNodesArrayPersistor.class)
    @ArrayWidget(elementLayout = ElementLayout.VERTICAL_CARD, hasFixedSize = true)
    @ValueProvider(OutputNodesProvider.class)
    @ValueReference(OutputNodesRef.class)
    WorkflowOutputNodeParameters[] m_outputNodes = new WorkflowOutputNodeParameters[0];

    static final class OutputNodesRef implements ParameterReference<WorkflowOutputNodeParameters[]> {
    }

    static final class WorkflowInputIsMissing implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(WorkflowInputIsMissing::checkWorkflowInput);
        }

        private static boolean checkWorkflowInput(final NodeParametersInput parametersInput) {
            final var workflowInputPortIndex = parametersInput.getPortsConfiguration().getInputPortLocation()
                    .get(WorkflowWriter2NodeParameterUtil.PORT_OBJECT_INPUT_GRP_NAME)[0];
            return parametersInput.getInPortSpec(workflowInputPortIndex).isEmpty();
        }

    }

    static final class OutputModeProvider implements StateProvider<OutputMode> {

        Supplier<OutputMode> m_outputModeSupplier;

        Supplier<Boolean> m_isOpenOutputModeEnabledSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_outputModeSupplier = initializer.getValueSupplier(OutputModeRef.class);
            m_isOpenOutputModeEnabledSupplier =
                initializer.computeFromProvidedState(IsOpenOutputModeEnabledProvider.class);
        }

        @Override
        public OutputMode computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            final var isOpenOutputModeEnabled = m_isOpenOutputModeEnabledSupplier.get();
            if (!isOpenOutputModeEnabled && m_outputModeSupplier.get() == OutputMode.OPEN) {
                return OutputMode.WRITE;
            }
            throw new StateComputationFailureException();
        }

    }

    private static final class OutputModeChoicesProvider implements EnumChoicesProvider<OutputMode> {

        Supplier<Boolean> m_isOpenOutputModeEnabledSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            EnumChoicesProvider.super.init(initializer);
            m_isOpenOutputModeEnabledSupplier =
                initializer.computeFromProvidedState(IsOpenOutputModeEnabledProvider.class);
        }

        @Override
        public List<EnumChoice<OutputMode>> computeState(final NodeParametersInput context) {
            return List.of( //
                EnumChoice.fromEnumConst(OutputMode.WRITE), //
                EnumChoice.fromEnumConst(OutputMode.OPEN, !m_isOpenOutputModeEnabledSupplier.get()), //
                EnumChoice.fromEnumConst(OutputMode.EXPORT));
        }

    }

    static final class IsOpenOutputModeEnabledProvider implements StateProvider<Boolean> {

        private Supplier<LegacyFileWriterWithCreateMissingFolders> m_destinationFolderSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_destinationFolderSupplier = initializer.computeFromValueSupplier(DestinationFolderRef.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            final var destinationFolder = m_destinationFolderSupplier.get();
            if (destinationFolder == null) {
                return false;
            }
            return Stream.of(FSCategory.RELATIVE, FSCategory.MOUNTPOINT)
                .anyMatch(c -> c == destinationFolder.getFileSelection().getFSLocation().getFSCategory());
        }

    }

    static final class InputNodesProvider extends IONodesProvider<WorkflowInputNodeParameters> {

        public InputNodesProvider() {
            super(InputIDsProvider.class, InputNodesRef.class,
                id -> new WorkflowInputNodeParameters(id, InputNodeConfigType.WORKFLOW_INPUT,
                    InputNodeConfigType.WORKFLOW_INPUT.getInputNodeConfig().getDefaultParameterName()),
                id -> new WorkflowInputNodeParameters(id, InputNodeConfigType.NONE, null),
                n -> n.m_inputId,
                WorkflowInputNodeParameters[]::new);
        }

    }

    static final class OutputNodesProvider extends IONodesProvider<WorkflowOutputNodeParameters> {

        public OutputNodesProvider() {
            super(OutputIDsProvider.class, OutputNodesRef.class,
                id -> new WorkflowOutputNodeParameters(id, OutputNodeConfigType.WORKFLOW_OUTPUT,
                    OutputNodeConfigType.WORKFLOW_OUTPUT.getOutputNodeConfig().getDefaultParameterName()),
                id -> new WorkflowOutputNodeParameters(id, OutputNodeConfigType.NONE, null),
                n -> n.m_outputId,
                WorkflowOutputNodeParameters[]::new);
        }

    }

    static final class InputIDsProvider extends IOIDsProvider {

        InputIDsProvider() {
            super(WorkflowPortObjectSpec::getInputs);
        }

    }

    static final class OutputIDsProvider extends IOIDsProvider {

        OutputIDsProvider() {
            super(WorkflowPortObjectSpec::getOutputs);
        }

    }

    static final class MissingWorkflowInputMessage implements StateProvider<Optional<TextMessage.Message>> {

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
                return Optional.of(new TextMessage.Message("No input spec available",
                    "Missing workflow input connection", TextMessage.MessageType.WARNING));
            }
            return Optional.empty();
        }

    }

    static final class MissingInputPortsMessage extends MissingPortsMessage {

        public MissingInputPortsMessage() {
            super(workflowPortObjectSpec -> workflowPortObjectSpec.getInputs().entrySet().stream().map(Entry::getKey)
                .collect(Collectors.toList()), "input");
        }

    }

    static final class MissingOutputPortsMessage extends MissingPortsMessage {

        public MissingOutputPortsMessage() {
            super(workflowPortObjectSpec -> workflowPortObjectSpec.getOutputs().entrySet().stream().map(Entry::getKey)
                .collect(Collectors.toList()), "output");
        }

    }

    static final class OutputModePersistor implements NodeParametersPersistor<OutputMode> {

        @Override
        public OutputMode load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final boolean isArchive = settings.getBoolean(WorkflowWriterNodeConfig.CFG_ARCHIVE);
            final boolean isOpenAfterWrite = settings.getBoolean(WorkflowWriterNodeConfig.CFG_OPEN_AFTER_WRITE);

            if (isArchive) {
                return OutputMode.EXPORT;
            } else if (isOpenAfterWrite) {
                return OutputMode.OPEN;
            } else {
                return OutputMode.WRITE;
            }
        }

        @Override
        public void save(final OutputMode param, final NodeSettingsWO settings) {
            settings.addBoolean(WorkflowWriterNodeConfig.CFG_ARCHIVE, param == OutputMode.EXPORT);
            settings.addBoolean(WorkflowWriterNodeConfig.CFG_OPEN_AFTER_WRITE, param == OutputMode.OPEN);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{WorkflowWriterNodeConfig.CFG_ARCHIVE},
                {WorkflowWriterNodeConfig.CFG_OPEN_AFTER_WRITE}};
        }
    }

    static final class InputNodesArrayPersistor extends IONodesArrayPersistor<WorkflowInputNodeParameters> {

        public InputNodesArrayPersistor() {
            super(CFG_NUM_INPUTS, WorkflowInputNodeParameters.CFG_KEY_INPUT_NODE,
                WorkflowInputNodeParameters.CFG_KEY_INPUT_ID,
                n -> n.m_inputId, n -> n.m_nodeConfigClass.getInputNodeConfig(), n -> n.m_parameterName,
                WorkflowInputNodeParameters::new);
        }

    }

    static final class OutputNodesArrayPersistor extends IONodesArrayPersistor<WorkflowOutputNodeParameters> {

        public OutputNodesArrayPersistor() {
            super(CFG_NUM_OUTPUTS, WorkflowOutputNodeParameters.CFG_KEY_OUTPUT_NODE,
                WorkflowOutputNodeParameters.CFG_KEY_OUTPUT_ID,
                n -> n.m_outputId, n -> n.m_nodeConfigClass.getOutputNodeConfig(), n -> n.m_parameterName,
                WorkflowOutputNodeParameters::new);
        }

    }

    enum OutputMode {
            @Label(value = "Write workflow", description = """
                    If selected, the workflow will be written into a folder and the KNIME Explorer view will be
                    refreshed.
                    """)
            WRITE,

            @Label(value = "Write workflow and open in explorer", description = """
                    If selected, the workflow will be written into a folder, the KNIME Explorer view will be
                    refreshed and the written workflow will be opened in KNIME Analytics Platform.
                    """)
            OPEN,

            @Label(value = "Export workflow as knwf archive", description = """
                    If selected, the workflow will be written as a .knwf archive, just as if it were exported from
                    KNIME Analytics Platform. This archive can be imported into other installations of KNIME
                    Analytics Platform.
                    """)
            EXPORT
    }

}
