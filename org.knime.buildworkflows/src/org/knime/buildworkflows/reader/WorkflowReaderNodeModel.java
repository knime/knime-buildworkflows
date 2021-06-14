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
 *   Feb 10, 2020 (hornm): created
 */
package org.knime.buildworkflows.reader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.container.ContainerTable;
import org.knime.core.data.container.DataContainer;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.dialog.InputNode;
import org.knime.core.node.dialog.OutputNode;
import org.knime.core.node.exec.dataexchange.PortObjectIDSettings;
import org.knime.core.node.exec.dataexchange.PortObjectIDSettings.ReferenceType;
import org.knime.core.node.exec.dataexchange.PortObjectRepository;
import org.knime.core.node.exec.dataexchange.in.PortObjectInNodeModel;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResultEntry.LoadResultEntryType;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.node.workflow.capture.WorkflowSegment;
import org.knime.core.node.workflow.capture.WorkflowSegment.Input;
import org.knime.core.node.workflow.capture.WorkflowSegment.Output;
import org.knime.core.node.workflow.capture.WorkflowSegment.PortID;
import org.knime.core.node.workflow.virtual.AbstractPortObjectRepositoryNodeModel;
import org.knime.core.util.FileUtil;
import org.knime.core.util.LockFailedException;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.WorkflowAware;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.ReadPathAccessor;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;

