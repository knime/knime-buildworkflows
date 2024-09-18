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
package org.knime.buildworkflows.converter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.zip.ZipInputStream;

import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.json.JSONCell;
import org.knime.core.data.json.JSONCellFactory;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.util.FileUtil;
import org.knime.core.util.LockFailedException;
import org.knime.core.util.hub.HubItemVersion;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.workflowaware.Entity;
import org.knime.filehandling.core.connections.workflowaware.WorkflowAwareUtil;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;
import org.knime.filehandling.core.util.TempPathCloseable;
import org.knime.shared.workflow.storage.multidir.loader.StandaloneLoader;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.knime.enterprise.utility.jackson.ObjectMapperUtil;

/**
 * Workflow Converter node model.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowConverterNodeModel extends NodeModel {

    private final WorkflowConverterNodeConfig m_config;

    private final NodeModelStatusConsumer m_statusConsumer;

    protected WorkflowConverterNodeModel(final NodeCreationConfiguration creationConfig) {
        super(getPortsConfig(creationConfig).getInputPorts(), getPortsConfig(creationConfig).getOutputPorts());
        m_config = new WorkflowConverterNodeConfig(creationConfig);
        m_statusConsumer = new NodeModelStatusConsumer(EnumSet.of(MessageType.ERROR, MessageType.WARNING));
    }

    private static PortsConfiguration getPortsConfig(final NodeCreationConfiguration creationConfig) {
        return creationConfig.getPortConfig().orElseThrow(IllegalStateException::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
        return new DataTableSpec[]{new DataTableSpec("Workflow converter",
            new DataColumnSpecCreator("output", DataType.getType(JSONCell.class)).createSpec())};
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws Exception {
        final var spec =
            new DataTableSpec(new DataColumnSpecCreator("output", DataType.getType(JSONCell.class)).createSpec());

        final var container = exec.createDataContainer(spec, false);

        container.addRowToTable(new DefaultRow("Row 0", JSONCellFactory.create(getWarningMessage())));

        container.close();
        return new BufferedDataTable[]{container.getTable()};
    }

    @Override
    protected final PortObject[] execute(final PortObject[] data, final ExecutionContext exec) throws Exception {
        try (final var accessor = m_config.getWorkflowChooserModel().createReadPathAccessor()) {
            final var path = accessor.getRootPath(m_statusConsumer);
            m_statusConsumer.setWarningsIfRequired(this::setWarningMessage);

            var json = readFromPath(path, exec);

            final var spec =
                new DataTableSpec(new DataColumnSpecCreator("output", DataType.getType(JSONCell.class)).createSpec());

            final var container = exec.createDataContainer(spec, false);

            container.addRowToTable(new DefaultRow("Row 0", JSONCellFactory.create(json)));

            container.close();
            return new BufferedDataTable[]{container.getTable()};
        } catch (NoSuchFileException e) {
            // if version is not current state add a hint that the version might not be available
            final var itemVersion = m_config.getWorkflowChooserModel().getItemVersion();
            final var versionWarning = switch (itemVersion.linkType()) {
                // current state is always available, cannot be a problem
                case LATEST_STATE -> "";
                // path might be wrong or version not available
                case LATEST_VERSION -> " or does not have any versions";
                case FIXED_VERSION -> " or does not have version " + itemVersion.versionNumber();
            };
            throw new IOException(String.format("The workflow '%s' does not exist%s.", e.getFile(), versionWarning), e);
        }
    }

    private static void ensureIsWorkflow(final FSPath path) throws IOException {
        final boolean isWorkflow;
        if (WorkflowAwareUtil.isWorkflowAwarePath(path)) {
            isWorkflow = WorkflowAwareUtil.getWorkflowAwareEntityOf(path) //
                .map(Entity.WORKFLOW::equals) //
                .orElse(false);
        } else {
            isWorkflow = path.toString().endsWith(".knwf");
        }

        if (!isWorkflow) {
            throw new IOException("Not a workflow");
        }
    }

    private String readFromPath(final FSPath inputPath, final ExecutionContext exec)
        throws IOException, CanceledExecutionException, InvalidSettingsException, UnsupportedWorkflowVersionException,
        LockFailedException, KNIMEException {

        exec.setProgress("Converting workflow");

        try (var wfTempFolder = toLocalWorkflowDir(inputPath, m_config.getWorkflowChooserModel().getItemVersion())) {
            final var wf = StandaloneLoader.load(wfTempFolder.getTempFileOrFolder().toFile());

            final var mapper = ObjectMapperUtil.getInstance().getObjectMapper().copy();

            if (m_config.getIndent().getBooleanValue()) {
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
            } else {
                mapper.disable(SerializationFeature.INDENT_OUTPUT);
            }

            if (m_config.getRemoveEmptyValues().getBooleanValue()) {
                mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            } else {
                mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            }

            final var dismiss = new HashSet<String>(8);

            if(m_config.getRemoveAnnotations().getBooleanValue()) {
                dismiss.add("annotations");
                dismiss.add("annotation");
            }

            if(m_config.getRemoveMetadata().getBooleanValue()) {
                dismiss.add("metadata");
            }

            if(m_config.getRemoveUIInfo().getBooleanValue()) {
                dismiss.add("uiInfo");
            }

            if(m_config.getRemoveFeature().getBooleanValue()) {
                dismiss.add("feature");
            }

            if(m_config.getRemoveBundle().getBooleanValue()) {
                dismiss.add("bundle");
            }

            final var filter = new SimpleFilterProvider().addFilter("dynamicFilter",
                SimpleBeanPropertyFilter.serializeAllExcept(dismiss));


            return mapper.addMixIn(Object.class, PropertyFilterMixIn.class).writer(filter).writeValueAsString(wf);
        }
    }

    @JsonFilter("dynamicFilter")
    private class PropertyFilterMixIn {
        // It's needed so that the property filter works as expected,
        // As otherwise we would have to annotated the actual POJOs
        // that we want to serialize.
    }

    @SuppressWarnings("resource")
    private static TempPathCloseable toLocalWorkflowDir(final FSPath path, final HubItemVersion version)
        throws IOException {
        // the connected file system is either WorkflowAware or provides the workflow as a '.knwf'-file

        ensureIsWorkflow(path);

        final var fs = path.getFileSystem();

        // Try with version
        final var wfvAware = fs.getItemVersionAware();
        if (wfvAware.isPresent() && version != null) {
            return wfvAware.get().downloadWorkflowAtVersion(path, version);
        }

        // Try to fetch workflow
        final var wfAware = fs.getWorkflowAware();
        if (wfAware.isPresent()) {
            return wfAware.get().toLocalWorkflowDir(path);
        }

        // Try plain input stream
        try (var in = FSFiles.newInputStream(path)) {
            return unzipToLocalDir(in);
        }
    }

    private static TempPathCloseable unzipToLocalDir(final InputStream in) throws IOException {
        File tmpDir = null;
        try (var zip = new ZipInputStream(in)) {
            tmpDir = FileUtil.createTempDir("workflow_reader");
            FileUtil.unzip(zip, tmpDir, 1);
        }
        return new TempPathCloseable(tmpDir.toPath());
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

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // do nothing
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // do nothing
    }

    @Override
    protected void reset() {
        // do nothing
    }
}
