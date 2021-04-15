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
 */
package org.knime.buildworkflows.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;
import java.util.regex.Matcher;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.FileUtil;

/**
 * Component that provides a text field that is validated on each keystroke. If validation fails, the input field is
 * highlighted and a message is displayed. Optionally, a label can be attached to the input field.
 *
 * @author Benjamin Moser
 */
@SuppressWarnings("java:S1948")
public final class ValidatedWorkflowNameField extends JPanel {

    private final Optional<JLabel> m_label;

    private final boolean m_allowEmpty;

    private final JTextField m_input;

    private final JLabel m_warningLabel;

    private final GridBagConstraints m_gbc;

    private final SettingsModelString m_model;

    /**
     * Present iff the current input is invalid. Contains an appropriate error message if so. Updated with each change
     * to the input field.
     */
    private Optional<String> m_errorMsg;

    private final DocumentListener m_inputListener = new DocumentListener() {
        @Override
        public void insertUpdate(final DocumentEvent e) {
            onInputChange();
        }

        @Override
        public void removeUpdate(final DocumentEvent e) {
            onInputChange();
        }

        @Override
        public void changedUpdate(final DocumentEvent e) {
            onInputChange();
        }
    };

    /**
     * @param model The settings model to back the input field
     * @see #ValidatedWorkflowNameField(SettingsModelString, String, boolean)
     */
    public ValidatedWorkflowNameField(final SettingsModelString model) {
        this(model, null, true);
    }

    /**
     *
     * @param model The settings model to back the input field
     * @param labelText If not null, a label for the text input is shown.
     * @param allowEmpty If false, validation will fail if the input field is empty.
     */
    public ValidatedWorkflowNameField(final SettingsModelString model, final String labelText,
        final boolean allowEmpty) {
        m_allowEmpty = allowEmpty;
        m_model = model;
        if (labelText != null) {
            m_label = Optional.of(new JLabel(labelText));
        } else {
            m_label = Optional.empty();
        }

        this.setBorder(new EmptyBorder(0, 10, 0, 0)); // left padding

        this.setLayout(new GridBagLayout());
        m_gbc = initGridBagConstraints();

        m_input = new JTextField(m_model.getStringValue());
        m_input.setColumns(15);
        m_input.getDocument().addDocumentListener(m_inputListener);


        // Add label component if given.
        m_label.ifPresent(label -> {
            label.setBorder(new EmptyBorder(0, 0, 0, 5));
            this.add(label, m_gbc);
            m_gbc.gridx++;
        });

        // Prepare warning message component.
        this.add(m_input, m_gbc);
        m_warningLabel = new JLabel();
        m_warningLabel.setPreferredSize(new Dimension(350, 15));
        m_warningLabel.setForeground(Color.RED);
        m_gbc.gridy++;
        this.add(m_warningLabel, m_gbc);

        this.setMaximumSize(new Dimension(Integer.MAX_VALUE, (int)this.getPreferredSize().getHeight()));
        // The ordering here is important.
        m_warningLabel.setVisible(false);

        m_model.addChangeListener(e -> updateEnabledState());
    }

    /**
     * Toggle presentational state of inputs to reflect enabled/disabled state of models.
     */
    private void updateEnabledState() {
        if (m_model.isEnabled()) {
            m_input.setEnabled(true);
            m_label.ifPresent(label -> label.setForeground(Color.BLACK));
            onInputChange(); // trigger validation
        } else {
            m_input.setEnabled(false);
            m_label.ifPresent(label -> label.setForeground(Color.LIGHT_GRAY));
            clearError(); // do not show error in any case
        }
    }

    private void onInputChange() {
        m_errorMsg = validateCustomWorkflowName(m_input.getText(), m_allowEmpty);
        if (m_errorMsg.isPresent()) {
            setError(m_errorMsg.get());
        } else {
            clearError();
            // actually update model contents
            m_model.setStringValue(m_input.getText());
        }
    }

    private static Optional<String> validateCustomWorkflowName(final String name, final boolean allowEmpty) {
        if (!allowEmpty && name.trim().isEmpty()) {
            return Optional.of("Custom workflow name is empty.");
        }
        final Matcher matcher = FileUtil.ILLEGAL_FILENAME_CHARS_PATTERN.matcher(name);
        if (matcher.find()) {
            return Optional.of(String.format("Illegal character at index %d.", matcher.start()));
        }
        return Optional.empty();
    }

    private void setError(final String text) {
        m_warningLabel.setText(text);
        m_warningLabel.setVisible(true);
    }

    private void clearError() {
        m_warningLabel.setVisible(false);
    }

    private static GridBagConstraints initGridBagConstraints() {
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = gbc.weighty = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.insets = new Insets(5, 0, 5, 0);
        return gbc;
    }

    /**
     * Save state of model to the given settings.
     *
     * @param settings
     * @throws InvalidSettingsException
     */
    public void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        if (m_model.isEnabled() && m_errorMsg.isPresent()) {
            throw new InvalidSettingsException(m_errorMsg.get());
        }
        m_model.saveSettingsTo(settings);
    }

    /**
     * Load state of model from the given settings.
     *
     * @param settings
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) {
        try {
            m_model.loadSettingsFrom(settings);
            updateEnabledState();
        } catch (InvalidSettingsException e) { // NOSONAR
            m_model.setStringValue("");
        }
    }

    /**
     * @return The model representing the input state.
     */
    public SettingsModelString getModel() {
        return m_model;
    }
}
