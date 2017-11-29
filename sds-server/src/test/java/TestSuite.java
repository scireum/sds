/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

import com.googlecode.junittoolbox.SuiteClasses;
import com.googlecode.junittoolbox.WildcardPatternSuite;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import sirius.kernel.TestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(WildcardPatternSuite.class)
@SuiteClasses({"**/*Test.class", "**/*Spec.class"})
public class TestSuite {

    @BeforeClass
    public static void setUp() {
        prepareFiles();
        TestHelper.setUp(TestSuite.class);
    }

    @AfterClass
    public static void tearDown() {
        TestHelper.tearDown(TestSuite.class);
    }

    private static void prepareFiles() {
        try {
            Path dataDirectory = Paths.get("data");
            if (dataDirectory.toFile().exists()) {
                sirius.kernel.commons.Files.delete(dataDirectory);
            }
            Files.createDirectory(dataDirectory);

            Path testFilesDirectory = Paths.get("src/test/resources/testfiles/basefiles");

            Files.walk(testFilesDirectory)
                 .filter(path -> path.toFile().isDirectory())
                 .filter(path -> !path.equals(testFilesDirectory))
                 .forEach(path -> {
                     try {
                         Files.createDirectory(dataDirectory.resolve(testFilesDirectory.relativize(path)));
                     } catch (IOException e) {
                         e.printStackTrace();
                     }
                 });
            Files.walk(testFilesDirectory).filter(path -> path.toFile().isFile()).forEach(file -> {
                try {
                    Files.copy(file, dataDirectory.resolve(testFilesDirectory.relativize(file)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
