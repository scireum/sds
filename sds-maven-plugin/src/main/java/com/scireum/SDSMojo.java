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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Deploys the given file to an SDS-Server
 */
@Mojo(name = "sds", defaultPhase = LifecyclePhase.DEPLOY)
public class SDSMojo extends AbstractMojo {

    /**
     * Contains the build directory (usually "target")
     */
    @Parameter(property = "project.build.directory")
    private File target;

    /**
     * Contains the name of the file to be deployed. By default we assume a zip created by the assembly plugin:
     * <tt>[artifactId]-[version]-zip.zip</tt>
     */
    @Parameter(defaultValue = "")
    private String file;

    /**
     * Contains the artifact id (used to determine the file name if not given).
     */
    @Parameter(property = "project.artifactId")
    private String artifactId;

    /**
     * Contains the version (used to determine the file name if not given).
     */
    @Parameter(property = "project.version")
    private String version;

    /**
     * Contains the name of the sds artifact to use when a SNAPSHOT build is to be deployed. If this is left empty,
     * SNAPSHOTS are ignored.
     */
    @Parameter(defaultValue = "")
    private String developmentArtifact;

    /**
     * Contains the name of the sds artifact to use when a release build is to be deployed. By default we assume the
     * artifact id.
     */
    @Parameter(defaultValue = "")
    private String releaseArtifact;

    /**
     * Contains the name of the SDS server. By default we check the property <tt>sds.server</tt>.
     */
    @Parameter(property = "sds.server")
    private String server;

    /**
     * Contains the user name used for authentication at the SDS server.
     * By default we check the property <tt>sds.identity</tt>.
     */
    @Parameter(property = "sds.identity")
    private String identity;

    /**
     * Contains the password used for authentication at the SDS server.
     * By default we check the property <tt>sds.key</tt>.
     */
    @Parameter(property = "sds.key")
    private String key;

    /**
     * Determines if this plugin should be skipped.
     */
    @Parameter(property = "sds.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            if (skip) {
                getLog().info("Skipping (sds.skip is true).");
                return;
            }
            String artifact = determineArtifact();
            if (isEmpty(artifact)) {
                getLog().info("No artifact name given - skipping...");
                return;
            }
            getLog().info("Artifact: " + artifact);
            File artifactFile = determineArtifactFile();
            checkServer();
            checkIdentity();
            checkKey();
            String contentHash = computeContentHash(artifactFile);
            URL url = computeURL(artifact, contentHash);
            putArtifact(artifactFile, url);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Throwable e) {
            throw new MojoExecutionException("Error while uploading artifact: " + e.getMessage(), e);
        }
    }

    private void putArtifact(File artifactFile, URL url) throws IOException, MojoExecutionException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setChunkedStreamingMode(8192);
        c.setRequestMethod("PUT");
        c.setDoOutput(true);

        upload(artifactFile, c.getOutputStream());

        if (c.getResponseCode() != 200) {
            throw new MojoExecutionException("Cannot upload artifact: "
                                             + c.getResponseMessage()
                                             + " ("
                                             + c.getResponseCode()
                                             + ")");
        } else {
            getLog().info("Artifact successfully uploaded to: " + server);
        }
    }

    private void upload(File artifactFile, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = artifactFile.length();
        long bytesSoFar = 0;
        long lastBytesReported = 0;
        long lastTimeReported = System.currentTimeMillis();
        try (FileInputStream in = new FileInputStream(artifactFile)) {
            int read = in.read(buffer);
            while (read > 0) {
                outputStream.write(buffer, 0, read);
                bytesSoFar += read;
                long now = System.currentTimeMillis();
                if (now - lastTimeReported > 5000) {
                    long timeDiff = (now - lastTimeReported) / 1000;
                    if (timeDiff == 0) {
                        timeDiff++;
                    }
                    long bytesDiff = bytesSoFar - lastBytesReported;
                    if (totalBytes == 0) {
                        totalBytes++;
                    }

                    getLog().info(String.format("Uploading... (%d%%, %s KB/s)",
                                                100 * bytesSoFar / totalBytes,
                                                (bytesDiff / 1024) / timeDiff));

                    lastTimeReported = now;
                    lastBytesReported = bytesSoFar;
                }
                read = in.read(buffer);
            }
            long timeDiff = (System.currentTimeMillis() - lastTimeReported) / 1000;
            if (timeDiff == 0) {
                timeDiff++;
            }
            long bytesDiff = bytesSoFar - lastBytesReported;
            if (totalBytes == 0) {
                totalBytes++;
            }
            getLog().info(String.format("Uploaded (%s KB/s)", (bytesDiff / 1024) / timeDiff));
        }
    }

    private URL computeURL(String artifact, String contentHash) throws MalformedURLException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String input = identity + timestamp + key;
        String hash = Hashing.md5().newHasher().putString(input, Charsets.UTF_8).hash().toString();
        return new URL("http://"
                       + server
                       + "/artifacts/"
                       + artifact
                       + "?contentHash="
                       + urlEncode(contentHash)
                       + "&user="
                       + urlEncode(identity)
                       + "&timestamp="
                       + urlEncode(timestamp)
                       + "&hash="
                       + urlEncode(hash));
    }

    private String computeContentHash(File artifactFile) throws IOException {
        return ByteStreams.hash(Files.newInputStreamSupplier(artifactFile), Hashing.md5()).toString();
    }

    private String determineArtifact() {
        if (isEmpty(version) || version.contains("SNAPSHOT")) {
            return developmentArtifact;
        } else {
            if (isEmpty(releaseArtifact)) {
                return artifactId;
            } else {
                return releaseArtifact;
            }
        }
    }

    private void checkKey() throws MojoExecutionException {
        if (isEmpty(key)) {
            throw new MojoExecutionException("No SDS-Key was given (key or ${sds.key})");
        } else {
            getLog().info("Key is present...");
        }
    }

    private void checkIdentity() throws MojoExecutionException {
        if (isEmpty(identity)) {
            throw new MojoExecutionException("No SDS-Identity was given (identity or ${sds.identity})");
        } else {
            getLog().info("Identity: " + identity);
        }
    }

    private void checkServer() throws MojoExecutionException {
        if (isEmpty(server)) {
            throw new MojoExecutionException("No SDS-Server was given (server or ${sds.server})");
        } else {
            getLog().info("Server: " + server);
        }
    }

    private File determineArtifactFile() throws MojoExecutionException {
        if (isEmpty(file)) {
            file = artifactId + "-" + version + "-zip.zip";
        }
        File artifactFile = new File(target, file);
        if (!artifactFile.exists()) {
            throw new MojoExecutionException("File " + artifactFile.getAbsolutePath() + " does not exist!");
        }
        getLog().info("Uploading file: " + artifactFile.getAbsolutePath());
        return artifactFile;
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(Charsets.UTF_8.name(), e);
        }
    }
}
