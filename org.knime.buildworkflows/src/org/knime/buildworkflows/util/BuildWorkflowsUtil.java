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
 *   Mar 12, 2020 (hornm): created
 */
package org.knime.buildworkflows.util;

import org.knime.core.util.Pair;

/**
 * Utility methods for 'build-workflow' functionality.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class BuildWorkflowsUtil {

    // vertical distance between newly added input and output nodes
    private static final int NODE_DIST = 120;

    private static final int NODE_WIDTH = 124;

    private static final int NODE_HEIGHT = 83;

    private BuildWorkflowsUtil() {
        //utility class
    }

    /**
     * Calculates the position of a n nodes either placed at the beginning (inputs) or the end (outputs) of a workflow.
     *
     * @param wfBounds the bounding box of the workflow to place the inputs/outputs before/after
     * @param numNodes the number of nodes to place
     * @param isInput if the position is calculated for input or output nodes
     * @return a pair containing the x-position first, second a list of y-positions (= number of nodes)
     */
    public static Pair<Integer, int[]> getInputOutputNodePositions(final int[] wfBounds, final int numNodes,
        final boolean isInput) {
        int y_bb_center = (int)Math.round((wfBounds[3] - wfBounds[1]) / 2.0 + wfBounds[1]);
        int y_offset = (int)Math.floor(y_bb_center - ((numNodes - 1) * NODE_DIST) / 2.0 - NODE_HEIGHT / 2.0);
        int x_pos = (int)Math.round((isInput ? wfBounds[0] - NODE_DIST : wfBounds[2] + NODE_DIST) - NODE_WIDTH / 2.0);
        int[] y_pos = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            y_pos[i] = (int)Math.floor(y_offset + i * NODE_DIST);
        }
        return Pair.create(x_pos, y_pos);
    }

}
