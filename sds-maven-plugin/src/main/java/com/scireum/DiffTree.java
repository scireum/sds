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
import com.google.common.io.Files;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    public void iterate(Consumer<? super DiffTreeNode> visitor, Predicate<? super DiffTreeNode> filter) {
        root.iterate(visitor, filter);
    }

    public void recomputeHashes() {
        iterate(DiffTreeNode::recomputeHash, file -> true);
    }

    public void calculateDiff(DiffTree changes) {
        changes.iterate(file -> {
            Path absolutePath = file.getAbsolutePath();
            Optional<DiffTreeNode> ownFile = getChild(absolutePath);
            if (ownFile.isPresent()) {
                if (file.getHash().equals(ownFile.get().getHash())) {
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
            }
        }, diffTreeNode -> diffTreeNode.getParent() != null);
        iterate(file -> {
            if (!changes.getChild(file.getAbsolutePath()).isPresent()) {
                file.setChangeMode(ChangeMode.DELETED);
            }
        }, diffTreeNode -> diffTreeNode.getParent() != null);
    }

    public static DiffTree fromJson(JSONArray files) {
        Map<Path, String> hashes = new HashMap<>();
        for (Object file : files) {
            JSONObject fileObject = ((JSONObject) file);
            hashes.put(Paths.get(fileObject.getString("name")), fileObject.getString("md5"));
        }

        return fromMap(hashes);
    }

    public static DiffTree fromFileSystem(Path baseDir) throws IOException {
        Map<Path, String> hashes = new HashMap<>();
        java.nio.file.Files.walk(baseDir).forEach(path -> {
            if (java.nio.file.Files.isRegularFile(path)) {
                try {
                    String hash =
                            ByteStreams.hash(Files.newInputStreamSupplier(path.toFile()), Hashing.md5()).toString();
                    hashes.put(baseDir.relativize(path), hash);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return fromMap(hashes);
    }

    private static DiffTree fromMap(Map<Path, String> hashes) {
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

    public static class DiffTreeNode {
        private String hash;
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

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public void recomputeHash() {
            if (isDirectory()) {
                StringBuilder builder = new StringBuilder();
                for (DiffTreeNode child : getChildren()) {
                    builder.append(child.getHash());
                }

                hash = Hashing.md5().hashString(builder.toString(), Charsets.UTF_8).toString();
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

        public boolean isFile() {
            return children.isEmpty();
        }

        public boolean isDirectory() {
            return !isFile();
        }

        public void iterate(Consumer<? super DiffTreeNode> visitor, Predicate<? super DiffTreeNode> filter) {
            getChildren().forEach(file -> file.iterate(visitor, filter));
            if (filter.test(this)) {
                visitor.accept(this);
            }
        }
    }
}
