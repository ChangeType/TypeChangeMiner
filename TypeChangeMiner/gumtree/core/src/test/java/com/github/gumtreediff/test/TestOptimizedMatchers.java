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
 * Copyright 2015-2016 Georg Dotzler <georg.dotzler@fau.de>
 * Copyright 2015-2016 Marius Kamp <marius.kamp@fau.de>
 */

package com.github.gumtreediff.test;

import com.github.gumtreediff.matchers.CompositeMatchers;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.utils.Pair;
import com.github.gumtreediff.tree.TreeContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOptimizedMatchers {

    @Test
    public void testRtedabcdefMatcher() {
        Pair<TreeContext, TreeContext> trees = TreeLoader.getZsSlidePair();
        ITree src = trees.first.getRoot();
        ITree dst = trees.second.getRoot();
        MappingStore mappings = new CompositeMatchers.Rtedacdef().match(src, dst);
        assertEquals(5, mappings.size());
        assertTrue(mappings.has(src, dst));
        assertTrue(mappings.has(src.getChild(0).getChild(0), dst.getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(0).getChild(0), dst.getChild(0).getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(1), dst.getChild(1).getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(2), dst.getChild(1)));
    }
    
    @Test
    public void testCdabcdefParMatcher() {
        Pair<TreeContext, TreeContext> trees = TreeLoader.getZsSlidePair();
        ITree src = trees.first.getRoot();
        ITree dst = trees.second.getRoot();
        MappingStore mappings = new CompositeMatchers.CdabcdefPar().match(src, dst);
        assertEquals(5, mappings.size());
        assertTrue(mappings.has(src.getChild(0).getChild(0), dst.getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(0).getChild(0), dst.getChild(0).getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(1), dst.getChild(1).getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(2), dst.getChild(1)));
        assertTrue(mappings.has(src.getChild(0), dst));
    }
    
    @Test
    public void testGtbcdefMatcher() {
        Pair<TreeContext, TreeContext> trees = TreeLoader.getZsSlidePair();
        ITree src = trees.first.getRoot();
        ITree dst = trees.second.getRoot();
        MappingStore mappings = new CompositeMatchers.Rtedacdef().match(src, dst);
        assertEquals(5, mappings.size());
        assertTrue(mappings.has(src, dst));
        assertTrue(mappings.has(src.getChild(0).getChild(0), dst.getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(0).getChild(0), dst.getChild(0).getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(1), dst.getChild(1).getChild(0)));
        assertTrue(mappings.has(src.getChild(0).getChild(2), dst.getChild(1)));
    }

}
