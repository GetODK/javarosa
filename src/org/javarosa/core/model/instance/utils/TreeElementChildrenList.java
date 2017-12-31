/*
 * Copyright 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.javarosa.core.model.instance.utils;

import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Stores and manages access to {@link TreeElement} children. They are stored in an {@link ArrayList}, and a
 * {@link NameToNameIndex} is used to speed access to them. Supports finding children using wildcards, and by
 * name with and without namespace prefixes (such as “orx:meta”).
 */
public class TreeElementChildrenList implements Iterable<TreeElement> {
    private final List<TreeElement> children = new ArrayList<>();
    /** A map of child names to {@link NameIndex}es */
    final NameToNameIndex nameToNameIndex = new NameToNameIndex();

    /** Returns the number of children */
    public int size() {
        return children.size();
    }

    /** Returns an iterator over the children */
    @Override public Iterator<TreeElement> iterator() {
        return children.iterator();
    }

    /** Adds a child at the specified index */
    public void add(int index, TreeElement child) {
        final String name = child.getName();
        final int mult = child.getMultiplicity();
        children.add(index, child);
        updateNameIndex(name, mult, index);
    }

    private void updateNameIndex(String name, int mult, int index) {
        NameIndex nameIndex = nameToNameIndex.get(name);
        if (nameIndex == null) {
            nameIndex = new NameIndex();
        }
        nameIndex.add(mult, index);
        nameToNameIndex.put(name, nameIndex);
    }

    /** Adds all of the provided children */
    public void addAll(Iterable<TreeElement> childIterable) {
        for (TreeElement child : childIterable) {
            final String name = child.getName();
            final int mult = child.getMultiplicity();
            int index = children.size();
            children.add(child);
            updateNameIndex(name, mult, index);
        }
    }

    /** Adds a child using the next available multiplicity */
    public void addInOrder(TreeElement child) {
        final String name = child.getName();
        final int childMultiplicity = child.getMultiplicity();
        final int searchMultiplicity;
        final int newIndexAdjustment;
        if (childMultiplicity == TreeReference.INDEX_TEMPLATE) {
            searchMultiplicity = 0;
            newIndexAdjustment = 0;
        } else {
            searchMultiplicity = childMultiplicity == 0 ? TreeReference.INDEX_TEMPLATE : childMultiplicity - 1;
            newIndexAdjustment = 1;
        }
        final ElementAndLoc el = getChildAndLoc(name, searchMultiplicity);
        final int newIndex = el == null ? children.size() : el.index + newIndexAdjustment;
        children.add(newIndex, child);
        updateNameIndex(name, childMultiplicity, newIndex);
    }

    /** Gets the child at the specified index */
    public TreeElement get(int index) {
        return children.get(index);
    }

    /** Gets all non-template children with the specified name */
    public List<TreeElement> get(String name) {
        final NameIndex nameIndex = nameToNameIndex.get(name);
        if (nameIndex != null) {
            if (nameIndex.isValid()) {
                final int seqLen = nameIndex.size(false);
                int fromIndex = nameIndex.startingIndex(false);
                int toIndex = fromIndex + seqLen; // subList toIndex is exclusive
                return children.subList(fromIndex, toIndex);
            }
        }
        // todo deal with TreeElementNameComparator
        List<TreeElement> children = new ArrayList<>();
        findChildrenWithNameWithoutIndex(name, children);
        return children;
    }

    /** Gets the child with the specified name and multiplicity */
    public TreeElement get(String name, int multiplicity) {
        TreeElementChildrenList.ElementAndLoc el = getChildAndLoc(name, multiplicity);
        if (el == null) {
            return null;
        }
        return el.treeElement;
    }

    /** Gets a count of all non-template children with the specified name */
    public int getCount(String name) {
        NameIndex nameIndex = nameToNameIndex.get(name);
        if (validNameIndex(nameIndex)) {
            return nameIndex.numNonTemplateChildren();
        }
        return findChildrenWithNameWithoutIndex(name, null);
    }

    private boolean validNameIndex(NameIndex nameIndex) {
        return nameIndex != null && nameIndex.isValid();
    }

