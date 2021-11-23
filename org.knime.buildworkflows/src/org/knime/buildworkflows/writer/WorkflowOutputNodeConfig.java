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
 *   28 Oct 2021 (Dionysios Stolis): created
 */
package org.knime.buildworkflows.writer;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortType;
import org.knime.workflowservices.knime.callee.WorkflowBoundaryConfiguration;
import org.knime.workflowservices.knime.callee.WorkflowOutputNodeFactory;

/**
 * Represents the {@link WorkflowOutputNodeFactory} node
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
final class WorkflowOutputNodeConfig extends OutputNodeConfig {

    static final String NODE_NAME = "Workflow Service Output";

    /**
     * {@inheritDoc}
     */
    @Override
    String getNodeName() {
        return NODE_NAME;
    }

    /**
     * {@inheritDoc}
     * @throws InvalidSettingsException
     */
    @Override
    protected NodeFactory<? extends NodeModel> createNodeFactory(final PortType portType) throws InvalidSettingsException {
        return new WorkflowOutputNodeFactory(portType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveActualNodeSettingsTo(final NodeSettingsWO settings, final boolean useV2SmartInOutNames)
        throws InvalidSettingsException {
        String parameterName = StringUtils.isEmpty(getParameterName()) ? getDefaultParameterName() : getParameterName();
        WorkflowBoundaryConfiguration.saveConfigAsNodeSettings(settings, parameterName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDefaultParameterName() {
        return "workflow-output";
    }

}
