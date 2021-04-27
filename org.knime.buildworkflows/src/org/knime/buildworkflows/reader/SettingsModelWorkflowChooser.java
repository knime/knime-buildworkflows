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
 *   Apr 12, 2021 (hornm): created
 */
package org.knime.buildworkflows.reader;

import java.util.HashSet;
import java.util.Set;

import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.defaultnodesettings.EnumConfig;
import org.knime.filehandling.core.defaultnodesettings.filechooser.AbstractSettingsModelFileChooser;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.ReadPathAccessor;
import org.knime.filehandling.core.defaultnodesettings.filtermode.SettingsModelFilterMode.FilterMode;

/**
 * Stores a single workflow selection.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
class SettingsModelWorkflowChooser extends AbstractSettingsModelFileChooser<SettingsModelWorkflowChooser> {

    protected SettingsModelWorkflowChooser(final String configName, final String fileSystemPortIdentifier,
        final PortsConfiguration portConfig) {
        super(configName, portConfig, fileSystemPortIdentifier, EnumConfig.create(FilterMode.WORKFLOW),
            getConvenienceFSs());
    }

    private SettingsModelWorkflowChooser(final SettingsModelWorkflowChooser toCopy) {
        super(toCopy);
    }

    private static Set<FSCategory> getConvenienceFSs() {
        Set<FSCategory> res = new HashSet<>();
        res.add(FSCategory.LOCAL);
        res.add(FSCategory.MOUNTPOINT);
        res.add(FSCategory.RELATIVE);
        res.add(FSCategory.CUSTOM_URL);
        res.add(FSCategory.CONNECTED);
        return res;
    }

    ReadPathAccessor createReadPathAccessor() {
        return super.createPathAccessor();
    }

    boolean isDataAreaRelativeLocationSelected() {
        return getLocation().getFileSystemSpecifier().map("knime.workflow.data"::equals).orElse(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SettingsModelWorkflowChooser createClone() {
        return new SettingsModelWorkflowChooser(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getModelTypeID() {
        return "SMID_WorkflowChooser";
    }

}