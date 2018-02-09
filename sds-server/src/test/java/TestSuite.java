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
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

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
            try (Stream<Path> stream = Files.walk(testFilesDirectory)) {
                stream.filter(path -> path.toFile().isFile()).forEach(file -> {
                    try {
                        Files.createDirectories(dataDirectory.resolve(testFilesDirectory.relativize(file.getParent())));
                        Files.copy(file, dataDirectory.resolve(testFilesDirectory.relativize(file)));
                    } catch (IOException e) {
                        throw Exceptions.handle(e);
                    }
                });
            }
        } catch (IOException e) {
            throw Exceptions.handle(e);
        }
    }
}