    /**
     * Returns the count of children with the given name, and if {@code results} is not null, stores the children there.
     *
     * @param name    the name to look for
     * @param results a List into which to store the children, or null
     * @return the number of children with the given name
     */
    private int findChildrenWithNameWithoutIndex(String name, List<TreeElement> results) {
        int count = 0;
        for (TreeElement child : children) {
            if ((child.getMultiplicity() != TreeReference.INDEX_TEMPLATE) &&
                    TreeElementNameComparator.elementMatchesName(child, name)) {
                ++count;
                if (results != null) {
                    results.add(child);
                }
            }
        }
        return count;
    }

    /** Removes a child at the specified index */
    public void remove(int index) {
        TreeElement remove = children.remove(index);
        NameIndex ni = nameToNameIndex.get(remove.getName());
        if (ni != null) {
            ni.setInvalid(NameIndexInvalidationReason.ELEMENT_REMOVED);
        }
    }

    /** Removes a specific child */
    public void remove(TreeElement treeElement) {
        int i = children.indexOf(treeElement);
        if (i >= 0) {
            remove(i);
        }
    }

    /**
     * Removes a specific child, decrements the mult values of following elements, and updates
     * the NameIndex.
     *
     * @param treeElement the child element to be removed
     */
    public void removeChildAndAdjustSiblingMults(TreeElement treeElement) {
        children.remove(treeElement);
        // update multiplicities of other child nodes
        final int mult = treeElement.getMult();
        for (TreeElement child : children) {
            if (child.getName().equals(treeElement.getName()) && child.getMult() > mult) {
                child.setMult(child.getMult() - 1);
                // When a group changes its multiplicity, then all caches of its children must clear.
                child.clearChildrenCaches();
            }
        }
        final NameIndex multToIndex = nameToNameIndex.get(treeElement.getName());
        if (multToIndex != null) {
            multToIndex.shrinkByOne();
        }
    }

    /** Removes the first child with the given name and multiplicity, if one exists */
    public void remove(String name, int multiplicity) {
        TreeElement child = get(name, multiplicity);
        if (child != null) {
            remove(child);
        }
    }

    public void removeAll(String name) {
        final NameIndex nameIndex = nameToNameIndex.get(name);
        if (validNameIndex(nameIndex)) {
            final int startingIndex = nameIndex.startingIndex(true);
            int size = nameIndex.size(true);
            children.subList(startingIndex, startingIndex + size).clear();
            nameToNameIndex.remove(name);
            nameToNameIndex.shiftIndexes(startingIndex, -size);
        } else {
            for (TreeElement child : get(name)) {
                remove(child);
            }
        }
    }

    /** Removes all children */
    public void clear() {
        children.clear();
        nameToNameIndex.clear();
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    public boolean indexIsValid(String name) { // For teting
        NameIndex nameIndex = nameToNameIndex.get(name);
        return validNameIndex(nameIndex);
    }

    private class ElementAndLoc {
        final TreeElement treeElement;
        final int index;

        ElementAndLoc(TreeElement treeElement, int index) {
            this.treeElement = treeElement;
            this.index = index;
        }
    }

    private ElementAndLoc getChildAndLoc(String name, int multiplicity) {
        if (name.equals(TreeReference.NAME_WILDCARD)) {
            if (multiplicity == TreeReference.INDEX_TEMPLATE || children.size() < multiplicity + 1) {
                return null;
            }
            return new ElementAndLoc(children.get(multiplicity), multiplicity); //droos: i'm suspicious of this
        }

        NameIndex nameIndex = nameToNameIndex.get(name);
        if (nameIndex == null || !nameIndex.isValid()) {
            for (int i = 0; i < children.size(); i++) {
                TreeElement child = children.get(i);
                if (TreeElementNameComparator.elementMatchesName(child, name) && child.getMult() == multiplicity) {
                    return new ElementAndLoc(child, i);
                }
            }
            return null;
        }

        Integer index = nameIndex.indexOf(multiplicity);
        if (index != null && index < children.size()) {
            return new ElementAndLoc(children.get(index), index);
        }
        return null;
    }
}
