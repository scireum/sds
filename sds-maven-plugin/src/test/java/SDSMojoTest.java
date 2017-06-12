/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

import com.scireum.SDSMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Paths;

public class SDSMojoTest {

    @Test
    public void testExecute() throws NoSuchFieldException, IllegalAccessException, MojoExecutionException {
        SDSMojo mojo = new SDSMojo();
        setField(mojo, "target", Paths.get("src/test/java/resources/fs").toAbsolutePath().toFile());
        setField(mojo, "artifactId", "blabla");
        setField(mojo, "version", "supertolleversion");
        setField(mojo, "server", "http://localhost:9000");
        setField(mojo, "identity", "test");
        setField(mojo, "key", "testtest");
        mojo.execute();
    }

    private void setField(SDSMojo object, String fieldName, Object value)
            throws IllegalAccessException, NoSuchFieldException {
        Field field = SDSMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }
}
