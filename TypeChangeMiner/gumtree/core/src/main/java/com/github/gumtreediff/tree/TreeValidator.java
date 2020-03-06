/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2019 Jean-Rémy Falleri <jr.falleri@gmail.com>
 */

package com.github.gumtreediff.tree;

public class TreeValidator {

    private TreeContext context;

    public void validate(TreeContext context) {
        this.context = context;
        validate(context.getRoot());
    }

    private void validate(ITree root) {
        for (ITree t : root.preOrder()) {
            if (!t.isLeaf()) {
                if (!t.getLabel().equals(ITree.NO_LABEL))
                    throw new TreeException(String.format("%s : %s\n%s",
                            "Inner node with label",
                            t.toString(),
                            t.toTreeString()));

                if (t.getChildren().get(0).getPos() < t.getPos())
                    throw new TreeException(String.format("%s : %s\n%s",
                            "Children begin position before node begin position",
                            t.getChildren().get(0).toString(),
                            t.toTreeString()));

                if (t.getChildren().get(t.getChildren().size() - 1).getEndPos() > t.getEndPos())
                    throw new TreeException(String.format("%s : %s\n%s",
                            "Children end position after node end position",
                            t.getChildren().get(t.getChildren().size() - 1).toString(),
                            t.toTreeString()));

                if (t.getChildren().size() > 1) {
                    for (int i = 1; i < t.getChildren().size(); i++) {
                        ITree b = t.getChild(i -  1);
                        ITree c = t.getChild(i);
                        if (c.getPos() < b.getEndPos())
                            throw new TreeException(String.format("%s : %s\n%s",
                                    "Sibling begin position before node end position",
                                    c.toString(),
                                    t.toTreeString()));
                    }
                }
            }

        }

    }

    public static class TreeException extends RuntimeException {
        public TreeException(String message) {
            super(message);
        }
    }

}
