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
 *   Dec 9, 2019 (Mark Ortmann, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.buildworkflows.combiner;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.xmlbeans.XmlException;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;
import org.xml.sax.SAXException;

/**
 * Workflow combiner node.
 *
 * @author Mark Ortmann, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction")
public class WorkflowCombinerNodeFactory extends ConfigurableNodeFactory<NodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final PortsConfigurationBuilder b = new PortsConfigurationBuilder();
        b.addExtendableInputPortGroup("workflow model",
            new PortType[]{WorkflowPortObject.TYPE, WorkflowPortObject.TYPE}, WorkflowPortObject.TYPE);
        b.addFixedOutputPortGroup("output", WorkflowPortObject.TYPE);
        return Optional.of(b);
    }

    @Override
    protected WorkflowCombinerNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new WorkflowCombinerNodeModel(creationConfig.getPortConfig().get());
    }

    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, WorkflowCombinerNodeParameters.class);
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<NodeModel> createNodeView(final int viewIndex, final NodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    public boolean hasNodeDialog() {
        return true;
    }

    // Descriptions and port names from factory.xml
    private static final String NODE_NAME = "Workflow Combiner";

    private static final String SHORT_DESCRIPTION = "Concatenates two workflow segments.";

    private static final String NODE_ICON = "workflow_combiner.png";

    private static final String FULL_DESCRIPTION =
        "Allows to connect various workflows into one workflow. "
            + "Free output ports from one workflow are connected to the free input ports of the consecutive workflow. "
            + "The actual pairing of those output and input ports can be configured."
            + "\n\nPlease note that the workflow name as well as the workflow editor settings "
            + "(such as grid or connection settings) of the result workflow will be inherited from the workflow at the first input port."
            + "\n\n<b>Default configuration:</b> "
            + "By default (i.e. if not configured otherwise) the inputs of each workflow will be automatically connected with the outputs of the predecessor workflow, i.e., the workflow connected to the previous input port. "
            + "The <tt>i-th</tt> output of workflow <tt>j</tt> will be connected to the <tt>i-th</tt> input of workflow <tt>j+1</tt>. "
            + "If the default pairing of inputs and outputs cannot be applied (because of a non-matching number of inputs and outputs or incompatible port types), the node can not be executed and requires manual configuration."
            + "\n\n<b>Manual configuration:</b> If the default configuration is not applicable or not desired, the output to input pairing can be chosen manually. This is done by selecting the outputs that are to be connected to an inputs of the subsequent workflow. Only compatible port types are eligible.";

    private static final List<String> KEYWORDS =
        List.of("workflow", "combine", "segment", "merge", "connect", "manual configuration", "default configuration");

    @Override
    public NodeDescription createNodeDescription() throws SAXException, IOException, XmlException {
        // Port names and descriptions from factory.xml
        Collection<PortDescription> inputPortDescs =
            List.of(new PortDescription("inId", "First workflow", "First workflow to be connected.", false),
                new PortDescription("Collector", "Second workflow", "Second workflow to be connected.", false),
                new PortDescription("dynport", "Additional workflow", "Workflow to be connected to its predecessor.",
                    true));
        Collection<PortDescription> outputPortDescs = List.of(new PortDescription("outId", "Connected workflow",
            "Workflow derived by connecting all input workflows in order of their appearance.", false));
        return DefaultNodeDescriptionUtil.createNodeDescription(NODE_NAME,
            NODE_ICON, // icon resource fallback
            inputPortDescs,
            outputPortDescs,
            SHORT_DESCRIPTION,
            FULL_DESCRIPTION,
            List.of(),
            WorkflowCombinerNodeParameters.class,
            List.of(),
            NodeType.Manipulator,
            KEYWORDS,
            null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, WorkflowCombinerNodeParameters.class));
    }

}
