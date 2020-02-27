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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;
import org.knime.core.data.container.DataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.exec.dataexchange.PortObjectIDSettings;
import org.knime.core.node.exec.dataexchange.in.PortObjectInNodeModel;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.NodeTimer;
import org.knime.core.node.workflow.NodeUIInformation;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowFragment.Input;
import org.knime.core.node.workflow.capture.WorkflowFragment.Output;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.util.FileUtil;
import org.knime.core.util.VMFileLocker;
import org.knime.filehandling.core.node.portobject.writer.PortObjectToPathWriterNodeModel;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.filesystem.ExplorerFileSystem;
import org.knime.workbench.explorer.view.ContentObject;
import org.knime.workbench.explorer.view.ExplorerView;

/**
 * Workflow writer node.
 *
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowWriterNodeModel extends PortObjectToPathWriterNodeModel<WorkflowWriterNodeConfig> {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowWriterNodeModel.class);

    WorkflowWriterNodeModel(final NodeCreationConfiguration creationConfig) {
        super(creationConfig, new WorkflowWriterNodeConfig());
    }

    @Override
    protected void writeToPath(final PortObject object, final Path outputPath, final ExecutionContext exec)
        throws Exception {

        CheckUtils.checkArgumentNotNull(object, "WorkflowPortObject must not be null.");
        CheckUtils.checkArgumentNotNull(outputPath, "Output Path must not be null.");
        CheckUtils.checkArgumentNotNull(exec, "Execution Context must not be null.");

        final WorkflowPortObject workflowPortObject = (WorkflowPortObject)object;
        final WorkflowFragment fragment = workflowPortObject.getSpec().getWorkflowFragment();
        final WorkflowWriterNodeConfig config = getConfig();
        final boolean archive = config.isArchive().getBooleanValue();
        final boolean overwrite = config.getOverwriteModel().getBooleanValue();

        // determine workflow name
        final String workflowName;
        if (config.isUseCustomName().getBooleanValue()) {
            workflowName = config.getCustomName().getStringValue();
        } else {
            final String originalName = workflowPortObject.getSpec().getWorkflowName();
            if (originalName == null || originalName.isEmpty()) {
                throw new InvalidSettingsException(
                    "Default workflow name is null or empty. Consider using a custom workflow name.");
            }
            workflowName = FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(originalName).replaceAll("_");
            if (!originalName.equals(workflowName)) {
                setWarningMessage(String.format(
                    "Default workflow name \"%s\" contains illegal characters and has been escaped to \"%s\".",
                    originalName, workflowName));
            }
        }

        // create parent directories, if applicable
        final boolean parentDirectoriesCreated;
        if (config.getCreateDirectoryModel().getBooleanValue() && !Files.exists(outputPath)) {
            exec.setMessage(() -> "Creating parent directory.");
            Files.createDirectories(outputPath);
            parentDirectoriesCreated = true;
        } else {
            parentDirectoriesCreated = false;
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
        final File tmpDir = FileUtil.createTempDir("workflow-writer");
        final File tmpWorkflowDir = new File(tmpDir, workflowName);
        tmpWorkflowDir.mkdir();
        final File tmpDataDir = new File(tmpWorkflowDir, "data");
        tmpDataDir.mkdir();

        final WorkflowManager wfm = fragment.loadWorkflow();
        wfm.setName(workflowName);
        try {
            addReferenceReaderNodes(fragment, wfm, tmpDataDir, exec);
            addIONodes(wfm, config, workflowPortObject, exec);

            exec.setProgress(.2, () -> "Saving workflow to disk.");
            // write workflow to temporary directory
            wfm.save(tmpWorkflowDir, exec.createSubProgress(.2), false);
        } finally {
            fragment.disposeWorkflow();
        }

        // zip temporary directory if applicable and resolve source path
        final Path source;
        if (archive) {
            final File knwf = new File(tmpDir, String.format("%s.knwf", workflowName));
            FileUtil.zipDir(knwf, tmpWorkflowDir, 9);
            source = knwf.toPath();
        } else {
            source = tmpWorkflowDir.toPath();
        }

        // copy workflow from temporary source to desired destination
        exec.setProgress(.6, () -> "Copying workflow to destination.");
        for (Path path : Files.walk(source).collect(Collectors.toList())) {
            final Path rel = source.relativize(path);
            final Path res = dest.resolve(rel.toString());
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
        FileUtil.deleteRecursively(tmpDir);

        exec.setProgress(.8, () -> "Refreshing project explorer.");
        final AbstractExplorerFileStore localFileStore;
        final File localFile;
        final ExplorerFileSystem fs = ExplorerMountTable.getFileSystem();
        switch (getConfig().getFileChooserModel().getFileSystemChoice().getType()) {
            case LOCAL_FS:
            case KNIME_FS:
                localFile = archive ? dest.toAbsolutePath().toFile()
                    : dest.toAbsolutePath().resolve(WorkflowPersistor.WORKFLOW_FILE).toFile();
                localFileStore = fs.fromLocalFile(localFile);
                break;
            case KNIME_MOUNTPOINT:
            case CUSTOM_URL_FS:
                localFileStore = archive ? fs.getStore(dest.toUri())
                    : fs.getStore(dest.toUri()).getChild(WorkflowPersistor.WORKFLOW_FILE);
                localFile = localFileStore.toLocalFile();
                break;
            default:
                localFileStore = null;
                localFile = null;
        }

        if (PlatformUI.isWorkbenchRunning() && localFileStore != null && localFile != null && localFile.exists()) {
            final IWorkbench workbenck = PlatformUI.getWorkbench();

            // refresh project explorer, if applicable
            Arrays.stream(workbenck.getWorkbenchWindows()).flatMap(window -> Arrays.stream(window.getPages()))
                .flatMap(page -> Arrays.stream(page.getViewReferences()))
                .filter(ref -> ref.getId().equals(ExplorerView.ID)).map(ref -> (ExplorerView)ref.getView(true))
                .map(ExplorerView::getViewer).findAny().ifPresent(viewer -> {
                    viewer.getControl().getDisplay().asyncExec(() -> {
                        final AbstractExplorerFileStore parentDir =
                            archive ? localFileStore.getParent() : localFileStore.getParent().getParent();
                        viewer.refresh(ContentObject.forFile(parentDir));
                        // for some reason, refresh does not work if the parentOfWorkflowDir is the root
                        if (parentDirectoriesCreated || parentDir.getParent() == null) {
                            viewer.refresh(null);
                        }
                    });
                });

            // open workflow, if applicable
            if (!archive && config.isOpenAfterWrite().getBooleanValue()) {
                Display.getDefault().asyncExec(() -> {
                    try {
                        final IEditorDescriptor editorDescriptor =
                            IDE.getEditorDescriptor(localFileStore.getName(), true, true);
                        workbenck.getActiveWorkbenchWindow().getActivePage()
                            .openEditor(new FileStoreEditorInput(localFileStore), editorDescriptor.getId());
                        NodeTimer.GLOBAL_TIMER.incWorkflowOpening();
                    } catch (PartInitException ex) {
                        LOGGER.info(String.format("Could not open editor for new workflow %s: %s.", workflowName,
                            ex.getMessage()), ex);
                    }
                });
            }
        }
    }

    private static void addReferenceReaderNodes(final WorkflowFragment fragment, final WorkflowManager wfm,
        final File tmpDataDir, final ExecutionContext exec)
        throws IOException, CanceledExecutionException, URISyntaxException, InvalidSettingsException {
        // create reference reader nodes and store their data in temp directory
        exec.setMessage(() -> "Introducing reference reader nodes.");
        final Set<NodeIDSuffix> portObjectReaderSufIds = fragment.getPortObjectReferenceReaderNodes();
        for (NodeIDSuffix portObjectReaderSufId : portObjectReaderSufIds) {

            final NodeID portObjectReaderId = portObjectReaderSufId.prependParent(wfm.getID());
            final NodeContainer portObjectReaderNC = wfm.getNodeContainer(portObjectReaderId);
            assert portObjectReaderNC instanceof NativeNodeContainer;
            final NodeModel portObjectReaderNM = ((NativeNodeContainer)portObjectReaderNC).getNodeModel();
            assert portObjectReaderNM instanceof PortObjectInNodeModel;
            final PortObjectInNodeModel portObjectReader = (PortObjectInNodeModel)portObjectReaderNM;
            final Optional<PortObject> poOpt = portObjectReader.getPortObject();
            assert poOpt.isPresent();
            final PortObject po = poOpt.get();

            final String poFileName = portObjectReaderSufId.toString().replaceAll(":", "_");
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

    private void addIONodes(final WorkflowManager wfm, final WorkflowWriterNodeConfig config,
        final WorkflowPortObject workflowPortObject, final ExecutionContext exec) throws InvalidSettingsException {
        exec.setMessage(() -> "Adding input and output nodes");
        //add, connect and configure input and output nodes
        int[] wfmb = NodeUIInformation.getBoundingBoxOf(wfm.getNodeContainers());
        List<String> configuredInputs =
            config.getIONodes().getConfiguredInputs(workflowPortObject.getSpec().getInputIDs());
        Map<String, Input> inputs = workflowPortObject.getSpec().getInputs();
        addConnectAndConfigureIONodes(wfm, configuredInputs, id -> inputs.get(id).getConnectedPorts().stream(),
            id -> config.getIONodes().getInputNodeConfig(id).get(),
            id -> workflowPortObject.getInputDataFor(id).orElse(null), true, wfmb);
        List<String> configuredOutputs =
            config.getIONodes().getConfiguredOutputs(workflowPortObject.getSpec().getOutputIDs());
        Map<String, Output> outputs = workflowPortObject.getSpec().getOutputs();
        addConnectAndConfigureIONodes(wfm, configuredOutputs, id -> {
            Optional<PortID> connectedPort = outputs.get(id).getConnectedPort();
            return connectedPort.isPresent() ? Stream.of(connectedPort.get()) : Stream.empty();
        }, id -> config.getIONodes().getOutputNodeConfig(id).get(),
            id -> workflowPortObject.getInputDataFor(id).orElse(null), false, wfmb);
        boolean unconnectedInputs = inputs.size() > configuredInputs.size();
        boolean unconnectedOutputs = outputs.size() > configuredOutputs.size();
        if (unconnectedInputs || unconnectedOutputs) {
            setWarningMessage(
                "Some " + (unconnectedInputs ? "input" : "") + (unconnectedInputs && unconnectedOutputs ? " and " : "")
                    + (unconnectedOutputs ? "output" : "") + " ports are not connected.");
        }
    }

}
