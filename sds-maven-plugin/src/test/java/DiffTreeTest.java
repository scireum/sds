/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.scireum.ChangeMode;
import com.scireum.DiffTree;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Optional;

public class DiffTreeTest {

    @Test
    public void testFromFileSystemTest() throws IOException {
        DiffTree tree = getFileSystemTree();

        Optional<DiffTree.DiffTreeNode> file1 = tree.getChild(Paths.get("abc/def/ghi.datei"));
        Optional<DiffTree.DiffTreeNode> file2 = tree.getChild(Paths.get("rst.datei"));
        Optional<DiffTree.DiffTreeNode> file3 = tree.getChild(Paths.get("abc/xyz.datei"));

        Assert.assertTrue(file1.isPresent());
        Assert.assertTrue(file2.isPresent());
        Assert.assertTrue(file3.isPresent());

        Assert.assertNotNull(file1.get().getHash());
        Assert.assertNotNull(file2.get().getHash());
        Assert.assertNotNull(file3.get().getHash());

        Assert.assertEquals("abc/def/ghi.datei", file1.get().getAbsolutePath().toString());
        Assert.assertEquals("rst.datei", file2.get().getAbsolutePath().toString());
        Assert.assertEquals("abc/xyz.datei", file3.get().getAbsolutePath().toString());

        Assert.assertEquals(2, tree.getRoot().getChildren().size());
        DiffTree.DiffTreeNode childAbc = tree.getRoot().getChild(Paths.get("abc")).get();
        Assert.assertEquals(2, childAbc.getChildren().size());
        Assert.assertNotNull(childAbc.getHash());
        Assert.assertEquals("abc", childAbc.getAbsolutePath().toString());
    }

    @Test
    public void fromJSONTest() throws IOException {
        DiffTree tree = getJsonTree();

        Optional<DiffTree.DiffTreeNode> file1 = tree.getChild(Paths.get("abc/def/ghi.datei"));
        Optional<DiffTree.DiffTreeNode> file2 = tree.getChild(Paths.get("abc/def/jkl.datei"));
        Optional<DiffTree.DiffTreeNode> file3 = tree.getChild(Paths.get("abc/xyz.datei"));

        Assert.assertTrue(file1.isPresent());
        Assert.assertTrue(file2.isPresent());
        Assert.assertTrue(file3.isPresent());

        Assert.assertEquals("abc/def/ghi.datei", file1.get().getAbsolutePath().toString());
        Assert.assertEquals("abc/def/jkl.datei", file2.get().getAbsolutePath().toString());
        Assert.assertEquals("abc/xyz.datei", file3.get().getAbsolutePath().toString());

        Assert.assertEquals("12345", file1.get().getHash());
        Assert.assertEquals("67890", file2.get().getHash());
        Assert.assertEquals("d41d8cd98f00b204e9800998ecf8427e", file3.get().getHash());

        Assert.assertEquals(1, tree.getRoot().getChildren().size());
        DiffTree.DiffTreeNode childAbc = tree.getRoot().getChildren().get(0);
        Assert.assertEquals(2, childAbc.getChildren().size());
        Assert.assertNotNull(childAbc.getHash());
        Assert.assertEquals("abc", childAbc.getAbsolutePath().toString());
    }

    @Test
    public void diffTest() throws IOException {
        DiffTree fsTree = getFileSystemTree();
        DiffTree jsonTree = getJsonTree();

        jsonTree.calculateDiff(fsTree);

        Optional<DiffTree.DiffTreeNode> file1 = jsonTree.getChild(Paths.get("abc/def/ghi.datei"));
        Optional<DiffTree.DiffTreeNode> file2 = jsonTree.getChild(Paths.get("abc/def/jkl.datei"));
        Optional<DiffTree.DiffTreeNode> file3 = jsonTree.getChild(Paths.get("abc/xyz.datei"));
        Optional<DiffTree.DiffTreeNode> file4 = jsonTree.getChild(Paths.get("rst.datei"));

        Assert.assertTrue(file1.isPresent());
        Assert.assertTrue(file2.isPresent());
        Assert.assertTrue(file3.isPresent());
        Assert.assertTrue(file4.isPresent());

        Assert.assertEquals("abc/def/ghi.datei", file1.get().getAbsolutePath().toString());
        Assert.assertEquals("abc/def/jkl.datei", file2.get().getAbsolutePath().toString());
        Assert.assertEquals("abc/xyz.datei", file3.get().getAbsolutePath().toString());
        Assert.assertEquals("rst.datei", file4.get().getAbsolutePath().toString());

        Assert.assertEquals(ChangeMode.CHANGED, file1.get().getChangeMode());
        Assert.assertEquals(ChangeMode.DELETED, file2.get().getChangeMode());
        Assert.assertEquals(ChangeMode.SAME, file3.get().getChangeMode());
        Assert.assertEquals(ChangeMode.NEW, file4.get().getChangeMode());
    }

    private DiffTree getFileSystemTree() throws IOException {
        return DiffTree.fromFileSystem(Paths.get("src/test/java/resources/fs/classes"));
    }

    private DiffTree getJsonTree() throws IOException {
        String json = CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream("/example-list.json"),
                                                                 Charsets.UTF_8));
        return DiffTree.fromJson(new JSONObject(json).getJSONArray("files"));
    }
}
