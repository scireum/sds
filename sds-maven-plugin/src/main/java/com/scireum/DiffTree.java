/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package com.scireum;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * A tree of {@link DiffTreeNode DiffTreeNodes} which represents the files of a software artifact
 */
public class DiffTree {

    private DiffTreeNode root;

    public DiffTree(DiffTreeNode root) {
        this.root = root;
    }

    public DiffTreeNode getRoot() {
        return root;
    }

    public Optional<DiffTreeNode> getChild(Path path) {
        return getRoot().getChild(path);
    }

    /**
     * Iterates over some nodes in this tree.
     *
     * @param visitor will be called for each {@link DiffTreeNode}
     * @param filter  will be called for each {@link DiffTreeNode}, should return false if a node should be skipped
     */
    public void iterate(Consumer<? super DiffTreeNode> visitor, Predicate<? super DiffTreeNode> filter) {
        root.iterate(file -> {
            visitor.accept(file);
            return true;
        }, filter);
    }

    /**
     * Iterates over some nodes in this tree.
     *
     * @param visitor will be called for each {@link DiffTreeNode}, should return false if iteration should be canceled
     * @param filter  will be called for each {@link DiffTreeNode}, should return false if a node should be skipped
     * @return whether all child nodes and this node have been visited, that is noone has been skipped and
     * {@code visitor} always returned true
     */
    public boolean iterate(Function<? super DiffTreeNode, Boolean> visitor, Predicate<? super DiffTreeNode> filter) {
        return root.iterate(visitor, filter);
    }

    public void recomputeHashes() {
        iterate(DiffTreeNode::recomputeHash, file -> true);
    }

    /**
     * Compares this tree with the given {@code changes}-tree.
     * Updates this tree so that it reflectes the difference to {@code changes} by creating new nodes with
     * {@link ChangeMode#NEW} or marking existent nodes as {@link ChangeMode#CHANGED} or {@link ChangeMode#DELETED}.
     *
     * @param changes the other tree
     */
    public void calculateDiff(DiffTree changes) {
        changes.iterate(file -> {
            Path absolutePath = file.getAbsolutePath();
            Optional<DiffTreeNode> ownFile = getChild(absolutePath);
            if (ownFile.isPresent()) {
                if (file.getHash() == ownFile.get().getHash()) {
                    ownFile.get().setChangeMode(ChangeMode.SAME);
                } else {
                    ownFile.get().setHash(file.getHash());
                    ownFile.get().setChangeMode(ChangeMode.CHANGED);
                }
            } else {
                DiffTreeNode current = root;
                for (int i = 0; i < absolutePath.getNameCount(); i++) {
                    Path subPath = absolutePath.getName(i);
                    Optional<DiffTreeNode> child = current.getChild(subPath);
                    DiffTreeNode newChild = new DiffTreeNode(current, subPath);
                    newChild.setChangeMode(ChangeMode.NEW);
                    if (!child.isPresent()) {
                        current.getChildren().add(newChild);
                    }
                    current = child.orElse(newChild);
                }
                current.setHash(file.getHash());
            }
        }, diffTreeNode -> diffTreeNode.getParent() != null);
        iterate(file -> {
            if (!changes.getChild(file.getAbsolutePath()).isPresent()) {
                file.setChangeMode(ChangeMode.DELETED);
            }
        }, diffTreeNode -> diffTreeNode.getParent() != null);
    }

    /**
     * Creates a file tree based on a list of json objects. Each JSON object must contain a {@code "name"} and a
     * {@code "crc"}.
     *
     * @param files the json data
     * @return a {@link DiffTree} that contains all given files with {@link ChangeMode#SAME}
     */
    public static DiffTree fromJson(JSONArray files) {
        Map<Path, Long> hashes = new HashMap<>();
        for (Object file : files) {
            JSONObject fileObject = (JSONObject) file;
            hashes.put(Paths.get(fileObject.getString("name")), fileObject.getLong("crc"));
        }

        return fromMap(hashes);
    }

