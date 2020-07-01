/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.Configuration;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

public class JavaClassFilter {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final int BLOCK_NONE = 0;
    private static final int BLOCK_JDK = 1;
    private static final int BLOCK_LIB = 2;
    private static final int BLOCK_BIN = 3;

    /**
     * Substitute the variables in the exclusion filter list.
     *
     * <p>
     * For example, a sample input could be:
     * [
     *  "$JDK",
     *  "$Libraries",
     *  "junit.*",
     *  "java.lang.ClassLoader"
     * ].
     *
     * Variable "$JDK" means skipping the classes from the JDK, and variable "$Libraries"
     * means skipping the classes from the application libraries.
     *
     * <p>
     * This function will resolve the packages belonging to the variable group first, and
     * then use a greedy algorithm to generate a list of wildcards to cover these packages.
     */
    public static String[] resolveClassFilters(List<Object> unresolvedFilters) {
        if (unresolvedFilters == null || unresolvedFilters.isEmpty()) {
            return new String[0];
        }

        int variableScope = BLOCK_NONE;
        Set<String> hardcodePatterns = new LinkedHashSet<>();
        for (Object filter : unresolvedFilters) {
            if (Objects.equals("$JDK", filter)) {
                variableScope = variableScope | BLOCK_JDK;
            } else if (Objects.equals("$Libraries", filter)) {
                variableScope = variableScope | BLOCK_LIB;
            } else if (filter instanceof String) {
                String value = (String) filter;
                if (StringUtils.isNotBlank(value)) {
                    hardcodePatterns.add(value.trim());
                }
            }
        }

        if (variableScope == BLOCK_NONE) {
            return hardcodePatterns.toArray(new String[0]);
        }

        Set<String> blackList = new LinkedHashSet<>();
        Set<String> whiteList = new LinkedHashSet<>();
        IJavaProject[] javaProjects = ProjectUtils.getJavaProjects();
        for (IJavaProject javaProject : javaProjects) {
            try {
                IPackageFragmentRoot[] roots = javaProject.getAllPackageFragmentRoots();
                for (IPackageFragmentRoot root : roots) {
                    if (isOnBlackList(root, variableScope)) {
                        collectPackages(root, blackList);
                    } else {
                        collectPackages(root, whiteList);
                    }
                }
            } catch (JavaModelException e) {
                logger.log(Level.SEVERE, String.format("Failed to get the classpath entry for the PackageFragmentRoot: %s", e.toString()), e);
            }
        }

        return convertToExclusionPatterns(blackList, whiteList, hardcodePatterns);
    }

    private static boolean isOnBlackList(IPackageFragmentRoot root, int variableScope) throws JavaModelException {
        if (root.isArchive()) {
            if (variableScope == BLOCK_BIN) {
                return true;
            }

            boolean isJDK = isJDKPackageFragmentRoot(root);
            return (variableScope == BLOCK_JDK && isJDK) || (variableScope == BLOCK_LIB && !isJDK);
        }

        return false;
    }

    private static boolean isJDKPackageFragmentRoot(IPackageFragmentRoot root) throws JavaModelException {
        if (root.getRawClasspathEntry() != null) {
            IPath path = root.getRawClasspathEntry().getPath();
            return path != null && path.segmentCount() > 0
                && Objects.equals(JavaRuntime.JRE_CONTAINER, path.segment(0));
        }

        return false;
    }

    private static void collectPackages(IPackageFragmentRoot root, Set<String> result) throws JavaModelException {
        for (IJavaElement javaElement : root.getChildren()) {
            String elementName = javaElement.getElementName();
            if (javaElement instanceof IPackageFragment
                && ((IPackageFragment) javaElement).hasChildren()) {
                if (StringUtils.isNotBlank(elementName)) {
                    result.add(elementName);
                }
            }
        }
    }

