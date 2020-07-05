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
 *   9 Dec 2019 (Marc Bux, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.buildworkflows.writer;

import static org.knime.buildworkflows.writer.IONodeConfig.addConnectAndConfigureIONodes;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.knime.core.data.container.DataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.exec.dataexchange.PortObjectIDSettings;
import org.knime.core.node.exec.dataexchange.in.PortObjectInNodeModel;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.NodeUIInformation;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowFragment.Input;
import org.knime.core.node.workflow.capture.WorkflowFragment.Output;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.util.FileUtil;
import org.knime.core.util.VMFileLocker;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.WorkflowAware;
import org.knime.filehandling.core.connections.base.UnixStylePathUtil;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.FileOverwritePolicy;
import org.knime.filehandling.core.node.portobject.writer.PortObjectToPathWriterNodeModel;

/**
 * Workflow writer node.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public final class WorkflowWriterNodeModel extends PortObjectToPathWriterNodeModel<WorkflowWriterNodeConfig> {

    WorkflowWriterNodeModel(final NodeCreationConfiguration creationConfig) {
        super(creationConfig, new WorkflowWriterNodeConfig(creationConfig));
    }

    @Override
    protected void writeToPath(final PortObject object, final Path outputPath, final ExecutionContext exec)
        throws Exception {

        CheckUtils.checkArgumentNotNull(object, "WorkflowPortObject must not be null.");
        CheckUtils.checkArgumentNotNull(outputPath, "Output Path must not be null.");
        CheckUtils.checkArgumentNotNull(exec, "Execution Context must not be null.");

        final WorkflowWriterNodeConfig config = getConfig();
        final WorkflowPortObjectSpec workflowPortObjectSpec =
            validateAndGetWorkflowPortObjectSpec(object.getSpec(), InvalidSettingsException::new);
        final Optional<String> err = validateWorkflowName(workflowPortObjectSpec,
            config.isUseCustomName().getBooleanValue(), config.getCustomName().getStringValue());
        if (err.isPresent()) {
            throw new InvalidSettingsException(err.get());
        }

        final WorkflowPortObject workflowPortObject = (WorkflowPortObject)object;
        final WorkflowFragment fragment = workflowPortObjectSpec.getWorkflowFragment();
        final boolean archive = config.isArchive().getBooleanValue();
        final boolean openAfterWrite = config.isOpenAfterWrite().getBooleanValue();
        final boolean overwrite =
            config.getFileChooserModel().getFileOverwritePolicy() == FileOverwritePolicy.OVERWRITE;

        // determine workflow name
        final String workflowName;
        if (config.isUseCustomName().getBooleanValue()) {
            workflowName = config.getCustomName().getStringValue();
        } else {
            final String originalName = workflowPortObjectSpec.getWorkflowName();
            if (originalName == null || originalName.isEmpty()) {
                throw new InvalidSettingsException(
                    "Default workflow name is null or empty. Consider using a custom workflow name.");
            }
            workflowName = determineWorkflowName(workflowPortObjectSpec);
            if (!originalName.equals(workflowName)) {
                setWarningMessage(String.format(
                    "Default workflow name \"%s\" contains illegal characters and has been escaped to \"%s\".",
                    originalName, workflowName));
            }
        }

        // create directory at output path, if applicable (parent path was already checked in super class)
        if (!Files.exists(outputPath)) {
            FSFiles.createDirectories(outputPath);
        }

        // resolve destination path and check if it is present already
        final Path dest;
        if (archive) {
            dest = outputPath.resolve(String.format("%s.knwf", workflowName));
        } else {
            dest = outputPath.resolve(workflowName);
        }
        if (Files.exists(dest)) {
            if (!overwrite) {
                throw new InvalidSettingsException(String
                    .format("Destination path \"%s\" exists and must not be overwritten due to user settings.", dest));
            }
            if (!archive && Files.exists(dest.resolve(VMFileLocker.LOCK_FILE))) {
                throw new InvalidSettingsException(String.format("To-be-overwritten workflow \"%s\" is locked.", dest));
            }
        }

        // create temporary local directory
        exec.setProgress(.33, () -> "Saving workflow to disk.");
        final File tmpDir = FileUtil.createTempDir("workflow-writer");
        final File localSource = write(tmpDir, workflowName, fragment, exec, config.getIONodes(), workflowPortObject,
            archive, this::setWarningMessage);

        final Path localSourcePath = localSource.toPath();

        // copy workflow from temporary source to desired destination
        exec.setProgress(.67, () -> "Copying workflow to destination.");
        final FileSystemProvider provider = dest.getFileSystem().provider();
        final boolean workflowAware = provider instanceof WorkflowAware;
        if (archive) {
            if (overwrite && Files.exists(dest)) {
                Files.copy(localSourcePath, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(localSourcePath, dest);
            }
        } else if (workflowAware) {
            ((WorkflowAware)provider).deployWorkflow(localSource, dest, overwrite, openAfterWrite);
        } else {
            try (final Stream<Path> streams = Files.walk(localSourcePath)) {
                for (final Path path : streams.collect(Collectors.toList())) {
                    final Path rel = localSourcePath.relativize(path);
                    final String relString = UnixStylePathUtil.asUnixStylePath(rel.toString());
                    final Path res = dest.resolve(relString);
                    exec.setMessage(() -> String.format("Copying file %s.", relString));
                    if (overwrite) {
                        try {
                            Files.copy(path, res, StandardCopyOption.REPLACE_EXISTING);
                        } catch (DirectoryNotEmptyException e) {
                            // we do not care about these when in overwrite mode
                        }
                    } else {
                        Files.copy(path, res);
                    }
                }
            }
        }
        FileUtil.deleteRecursively(tmpDir);
    }

    public static File write(final File tmpDir, final String workflowName, final WorkflowFragment fragment,
        final ExecutionContext exec, final SettingsModelIONodes ioNodes, final WorkflowPortObject workflowPortObject,
        final boolean archive, final Consumer<String> warningMessageConsumer) throws Exception {
        final File tmpWorkflowDir = new File(tmpDir, workflowName);
        tmpWorkflowDir.mkdir();
        final File tmpDataDir = new File(tmpWorkflowDir, "data");
        tmpDataDir.mkdir();

        final WorkflowManager wfm = fragment.loadWorkflow();
        wfm.setName(workflowName);
        try {
            addReferenceReaderNodes(fragment, wfm, tmpDataDir, exec);
            addIONodes(wfm, ioNodes, workflowPortObject, exec, warningMessageConsumer);

            wfm.save(tmpWorkflowDir, exec.createSubProgress(.34), false);
        } finally {
            fragment.disposeWorkflow();
        }

        // zip temporary directory if applicable
        if (!archive) {
            return tmpWorkflowDir;
        }
        final File localSource = new File(tmpDir, String.format("%s.knwf", workflowName));
        FileUtil.zipDir(localSource, tmpWorkflowDir, 9);
        return localSource;
    }

    private static void addReferenceReaderNodes(final WorkflowFragment fragment, final WorkflowManager wfm,
        final File tmpDataDir, final ExecutionContext exec)
        throws IOException, CanceledExecutionException, URISyntaxException, InvalidSettingsException {
        // create reference reader nodes and store their data in temp directory
        exec.setMessage(() -> "Introducing reference reader nodes.");
        final Set<NodeIDSuffix> portObjectReaderSufIds = fragment.getPortObjectReferenceReaderNodes();
        for (NodeIDSuffix portObjectReaderSufId : portObjectReaderSufIds) {

            final NodeID portObjectReaderId = portObjectReaderSufId.prependParent(wfm.getID());
            final NodeContainer portObjectReaderNC = wfm.findNodeContainer(portObjectReaderId);
            assert portObjectReaderNC instanceof NativeNodeContainer;
            final NodeModel portObjectReaderNM = ((NativeNodeContainer)portObjectReaderNC).getNodeModel();
            assert portObjectReaderNM instanceof PortObjectInNodeModel;
            final PortObjectInNodeModel portObjectReader = (PortObjectInNodeModel)portObjectReaderNM;
            final Optional<PortObject> poOpt = portObjectReader.getPortObject();
            assert poOpt.isPresent();
            final PortObject po = poOpt.get();

            final String poFileName = portObjectReaderSufId.toString().replace(":", "_");
            final URI poFileRelativeURI = new URI("knime://knime.workflow/data/" + poFileName);
            final File tmpPoFile = new File(tmpDataDir, poFileName);
            final PortObjectIDSettings poSettings = portObjectReader.getInputNodeSettingsCopy();
            if (po instanceof BufferedDataTable) {
                final BufferedDataTable table = (BufferedDataTable)po;
                DataContainer.writeToZip(table, tmpPoFile, exec.createSubProgress(.2 / portObjectReaderSufIds.size()));
                poSettings.setFileReference(poFileRelativeURI, true);
            } else {
                PortUtil.writeObjectToFile(po, tmpPoFile, exec.createSubProgress(.2 / portObjectReaderSufIds.size()));
                poSettings.setFileReference(poFileRelativeURI, false);
            }

            final NodeSettings settings = new NodeSettings("root");
            portObjectReaderNC.getParent().saveNodeSettings(portObjectReaderId, settings);
            final NodeSettingsWO modelSettings = settings.addNodeSettings("model");
            poSettings.saveSettings(modelSettings);
            portObjectReaderNC.getParent().loadNodeSettings(portObjectReaderId, settings);
        }
    }

    private static void addIONodes(final WorkflowManager wfm, final SettingsModelIONodes ioNodes,
        final WorkflowPortObject workflowPortObject, final ExecutionContext exec,
        final Consumer<String> warningMessageConsumer) throws InvalidSettingsException {
        exec.setMessage(() -> "Adding input and output nodes");

        ioNodes.initWithDefaults(workflowPortObject.getSpec().getInputIDs(),
            workflowPortObject.getSpec().getOutputIDs());

        //add, connect and configure input and output nodes
        int[] wfmb = NodeUIInformation.getBoundingBoxOf(wfm.getNodeContainers());
        List<String> configuredInputs = ioNodes.getConfiguredInputs(workflowPortObject.getSpec().getInputIDs());
        Map<String, Input> inputs = workflowPortObject.getSpec().getInputs();
        addConnectAndConfigureIONodes(wfm, configuredInputs, id -> inputs.get(id).getConnectedPorts().stream(),
            id -> ioNodes.getInputNodeConfig(id).get(), id -> workflowPortObject.getInputDataFor(id).orElse(null), true,
            wfmb);
        List<String> configuredOutputs = ioNodes.getConfiguredOutputs(workflowPortObject.getSpec().getOutputIDs());
        Map<String, Output> outputs = workflowPortObject.getSpec().getOutputs();
        addConnectAndConfigureIONodes(wfm, configuredOutputs, id -> {
            Optional<PortID> connectedPort = outputs.get(id).getConnectedPort();
            return connectedPort.isPresent() ? Stream.of(connectedPort.get()) : Stream.empty();
        }, id -> ioNodes.getOutputNodeConfig(id).get(), id -> workflowPortObject.getInputDataFor(id).orElse(null),
            false, wfmb);
        boolean unconnectedInputs = inputs.size() > configuredInputs.size();
        boolean unconnectedOutputs = outputs.size() > configuredOutputs.size();
        if (unconnectedInputs || unconnectedOutputs) {
            warningMessageConsumer.accept(
                "Some " + (unconnectedInputs ? "input" : "") + (unconnectedInputs && unconnectedOutputs ? " and " : "")
                    + (unconnectedOutputs ? "output" : "") + " ports are not connected.");
        }
    }

    public static Optional<String> validateWorkflowName(final WorkflowPortObjectSpec portObjectSpec,
        final boolean useCustomName, final String customName) {
        if (useCustomName) {
            if (customName.trim().isEmpty()) {
                return Optional.of("Custom workflow name is empty.");
            }
            final Matcher matcher = FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(customName);
            if (matcher.find()) {
                return Optional.of(String.format("Illegal character in custom workflow name \"%s\" at index %d.",
                    customName, matcher.start()));
            }
        } else if (determineWorkflowName(portObjectSpec).isEmpty()) {
            return Optional.of("Default workflow name is empty. Consider using a custom workflow name.");
        }
        return Optional.empty();
    }

    public static final String determineWorkflowName(final WorkflowPortObjectSpec spec) {
        return FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(spec.getWorkflowName()).replaceAll("_").trim();
    }

    public static <E extends Exception> WorkflowPortObjectSpec validateAndGetWorkflowPortObjectSpec(
        final PortObjectSpec spec, final Function<String, E> errorFunction) throws E {

        final WorkflowPortObjectSpec portObjectSpec = (WorkflowPortObjectSpec)spec;
        if (portObjectSpec == null) {
            throw errorFunction.apply("No workflow available.");
        }
        return portObjectSpec;
    }

}
