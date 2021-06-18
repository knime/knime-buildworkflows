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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;
import java.util.regex.Matcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
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
import org.knime.filehandling.core.defaultnodesettings.status.DefaultStatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusView;

/**
 * Component that provides a text field that is validated on each keystroke. If validation fails, the input field is
 * highlighted and a message is displayed. Optionally, a label can be attached to the input field.
 *
 * @author Benjamin Moser
 */
@SuppressWarnings("java:S1948")
public final class ValidatedWorkflowNameField extends DialogComponent {

    private static final Border DEFAULT_BORDER = new JTextField().getBorder();

    private static final Insets DEFAULT_INSETS = DEFAULT_BORDER.getBorderInsets(new JTextField());

    private static final Border ERROR_BORDER =
        BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.RED), BorderFactory.createEmptyBorder(
            DEFAULT_INSETS.top - 1, DEFAULT_INSETS.left - 1, DEFAULT_INSETS.bottom - 1, DEFAULT_INSETS.right - 1));

    private final Optional<JLabel> m_label;

    private final boolean m_allowEmpty;

    private final JTextField m_input;

    private final StatusView m_status;

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
        m_input.setBorder(DEFAULT_BORDER);

        // Add label component if given.
        m_label.ifPresent(label -> {
            JPanel labelContainer = new JPanel();
            labelContainer.add(label);
            labelContainer.add(Box.createHorizontalStrut(0));
            container.add(labelContainer, m_gbc);
            m_gbc.gridx++;
        });

        container.add(m_input, m_gbc);

        // Prepare warning message component.
        final JPanel status = new JPanel(new GridBagLayout());
        m_status = new StatusView();
        final JLabel statusLabel = m_status.getLabel();
        status.add(statusLabel, m_gbc);
        m_gbc.gridy++;
        container.add(status, m_gbc);

        container.add(Box.createVerticalGlue());

        model.addChangeListener(e -> setEnabledComponents(model.isEnabled()));

        updateComponent();
    }

    private void liveValidateInput() throws InvalidSettingsException {
        Optional<String> errorMsg = validateCustomWorkflowName(m_input.getText(), m_allowEmpty);
        if (errorMsg.isPresent()) {
            setError(errorMsg.get());
            throw new InvalidSettingsException(errorMsg.get()); // NOSONAR
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
            return Optional
                .of("<html>Name must not contain either of " + listChars(FileUtil.ILLEGAL_FILENAME_CHARS) + "</html>");
        }
        return Optional.empty();
    }

    private void setError(final String text) {
        m_input.setBorder(ERROR_BORDER);
        m_status.setStatus(new DefaultStatusMessage(StatusMessage.MessageType.ERROR, text));
    }

    private void clearError() {
        m_status.clearStatus();
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
            } catch (InvalidSettingsException e) { // NOSONAR
            }
        } else {
            m_input.setEnabled(false);
            m_label.ifPresent(label -> label.setForeground(Color.LIGHT_GRAY));
            clearError(); // do not show error in any case
        }
    }

    @Override
    public void setToolTipText(final String text) {
        m_label.ifPresent(c -> c.setToolTipText(text));
        m_input.setToolTipText(text);
        m_status.getLabel().setToolTipText(text);
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

    /**
     * Given a String, list each character of that string in a human-readable, formatted string.
     *
     * @param chars A string containing the characters to be listed.
     * @return A HTML-formatted string listing the given characters.
     */
    private static String listChars(final String chars) {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < chars.length(); i++) {
            String curChar = chars.substring(i, i + 1);
            // Printing `<` or `>` in JLabels that already use HTML formatting is problematic.
            if (curChar.equals("<")) {
                curChar = "&lt;";
            }
            if (curChar.equals(">")) {
                curChar = "&gt;";
            }
            res.append("<code>" + curChar + "</code>");
            if (i < chars.length() - 2) {
                res.append(", ");
            }
            if (i == chars.length() - 2) {
                res.append(" or ");
            }
        }
        return res.toString();
    }

}
