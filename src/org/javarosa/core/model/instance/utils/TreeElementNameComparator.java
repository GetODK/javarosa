package org.javarosa.core.model.instance.utils;

import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;

import java.util.Map;

import static java.lang.System.out;

/** Supports locating {@link TreeElement}s by name. */
public class TreeElementNameComparator {

    /**
     * Determines whether treeElement matches name. If name is a wildcard, it does. If the element’s
     * name matches name (using equals), it does. If neither of those cases are true, a namespace
     * prefix for treeElement, if one can be located, is prepended to treeElement’s name, and that is
     * compared to name with equals.
     *
     * @param treeElement the TreeElement under examination
     * @param name        the name to be compared with treeElement’s name
     * @return whether treeElement matches name
     */
    public static boolean elementMatchesName(TreeElement treeElement, String name, Map<String, String> namespacesMap) {
        final String namespace = treeElement.getNamespace();
        final String prefix =
            (namespacesMap != null && namespacesMap.containsKey(namespace)) ?
            " " + namespacesMap.get(namespace) : "";
        final String nsMsg = namespace == null ? "" : " in " + namespace + prefix;
        // Todo remove the logging after development is finished
        out.println("comparing " + treeElement.getName() + nsMsg + " with " + name);

        if (name.equals(TreeReference.NAME_WILDCARD)) {
            out.println("Match");
            return true;
        }

        final String elementName = treeElement.getName();

        if (elementName.equals(name)) {
            out.println("Match");
            return true;
        }

        if (namespace != null && namespacesMap != null) {
            String namespacePrefix = namespacesMap.get(namespace);
            if (namespacePrefix != null && (namespacePrefix + ":" + elementName).equals(name)) {
                out.println("Match");
                return true;
            }
        }

        out.println("No match");
        return false;
    }
}