    /**
     * Creates a file tree based on the files of a zip archive. {@code ".DS_Store"} and {@code "__MACOSX"} will be
     * skipped.
     *
     * @param zipFile the zip archive
     * @return a {@link DiffTree} that contains all files of the zip archive with {@link ChangeMode#SAME}
     */
    public static DiffTree fromZipFile(ZipFile zipFile) {
        Map<Path, Long> hashes = new HashMap<>();
        zipFile.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
            if (!entry.getName().endsWith(".DS_Store") && !entry.getName().endsWith("__MACOSX")) {
                hashes.put(Paths.get(entry.getName()), entry.getCrc());
            }
        });

        return fromMap(hashes);
    }

    /**
     * Creates a file tree based on the files of a local directory.
     *
     * @param baseDir the path of the local directory
     * @return a {@link DiffTree} that contains all files of the local directory with {@link ChangeMode#SAME}
     * @throws IOException if an I/O error is thrown
     */
    public static DiffTree fromFileSystem(Path baseDir) throws IOException {
        Map<Path, Long> hashes = new HashMap<>();
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.forEach(path -> {
                if (Files.isRegularFile(path)) {
                    try {
                        long hash = ByteStreams.hash(com.google.common.io.Files.newInputStreamSupplier(path.toFile()),
                                                     Hashing.crc32()).padToLong();
                        hashes.put(baseDir.relativize(path), hash);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw e;
            }
        }

        return fromMap(hashes);
    }

    private static DiffTree fromMap(Map<Path, Long> hashes) {
        DiffTreeNode root = new DiffTreeNode(null, Paths.get(""));
        hashes.keySet().stream().map(Path::normalize).forEachOrdered(path -> {
            DiffTreeNode current = root;
            for (int i = 0; i < path.getNameCount(); i++) {
                Path subPath = path.getName(i);
                Optional<DiffTreeNode> child = current.getChild(subPath);
                DiffTreeNode newChild = new DiffTreeNode(current, subPath);
                if (!child.isPresent()) {
                    current.getChildren().add(newChild);
                }
                current = child.orElse(newChild);
            }
            current.setHash(hashes.get(path));
        });

        DiffTree tree = new DiffTree(root);
        tree.recomputeHashes();
        return tree;
    }

    /**
     * @return true if any of this tree's nodes has not {@link ChangeMode#SAME}
     */
    public boolean hasChanges() {
        final AtomicBoolean result = new AtomicBoolean();
        iterate(file -> {
            result.set(true);
            return false;
        }, file -> file.isFile() && file.changeMode != ChangeMode.SAME);
        return result.get();
    }

    /**
     * Represents a single file in a file tree
     */
    public static class DiffTreeNode {

        private long hash;
        private Path path;
        private ChangeMode changeMode = ChangeMode.SAME;
        private DiffTreeNode parent;
        private List<DiffTreeNode> children = new LinkedList<>();

        public DiffTreeNode(DiffTreeNode parent, Path path) {
            this.parent = parent;
            this.path = path;
        }

        public DiffTreeNode getParent() {
            return parent;
        }

        /**
         * {@link ChangeMode#CHANGED} will be replicated to all parent nodes until the root node.
         * <p>
         * {@link ChangeMode#DELETED} or {@link ChangeMode#NEW} will be replicated to all child nodes.
         *
         * @param changeMode the new {@link ChangeMode}
         */
        public void setChangeMode(ChangeMode changeMode) {
            this.changeMode = changeMode;
            if (changeMode == ChangeMode.CHANGED && getParent() != null) {
                getParent().setChangeMode(ChangeMode.CHANGED);
            }
            if (changeMode == ChangeMode.DELETED || changeMode == ChangeMode.NEW) {
                children.forEach(child -> child.setChangeMode(changeMode));
            }
        }

        public ChangeMode getChangeMode() {
            return changeMode;
        }

        public long getHash() {
            return hash;
        }

        public void setHash(long hash) {
            this.hash = hash;
        }

        public void recomputeHash() {
            if (isDirectory()) {
                StringBuilder builder = new StringBuilder();
                for (DiffTreeNode child : getChildren()) {
                    builder.append(child.getHash());
                }

                hash = Hashing.crc32().hashString(builder.toString(), Charsets.UTF_8).padToLong();
            }
        }

        public Path getAbsolutePath() {
            if (getParent() == null) {
                return getPath();
            } else {
                return getParent().getAbsolutePath().resolve(getPath());
            }
        }

        public Path getPath() {
            return path;
        }

        public void setPath(Path path) {
            this.path = path;
        }

        public List<DiffTreeNode> getChildren() {
            return children;
        }

        public Optional<DiffTreeNode> getChild(Path path) {
            if (path.getNameCount() == 1) {
                return getChildren().stream().filter(file -> file.getPath().equals(path)).findFirst();
            } else {
                return getChild(path.getName(0)).flatMap(diffTreeNode -> diffTreeNode.getChild(path.getName(0)
                                                                                                   .relativize(path)));
            }
        }

        /**
         * @return whether this node is a leaf node, that is it represents a file
         */
        public boolean isFile() {
            return children.isEmpty();
        }

        /**
         * @return whether this node is not a leaf node, that is it represents a directory
         */
        public boolean isDirectory() {
            return !isFile();
        }

        /**
         * Iterates over some child nodes of this node.
         *
         * @param visitor will be called for each child and this node, should return false if iteration should be canceled
         * @param filter  will be called for each child and this node, should return false if a node should be skipped
         * @return whether all child nodes and this node have been visited, that is noone has been skipped and
         * {@code visitor} always returned true
         */
        public boolean iterate(Function<? super DiffTreeNode, Boolean> visitor,
                               Predicate<? super DiffTreeNode> filter) {
            for (DiffTreeNode file : getChildren()) {
                if (!file.iterate(visitor, filter)) {
                    return false;
                }
            }
            if (filter.test(this)) {
                return visitor.apply(this);
            } else {
                return true;
            }
        }
    }
}