    private static String[] convertToExclusionPatterns(Collection<String> blackList,
        Collection<String> whiteList, Collection<String> hardcodePatterns) {
        List<String> hardcodeBlockedPackages = hardcodePatterns.stream()
            .filter(pattern -> pattern.endsWith(".*"))
            .map(pattern -> pattern.substring(0, pattern.length() - 2))
            .collect(Collectors.toList());
        Trie hardcodeBlackTree = new Trie(hardcodeBlockedPackages);

        // Remove those packages that are on the user hardcode black list.
        List<String> newWhiteList = whiteList.stream()
            .filter(pattern -> !hardcodeBlackTree.isPrefix(pattern))
            .collect(Collectors.toList());

        // Superimpose the white tree on the black tree, then gray out the nodes
        // with the same name.
        Trie whiteTree = new Trie(newWhiteList);
        Trie blackTree = new Trie(blackList);
        superimpose(whiteTree, blackTree);

        // Generate some wildcard patterns to cover all items in the black list.
        List<String> wildcardPatterns = new ArrayList<>();
        traverse(blackTree.root, 0, wildcardPatterns, new ArrayList<>());

        // Append the hardcode patterns to the result.
        for (String name : hardcodePatterns) {
            if (!blackTree.wildcardMatch(name)) {
                wildcardPatterns.add(name);
            }
        }

        return wildcardPatterns.toArray(new String[0]);
    }

    private static void superimpose(Trie upTree, Trie downTree) {
        Queue<TrieNode> upQueue = new LinkedList<>();
        Queue<TrieNode> downQueue = new LinkedList<>();
        upQueue.offer(upTree.root);
        downQueue.offer(downTree.root);
        while (!upQueue.isEmpty()) {
            TrieNode upNode = upQueue.poll();
            TrieNode downNode = downQueue.poll();
            downNode.isGray = true;
            for (Entry<String, TrieNode> entry : upNode.children.entrySet()) {
                if (downNode.children.containsKey(entry.getKey())) {
                    upQueue.offer(entry.getValue());
                    downQueue.offer(downNode.children.get(entry.getKey()));
                }
            }
        }
    }

    private static void traverse(TrieNode root, int depth, List<String> result, List<String> parent) {
        // If the node is gray, that means it also appears in the white list.
        // We cannot use it to generate a wildcard pattern.
        if (!root.isGray) {
            String[] names = new String[depth + 1];
            for (int i = 0; i < depth - 1; i++) {
                names[i] = parent.get(i);
            }

            names[depth - 1] = root.name;
            names[depth] = "*";
            result.add(String.join(".", names));
            return;
        }

        if (depth > 0) {
            if (parent.size() < depth) {
                parent.add(root.name);
            } else {
                parent.set(depth - 1, root.name);
            }
        }

        for (TrieNode child : root.children.values()) {
            traverse(child, depth + 1, result, parent);
        }
    }

    private static class Trie {
        private TrieNode root = new TrieNode();

        public Trie(Collection<String> names) {
            for (String name : names) {
                insert(name);
            }
        }

        public void insert(String name) {
            if (StringUtils.isBlank(name)) {
                return;
            }

            String[] names = name.split("\\.");
            TrieNode currentNode = this.root;
            for (int i = 0; i < names.length; i++) {
                TrieNode node;
                if (currentNode.children.containsKey(names[i])) {
                    node = currentNode.children.get(names[i]);
                } else {
                    node = new TrieNode(names[i]);
                    currentNode.children.put(names[i], node);
                }

                currentNode = node;
            }

            currentNode.isLeaf = true;
        }

        public boolean isPrefix(String name) {
            String[] names = name.split("\\.");
            TrieNode currentNode = this.root;
            for (int i = 0; i < names.length; i++) {
                TrieNode node = currentNode.children.get(names[i]);
                if (node == null) {
                    break;
                }

                currentNode = node;
            }

            return currentNode != this.root && currentNode.isLeaf;
        }

        /**
         * Check whether the name can be covered by the wildcards generated from
         * this trie.
         */
        public boolean wildcardMatch(String name) {
            String[] names = name.split("\\.");
            Map<String, TrieNode> children = this.root.children;
            for (int i = 0; i < names.length; i++) {
                TrieNode node = children.get(names[i]);
                if (node == null) {
                    break;
                } else if (!node.isGray) {
                    return true;
                }

                children = node.children;
            }

            return false;
        }
    }

    private static class TrieNode {
        private String name;
        private Map<String, TrieNode> children = new LinkedHashMap<>();
        private boolean isLeaf = false;
        private boolean isGray = false;

        public TrieNode() {
        }

        public TrieNode(String name) {
            this.name = name;
        }
    }
}
