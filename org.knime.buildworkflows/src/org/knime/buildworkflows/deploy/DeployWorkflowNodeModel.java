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
 *   11 Jun 2020 ("Marc Bux, KNIME GmbH, Berlin, Germany"): created
 */
package org.knime.buildworkflows.deploy;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformation;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformationPortObject;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformationPortObjectSpec;
import org.knime.buildworkflows.deploy.DeployWorkflowNodeDialog.ExistsOption;
import org.knime.buildworkflows.writer.SettingsModelIONodes;
import org.knime.buildworkflows.writer.WorkflowWriterNodeModel;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.util.FileUtil;

import com.knime.enterprise.server.rest.api.UploadApplication;
import com.knime.enterprise.server.rest.api.v4.repository.ent.RepositoryItem;
import com.knime.enterprise.server.rest.api.v4.repository.ent.Snapshot;
import com.knime.enterprise.server.rest.api.v4.repository.snapshots.Snapshots;
import com.knime.enterprise.server.rest.client.AbstractClient;
import com.knime.enterprise.server.rest.client.filehandling.RepositoryClient;
import com.knime.enterprise.utility.PermissionException;
import com.knime.enterprise.utility.WrongTypeException;

/**
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
final class DeployWorkflowNodeModel extends NodeModel {

    static final String determineWorkflowName(final WorkflowPortObjectSpec spec) {
        return FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(spec.getWorkflowName()).replaceAll("_").trim();
    }

    static <E extends Exception> ConnectionInformation validateAndGetConnectionInformation(final PortObjectSpec spec,
        final Function<String, E> errorConsumer) throws E {

        final ConnectionInformationPortObjectSpec con = (ConnectionInformationPortObjectSpec)spec;
        if (con == null) {
            throw errorConsumer.apply("No connection available.");
        }
        final ConnectionInformation conInf = con.getConnectionInformation();
        if (conInf == null) {
            throw errorConsumer.apply("No connection information available.");
        }
        return conInf;
    }

    static <E extends Exception> WorkflowPortObjectSpec validateAndGetWorkflowPortObjectSpec(final PortObjectSpec spec,
        final Function<String, E> errorFunction) throws E {

        final WorkflowPortObjectSpec portObjectSpec = (WorkflowPortObjectSpec)spec;
        if (portObjectSpec == null) {
            throw errorFunction.apply("No workflow available.");
        }
        return portObjectSpec;
    }

    static Optional<String> validateWorkflowGrp(final String workflowGrp) {
        if (workflowGrp == null || workflowGrp.trim().isEmpty()) {
            return Optional.of("Path to folder must not be empty.");
        }
        if (!workflowGrp.trim().startsWith(DeployWorkflowNodeDialog.PATH_SEPARATOR)) {
            return Optional
                .of(String.format("Path to folder must be absolute and start with a path separator (\"%s\").",
                    DeployWorkflowNodeDialog.PATH_SEPARATOR));
        }
        return Optional.empty();
    }

    static Optional<String> validateWorkflowName(final WorkflowPortObjectSpec portObjectSpec,
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

    private static final long TIMEOUT_IN_MS = 60_000L;

    private final SettingsModelString m_workflowGrp =
        new SettingsModelString(DeployWorkflowNodeDialog.WORKFLOW_GRP_CFG, DeployWorkflowNodeDialog.PATH_SEPARATOR);

    private final SettingsModelBoolean m_createParent = DeployWorkflowNodeDialog.createCreateParentModel();

    private final SettingsModelBoolean m_useCustomName = DeployWorkflowNodeDialog.createUseCustomNameModel();

    private final SettingsModelString m_customName = DeployWorkflowNodeDialog.createCustomNameModel();

    private final SettingsModelString m_existsOption = DeployWorkflowNodeDialog.createExistsOptionModel();

    private final SettingsModelBoolean m_createSnapshot = DeployWorkflowNodeDialog.createCreateSnapshotModel();

    private final SettingsModelString m_snapshotMessage = DeployWorkflowNodeDialog.createSnapshotMessageModel();

    private final SettingsModelIONodes m_ioNodes = DeployWorkflowNodeDialog.createIONodesModel();

    DeployWorkflowNodeModel() {
        super(new PortType[]{ConnectionInformationPortObject.TYPE, WorkflowPortObject.TYPE}, new PortType[0]);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {

        validateAndGetConnectionInformation(inSpecs[0], InvalidSettingsException::new);
        final WorkflowPortObjectSpec portObjectSpec =
            validateAndGetWorkflowPortObjectSpec(inSpecs[1], InvalidSettingsException::new);

        final Optional<String> err = validateWorkflowGrp(m_workflowGrp.getStringValue()).map(Optional::of)
            .orElseGet(() -> validateWorkflowName(portObjectSpec, m_useCustomName.getBooleanValue(),
                m_customName.getStringValue()));
        if (err.isPresent()) {
            throw new InvalidSettingsException(err.get());
        }

        return new PortObjectSpec[0];
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final ConnectionInformationPortObject con = (ConnectionInformationPortObject)inObjects[0];
        final ConnectionInformation conInf = con.getConnectionInformation();
        final String user = conInf.getUser();
        final String password = conInf.getPassword();
        final WorkflowPortObject workflowPortObject = (WorkflowPortObject)inObjects[1];
        final WorkflowPortObjectSpec workflowPortObjectSpec = workflowPortObject.getSpec();
        final WorkflowFragment fragment = workflowPortObjectSpec.getWorkflowFragment();

        final URI endpoint =
            new URI(conInf.getProtocol(), conInf.getHost(), DeployWorkflowNodeDialog.REST_ENDPOINT, null);
        final String path = m_workflowGrp.getStringValue().trim();
        final String workflowGrp =
            path.endsWith(DeployWorkflowNodeDialog.PATH_SEPARATOR) ? path.substring(0, path.length() - 1) : path;
        final String workflowName = m_useCustomName.getBooleanValue() ? m_customName.getStringValue().trim()
            : determineWorkflowName(workflowPortObjectSpec);
        final String workflowPath = workflowGrp + DeployWorkflowNodeDialog.PATH_SEPARATOR + workflowName;
        final ExistsOption existsOption = ExistsOption.valueOf(m_existsOption.getStringValue());

        final RepositoryClient rc = new RepositoryClient(endpoint, user, password, TIMEOUT_IN_MS);

        if (!m_createParent.getBooleanValue()) {
            exec.setProgress(.1, () -> "Checking if workflow group exists.");
            checkWorkflowGrpExists(path, workflowGrp, rc);
        }

        exec.setProgress(.3, () -> "Checking if workflow exists.");
        try {
            final RepositoryItem item = rc.getRepositoryItem(workflowPath);
            final RepositoryItem.Type type = item.getType();
            if (!type.equals(RepositoryItem.Type.Workflow)) {
                throw new IOException(String.format("An item of type \"%s\" already exists at path %s.",
                    type.getDescription(), workflowPath));
            } else if (existsOption == ExistsOption.FAIL) {
                throw new IOException(
                    String.format("Workflow %s exists and node is configured not to overwrite it.", workflowPath));
            }
        } catch (NoSuchElementException e) {
            // we are actually expecting to catch this exception here
        } catch (WebApplicationException e) {
            throw new IllegalStateException(String.format("%s while checking for existence of workflow: %s: ",
                e.getClass().getSimpleName(), e.getResponse().getStatus()), e);
        }

        exec.setProgress(.5, () -> "Saving workflow to disk.");
        final File tmpDir = FileUtil.createTempDir("deploy-workflow");
        final File localSource = WorkflowWriterNodeModel.write(tmpDir, workflowName, fragment, exec, m_ioNodes,
            workflowPortObject, false, this::setWarningMessage);

        exec.setProgress(.7, () -> "Deploying workflow onto KNIME Server.");
        deployWorkflow(user, password, endpoint, workflowPath, localSource);

        FileUtil.deleteRecursively(tmpDir);

        if (m_createSnapshot.getBooleanValue()) {
            exec.setProgress(.9, () -> "Creating Snapshot.");
            createSnapshot(user, password, endpoint, workflowPath);
        }

        return new PortObject[0];
    }

    private static void checkWorkflowGrpExists(final String path, final String workflowGrp, final RepositoryClient rc)
        throws PermissionException, IOException {
        try {
            rc.getRepositoryItem(path);
        } catch (NoSuchElementException e) {
            throw new IOException(String
                .format("Workflow group %s does not exist and node is configured not to create it.", workflowGrp));
        } catch (WebApplicationException e) {
            throw new IllegalStateException(String.format("%s while checking for existence of folder: %s: ",
                e.getClass().getSimpleName(), e.getResponse().getStatus()), e);
        }
    }

    private static void deployWorkflow(final String user, final String password, final URI endpoint,
        final String workflowPath, final File localSource) throws Exception {
        try {
            UploadApplication.uploadWorkflow(endpoint.toString(), user, password, workflowPath, localSource);
        } catch (WebApplicationException e) {
            throw new IllegalStateException(String.format("%s while uploading workflow: %s: ",
                e.getClass().getSimpleName(), e.getResponse().getStatus()), e);
        }
    }

    private void createSnapshot(final String user, final String password, final URI endpoint, final String workflowPath)
        throws InstantiationException, IllegalAccessException, IOException, PermissionException, WrongTypeException {
        final Snapshots snapshots = AbstractClient.createProxy(Snapshots.class, endpoint, user, password, "Workflow01",
            Duration.ofMillis(TIMEOUT_IN_MS));
        try {
            final Response response = snapshots.createSnapshot(workflowPath, m_snapshotMessage.getStringValue());
            try {
                if (response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                    final Snapshot snapshot = response.readEntity(Snapshot.class);
                    if (snapshot.getError() != null) {
                        throw new IOException(
                            String.format("Snapshot could not be created: %s", snapshot.getError().getMessage()));
                    }
                } else {
                    throw new IOException(
                        String.format("Snapshot could not be created: %s", response.readEntity(String.class)));
                }
            } finally {
                response.close();
            }
        } catch (WebApplicationException e) {
            throw new IllegalStateException(String.format("%s while creating snapshot: %s: ",
                e.getClass().getSimpleName(), e.getResponse().getStatus()), e);
        }
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_workflowGrp.saveSettingsTo(settings);
        m_createParent.saveSettingsTo(settings);
        m_useCustomName.saveSettingsTo(settings);
        m_customName.saveSettingsTo(settings);
        m_existsOption.saveSettingsTo(settings);
        m_createSnapshot.saveSettingsTo(settings);
        m_snapshotMessage.saveSettingsTo(settings);
        m_ioNodes.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowGrp.validateSettings(settings);
        m_createParent.validateSettings(settings);
        m_useCustomName.validateSettings(settings);
        m_customName.validateSettings(settings);
        m_existsOption.validateSettings(settings);
        m_createSnapshot.validateSettings(settings);
        m_snapshotMessage.validateSettings(settings);
        m_ioNodes.validateSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowGrp.loadSettingsFrom(settings);
        m_createParent.loadSettingsFrom(settings);
        m_useCustomName.loadSettingsFrom(settings);
        m_customName.loadSettingsFrom(settings);
        m_existsOption.loadSettingsFrom(settings);
        m_createSnapshot.loadSettingsFrom(settings);
        m_snapshotMessage.loadSettingsFrom(settings);
        m_ioNodes.loadSettingsFrom(settings);
    }

    @Override
    protected void reset() {
        // No internal state to load.
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // No internal state to load.
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // No internal state to load.
    }
}
