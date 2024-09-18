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
 *   24 Jun 2020 (Marc Bux, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.buildworkflows.converter;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;

/**
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
final class WorkflowConverterNodeConfig {

    /** The name of the optional connection input port group. */
    static final String CONNECTION_INPUT_PORT_GRP_NAME = "File System Connection";

    private final SettingsModelBoolean m_indent = new SettingsModelBoolean("indent", true);

    private final SettingsModelBoolean m_removeEmptyValues = new SettingsModelBoolean("remove-empty", false);

    private final SettingsModelBoolean m_removeAnnotations = new SettingsModelBoolean("remove-annotations", false);

    private final SettingsModelBoolean m_removeMetadata = new SettingsModelBoolean("remove-metadata", false);

    private final SettingsModelBoolean m_removeUiInfo = new SettingsModelBoolean("remove-ui-info", false);

    private final SettingsModelBoolean m_removeBundle = new SettingsModelBoolean("remove-bundle", false);

    private final SettingsModelBoolean m_removeFeature = new SettingsModelBoolean("remove-feature", false);

    private final SettingsModelWorkflowChooser m_workflowChooser;

    WorkflowConverterNodeConfig(final NodeCreationConfiguration creationConfig) {
        m_workflowChooser = new SettingsModelWorkflowChooser("workflow-chooser", CONNECTION_INPUT_PORT_GRP_NAME,
            creationConfig.getPortConfig().orElseThrow(IllegalStateException::new));
    }

    SettingsModelWorkflowChooser getWorkflowChooserModel() {
        return m_workflowChooser;
    }

    void validateConfigurationForModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowChooser.validateSettings(settings);
        m_removeEmptyValues.loadSettingsFrom(settings);
        m_indent.loadSettingsFrom(settings);
        m_removeAnnotations.loadSettingsFrom(settings);
        m_removeMetadata.loadSettingsFrom(settings);
        m_removeUiInfo.loadSettingsFrom(settings);
        m_removeBundle.loadSettingsFrom(settings);
        m_removeFeature.loadSettingsFrom(settings);
    }

    void saveConfigurationForModel(final NodeSettingsWO settings) {
        m_workflowChooser.saveSettingsTo(settings);
        m_removeEmptyValues.saveSettingsTo(settings);
        m_indent.saveSettingsTo(settings);
        m_removeAnnotations.saveSettingsTo(settings);
        m_removeMetadata.saveSettingsTo(settings);
        m_removeUiInfo.saveSettingsTo(settings);
        m_removeBundle.saveSettingsTo(settings);
        m_removeFeature.saveSettingsTo(settings);
    }

    void loadConfigurationForModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowChooser.loadSettingsFrom(settings);
    }

    SettingsModelBoolean getIndent() {
        return m_indent;
    }

    SettingsModelBoolean getRemoveEmptyValues() {
        return m_removeEmptyValues;
    }

    SettingsModelBoolean getRemoveAnnotations() {
        return m_removeAnnotations;
    }

    SettingsModelBoolean getRemoveMetadata() {
        return m_removeMetadata;
    }

    SettingsModelBoolean getRemoveUIInfo() {
        return m_removeUiInfo;
    }

    SettingsModelBoolean getRemoveBundle() {
        return m_removeBundle;
    }

    SettingsModelBoolean getRemoveFeature() {
        return m_removeFeature;
    }
}