/**
 * Workflow Reader node model.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowReaderNodeModel extends AbstractPortObjectRepositoryNodeModel {

    private final WorkflowReaderNodeConfig m_config;

    private final NodeModelStatusConsumer m_statusConsumer;

    protected WorkflowReaderNodeModel(final NodeCreationConfiguration creationConfig) {
        super(getPortsConfig(creationConfig).getInputPorts(), getPortsConfig(creationConfig).getOutputPorts());
        m_config = new WorkflowReaderNodeConfig(creationConfig);
        m_statusConsumer = new NodeModelStatusConsumer(EnumSet.of(MessageType.ERROR, MessageType.WARNING));
    }

    private static PortsConfiguration getPortsConfig(final NodeCreationConfiguration creationConfig) {
        return creationConfig.getPortConfig().orElseThrow(IllegalStateException::new);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        if (m_config.getWorkflowChooserModel().isDataAreaRelativeLocationSelected()) {
            throw new InvalidSettingsException("Data area relative location not supported");
        }
        m_config.getWorkflowChooserModel().configureInModel(inSpecs, m_statusConsumer);
        m_statusConsumer.setWarningsIfRequired(this::setWarningMessage);
        return null; // NOSONAR
    }

    @Override
    protected final PortObject[] execute(final PortObject[] data, final ExecutionContext exec) throws Exception {
        try (final ReadPathAccessor accessor = m_config.getWorkflowChooserModel().createReadPathAccessor()) {
            final List<FSPath> paths = accessor.getFSPaths(m_statusConsumer);
            assert paths.size() == 1;
            m_statusConsumer.setWarningsIfRequired(this::setWarningMessage);
            return readFromPath(paths.get(0), exec);
        } catch (NoSuchFileException e) {
            throw new IOException(String.format("The workflow '%s' does not exist.", e.getFile()), e);
        }
    }

    private PortObject[] readFromPath(final Path inputPath, final ExecutionContext exec) throws IOException,
        CanceledExecutionException, InvalidSettingsException, UnsupportedWorkflowVersionException, LockFailedException {
        File wfFile = toLocalWorkflowDir(inputPath);
        exec.setProgress("Reading workflow");
        WorkflowManager wfm = readWorkflow(wfFile, this::setWarningMessage, exec);
        if (wfm.canResetAll()) {
            setWarningMessage("The read workflow contains executed nodes which have been reset");
            wfm.resetAndConfigureAll();
        }

        String customWorkflowName = m_config.getWorkflowName().getStringValue();
        if (!StringUtils.isBlank(customWorkflowName)) {
            wfm.setName(customWorkflowName);
        } else {
            String wfName = inputPath.getFileName().toString();
            if (wfName.endsWith(".knwf")) {
                wfName = wfName.substring(0, wfName.length() - 5);
            }
            wfm.setName(wfName);
        }

        List<Input> inputs;
        List<Output> outputs;
        if (m_config.getRemoveIONodes().getBooleanValue()) {
            inputs = new ArrayList<>();
            outputs = new ArrayList<>();
            removeAndCollectContainerInputsAndOutputs(wfm, inputs, outputs);
        } else {
            inputs = Collections.emptyList();
            outputs = Collections.emptyList();
        }

        Set<NodeIDSuffix> portObjectReferenceReaderNodes = copyPortObjectReferenceReaderData(wfm, wfFile, exec);

        WorkflowSegment ws = new WorkflowSegment(wfm, inputs, outputs, portObjectReferenceReaderNodes);
        try {
            return new PortObject[]{new WorkflowPortObject(new WorkflowPortObjectSpec(ws, null,
                getIOIds(inputs.size(), m_config.getInputIdPrefix().getStringValue()),
                getIOIds(outputs.size(), m_config.getOutputIdPrefix().getStringValue())))};
        } finally {
            exec.setMessage("Finalizing");
            ws.serializeAndDisposeWorkflow();
        }
    }

    private static List<String> getIOIds(final int num, final String prefix) {
        if (num == 0) {
            return Collections.emptyList();
        } else if (num == 1) {
            return Arrays.asList(prefix.trim());
        } else {
            return IntStream.range(1, num + 1).mapToObj(i -> prefix + i).collect(Collectors.toList());
        }

    }

    private static WorkflowManager readWorkflow(final File wfFile, final Consumer<String> loadWarning,
        final ExecutionContext exec) throws IOException, InvalidSettingsException, CanceledExecutionException,
        UnsupportedWorkflowVersionException, LockFailedException {

        final WorkflowLoadHelper loadHelper = WorkflowSegment.createWorkflowLoadHelper(wfFile, loadWarning);
        final WorkflowLoadResult loadResult =
            WorkflowManager.EXTRACTED_WORKFLOW_ROOT.load(wfFile, exec, loadHelper, false);

        final WorkflowManager m = loadResult.getWorkflowManager();
        if (m == null) {
            throw new IOException(
                "Errors reading workflow: " + loadResult.getFilteredError("", LoadResultEntryType.Ok));
        } else {
            if (loadResult.getType() != LoadResultEntryType.Ok
                // we accept data load errors since the workflow manager is reset anyway (i.e. loaded without data)
                && loadResult.getType() != LoadResultEntryType.DataLoadError) {
                WorkflowManager.EXTRACTED_WORKFLOW_ROOT.removeProject(m.getID());
                NodeLogger.getLogger(WorkflowReaderNodeModel.class)
                    .error("Workflow couldn't be loaded.\n" + loadResult);
                throw new IOException("Workflow couldn't be loaded. Details in the log.");
            }
        }
        return loadResult.getWorkflowManager();
    }

    private static File toLocalWorkflowDir(final Path path) throws IOException {
        // the connected file system is either WorkflowAware or provides the workflow as a '.knwf'-file
        FileSystemProvider provider = path.getFileSystem().provider();
        if (provider instanceof WorkflowAware) {
            if (path.toString().endsWith(".knwf")) {
                throw new IOException("Not a workflow");
            }
            return ((WorkflowAware)provider).toLocalWorkflowDir(path);
        } else {
            try (InputStream in = FSFiles.newInputStream(path)) {
                return unzipToLocalDir(in);
            }
        }
    }

    private static File unzipToLocalDir(final InputStream in) throws IOException {
        File tmpDir = null;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            tmpDir = FileUtil.createTempDir("workflow_reader");
            FileUtil.unzip(zip, tmpDir, 1);
        }
        return tmpDir;
    }

    private static void removeAndCollectContainerInputsAndOutputs(final WorkflowManager wfm, final List<Input> inputs,
        final List<Output> outputs) {
        List<NodeID> nodesToRemove = new ArrayList<>();
        for (NodeContainer nc : wfm.getNodeContainers()) {
            if (nc instanceof NativeNodeContainer) {
                NativeNodeContainer nnc = (NativeNodeContainer)nc;
                if (collectInputs(wfm, inputs, nnc) || collectOutputs(wfm, outputs, nnc)) {
                    nodesToRemove.add(nnc.getID());
                }
            }
        }
        nodesToRemove.forEach(wfm::removeNode);
    }

    private static boolean collectOutputs(final WorkflowManager wfm, final List<Output> outputs,
        final NativeNodeContainer nnc) {
        if (nnc.getNodeModel() instanceof OutputNode) {
            for (ConnectionContainer cc : wfm.getIncomingConnectionsFor(nnc.getID())) {
                outputs.add(new Output(nnc.getInPort(cc.getDestPort()).getPortType(), null,
                    new PortID(NodeIDSuffix.create(wfm.getID(), cc.getSource()), cc.getSourcePort())));
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean collectInputs(final WorkflowManager wfm, final List<Input> inputs,
        final NativeNodeContainer nnc) {
        if (nnc.getNodeModel() instanceof InputNode) {
            for (int i = 0; i < nnc.getNrOutPorts(); i++) {
                Set<PortID> ports = wfm.getOutgoingConnectionsFor(nnc.getID(), i).stream()
                    .map(cc -> new PortID(NodeIDSuffix.create(wfm.getID(), cc.getDest()), cc.getDestPort()))
                    .collect(Collectors.toSet());
                if (!ports.isEmpty()) {
                    inputs.add(new Input(nnc.getOutputType(i), null, ports));
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private Set<NodeIDSuffix> copyPortObjectReferenceReaderData(final WorkflowManager wfm, final File wfFile,
        final ExecutionContext exec) throws IOException, CanceledExecutionException, InvalidSettingsException {
        Set<NodeIDSuffix> res = new HashSet<>();
        for (NodeContainer nc : wfm.getNodeContainers()) {
            if (nc instanceof NativeNodeContainer
                && ((NativeNodeContainer)nc).getNodeModel() instanceof PortObjectInNodeModel) {
                exec.setProgress("Copying data for node " + nc.getID());
                PortObjectInNodeModel portObjectReader =
                    (PortObjectInNodeModel)((NativeNodeContainer)nc).getNodeModel();
                final PortObjectIDSettings poSettings = portObjectReader.getInputNodeSettingsCopy();
                if (poSettings.getReferenceType() != ReferenceType.FILE) {
                    throw new IllegalStateException(
                        "Reference reader nodes expected to reference a file. But the reference type is "
                            + poSettings.getReferenceType());
                }
                URI uri = poSettings.getUri();
                File absoluteDataFile =
                    new File(wfFile, uri.toString().replace("knime://knime.workflow", ""));
                if (!absoluteDataFile.getCanonicalPath().startsWith(wfFile.getCanonicalPath())) {
                    throw new IllegalStateException(
                        "Trying to read in a data file outside of the workflow directory. Not allowed!");
                }
                PortObject po;
                try (InputStream in = absoluteDataFile.toURI().toURL().openStream()) {
                    po = readPortObject(exec, in, poSettings.isTable());
                }
                UUID id = UUID.randomUUID();
                addPortObject(id, po);
                PortObjectRepository.add(id, po);
                updatePortObjectReferenceReaderReference(wfm, nc.getID(), poSettings, id);
                res.add(NodeIDSuffix.create(wfm.getID(), nc.getID()));
            }
        }
        return res;
    }

    private static PortObject readPortObject(final ExecutionContext exec, final InputStream in, final boolean isTable)
        throws CanceledExecutionException, IOException {
        PortObject po;
        if (isTable) {
            try (ContainerTable table = DataContainer.readFromStream(in)) {
                po = exec.createBufferedDataTable(table, exec);
            }
        } else {
            po = PortUtil.readObjectFromStream(in, exec);
        }
        return po;
    }

    private static void updatePortObjectReferenceReaderReference(final WorkflowManager wfm, final NodeID nodeId,
        final PortObjectIDSettings poSettings, final UUID id) throws InvalidSettingsException {
        poSettings.setId(id);

        final NodeSettings settings = new NodeSettings("root");
        wfm.saveNodeSettings(nodeId, settings);
        final NodeSettingsWO modelSettings = settings.addNodeSettings("model");
        poSettings.saveSettings(modelSettings);
        wfm.loadNodeSettings(nodeId, settings);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_config.saveConfigurationForModel(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.validateConfigurationForModel(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.loadConfigurationForModel(settings);
    }

}
