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

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponent;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.FileUtil;

/**
 * Component that provides a text field that is validated on each keystroke. If validation fails, the input field is
 * highlighted and a message is displayed. Optionally, a label can be attached to the input field.
 *
 * @author Benjamin Moser
 */
@SuppressWarnings("java:S1948")
public final class ValidatedWorkflowNameField extends DialogComponent {

    private final static Border DEFAULT_BORDER = new JTextField().getBorder();

    private final static Insets DEFAULT_INSETS = DEFAULT_BORDER.getBorderInsets(new JTextField());

    private final static Border ERROR_BORDER =
        BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.RED), BorderFactory
            .createEmptyBorder(DEFAULT_INSETS.top, DEFAULT_INSETS.left, DEFAULT_INSETS.bottom, DEFAULT_INSETS.right));

    private final Optional<JLabel> m_label;

    private final boolean m_allowEmpty;

    private final JTextField m_input;

    private final JLabel m_warningLabel;

    private final GridBagConstraints m_gbc;

    private final DocumentListener m_inputListener = new DocumentListener() { // NOSONAR
        @Override
        public void insertUpdate(final DocumentEvent e) {
            update();
        }

        @Override
        public void removeUpdate(final DocumentEvent e) {
            update();
        }

        @Override
        public void changedUpdate(final DocumentEvent e) {
            update();
        }

        private void update() {
            try {
                updateModel();
            } catch (InvalidSettingsException invalidSettingsException) { // NOSONAR
            }

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
        super(model);
        JPanel container = getComponentPanel();
        m_allowEmpty = allowEmpty;
        if (labelText != null) {
            m_label = Optional.of(new JLabel(labelText));
        } else {
            m_label = Optional.empty();
        }

        container.setLayout(new GridBagLayout());

        m_gbc = initGridBagConstraints();

        m_input = new JTextField(model.getStringValue());
        m_input.setColumns(15);
        m_input.getDocument().addDocumentListener(m_inputListener);

        // Add label component if given.
        m_label.ifPresent(label -> {
            container.add(label, m_gbc);
            m_gbc.gridx++;
        });

        // Prepare warning message component.
        container.add(m_input, m_gbc);
        m_warningLabel = new JLabel();
        m_warningLabel.setPreferredSize(new Dimension(350, 15));
        m_warningLabel.setForeground(Color.RED);
        m_gbc.gridy++;
        container.add(m_warningLabel, m_gbc);

        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, (int)container.getPreferredSize().getHeight()));
        // The ordering here is important.
        m_warningLabel.setVisible(false);

        model.addChangeListener(e -> setEnabledComponents(model.isEnabled()));

        updateComponent();
    }

    private void liveValidateInput() throws InvalidSettingsException {
        Optional<String> errorMsg = validateCustomWorkflowName(m_input.getText(), m_allowEmpty);
        if (errorMsg.isPresent()) {
            setError(errorMsg.get());
            throw new InvalidSettingsException(errorMsg.get());  // NOSONAR
        } else {
            clearError();
        }
    }

    private void updateModel() throws InvalidSettingsException {
        liveValidateInput();
        ((SettingsModelString)getModel()).setStringValue(m_input.getText());
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
        if (m_input.getText().trim().isEmpty()) {
            m_input.setBorder(ERROR_BORDER);
        }
    }

    private void clearError() {
        m_warningLabel.setVisible(false);
        m_input.setBorder(DEFAULT_BORDER);
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

    @Override
    protected void validateSettingsBeforeSave() throws InvalidSettingsException {
        if (getModel().isEnabled()) {
            updateModel();
        }
    }

    @Override
    protected void checkConfigurabilityBeforeLoad(final PortObjectSpec[] specs) throws NotConfigurableException {
        // noop
    }

    @Override
    protected void setEnabledComponents(final boolean enabled) {
        if (enabled) {
            m_input.setEnabled(true);
            m_label.ifPresent(label -> label.setForeground(Color.BLACK));
            try {
                // trigger validation but do not update model
                liveValidateInput();
            } catch (InvalidSettingsException e) {  // NOSONAR
            }
        } else {
            m_input.setEnabled(false);
            m_label.ifPresent(label -> label.setForeground(Color.LIGHT_GRAY));
            clearError();  // do not show error in any case
        }
    }

    @Override
    public void setToolTipText(final String text) {
        m_label.ifPresent(c -> c.setToolTipText(text));
        m_warningLabel.setToolTipText(text);
        m_input.setToolTipText(text);
    }


    @Override
    protected void updateComponent() {
        clearError();
        final String modelValue = ((SettingsModelString)getModel()).getStringValue();
        if (!m_input.getText().equals(modelValue)) {
            m_input.setText(modelValue);
        }
        setEnabledComponents(getModel().isEnabled());
    }
}
