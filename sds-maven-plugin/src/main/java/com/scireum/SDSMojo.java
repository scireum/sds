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
import com.google.common.io.CharStreams;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
     * Contains the build classes directory (usually "target/classes") as path
     */
    private Path targetPath;

    /**
     * Contains the name of the file to be deployed. By default we assume a zip created by the assembly plugin:
     * <tt>[artifactId]-[version]-zip.zip</tt>
     */
    @Parameter
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
    @Parameter
    private String developmentArtifact;

    /**
     * Contains the name of the sds artifact to use when a release build is to be deployed. By default we assume the
     * artifact id.
     */
    @Parameter
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

    private String transactionToken;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            if (skip) {
                getLog().info("Skipping (sds.skip is true).");
                return;
            }
            targetPath = target.toPath().resolve("classes");

            String artifact = determineArtifact();
            if (isEmpty(artifact)) {
                getLog().info("No artifact name given - skipping...");
                return;
            }
            getLog().info("Artifact: " + artifact);
            checkServer();
            checkIdentity();
            checkKey();

            DiffTree currentFileList = requestFileList(artifact);
            transactionToken = requestNewVersion(artifact);
            try {
                DiffTree localFileList = createLocalFileList();
                currentFileList.calculateDiff(localFileList);

                currentFileList.iterate(changedFile -> {
                    uploadFile(artifact, changedFile);
                }, changedFile -> changedFile.isFile() && changedFile.getChangeMode() != ChangeMode.SAME);

                requestFinalize(artifact);
            } catch (Exception e) {
                requestFinalizeError(artifact);
                throw e;
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Error while uploading artifact: " + e.getMessage(), e.getCause());
        } catch (Exception e) {
            throw new MojoExecutionException("Error while uploading artifact: " + e.getMessage(), e);
        }
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

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private DiffTree createLocalFileList() throws IOException {
        return DiffTree.fromFileSystem(targetPath);
    }

    /**
     * @param artifact
     * @return the auth token for the transaction
     * @throws IOException
     */
    private String requestNewVersion(String artifact) throws IOException {
        JSONObject result = doRequest(computeURL(artifact, "/_new-version", "&version=" + urlEncode(version)), "GET");

        return result.getString("token");
    }

    private DiffTree requestFileList(String artifact) throws IOException {
        JSONObject result = doRequest(computeURL(artifact, "/_index", "&token=" + transactionToken), "GET");
        JSONArray files = result.getJSONArray("files");

        return DiffTree.fromJson(files);
    }

    private void uploadFile(String artifact, DiffTree.DiffTreeNode changedFile) {
        try {
            switch (changedFile.getChangeMode()) {
                case NEW:
                    uploadNewFile(artifact, changedFile);
                    break;
                case DELETED:
                    uploadDeletedFile(artifact, changedFile);
                    break;
                case CHANGED:
                    uploadChangedFile(artifact, changedFile);
                    break;
                default:
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void uploadChangedFile(String artifact, DiffTree.DiffTreeNode changedFile) throws IOException {
        getLog().info(String.format("Updating %s", changedFile.getAbsolutePath()));

        doUpload(computeURL(artifact,
                            "",
                            "&token="
                            + transactionToken
                            + "&contentHash="
                            + changedFile.getHash()
                            + "&path="
                            + urlEncode(changedFile.getAbsolutePath().toString())),
                 "PUT",
                 targetPath.resolve(changedFile.getAbsolutePath()));
    }

    private void uploadDeletedFile(String artifact, DiffTree.DiffTreeNode changedFile) throws IOException {
        getLog().info(String.format("Deleting %s", changedFile.getAbsolutePath()));

        doRequest(computeURL(artifact,
                             "",
                             "&token=" + transactionToken + "&path=" + urlEncode(changedFile.getAbsolutePath()
                                                                                            .toString())), "DELETE");
    }

    private void uploadNewFile(String artifact, DiffTree.DiffTreeNode changedFile) throws IOException {
        getLog().info(String.format("Creating %s", changedFile.getAbsolutePath()));

        doUpload(computeURL(artifact,
                            "",
                            "&token=" + transactionToken + "&path=" + urlEncode(changedFile.getAbsolutePath()
                                                                                           .toString())),
                 "PUT",
                 targetPath.resolve(changedFile.getAbsolutePath()));
    }

    private void requestFinalize(String artifact) throws IOException {
        doRequest(computeURL(artifact, "/_finalize", "&token=" + transactionToken), "GET");
    }

    private void requestFinalizeError(String artifact) {
        try {
            doRequest(computeURL(artifact, "/_finalize-error", "&token=" + transactionToken), "GET");
        } catch (Exception e) {
            getLog().warn(e);
        }
    }

    private URL computeURL(String artifact, String path, String queryParameters) throws MalformedURLException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String input = identity + timestamp + key;
        String hash = Hashing.md5().hashString(input, Charsets.UTF_8).toString();
        String url = "/artifacts/"
                     + artifact
                     + path
                     + "?user="
                     + urlEncode(identity)
                     + "&timestamp="
                     + urlEncode(timestamp)
                     + "&hash="
                     + urlEncode(hash)
                     + queryParameters;
        if (server != null && server.startsWith("http")) {
            return new URL(server + url);
        } else {
            return new URL("https://" + server + url);
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(Charsets.UTF_8.name(), e);
        }
    }

    private JSONObject doRequest(URL url, String method) throws IOException {
        getLog().info(method + " " + url.toString());

        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setDoOutput(false);
        c.connect();
        try (InputStream is = c.getInputStream()) {
            String jsonText = CharStreams.toString(new InputStreamReader(is));
            JSONObject json = new JSONObject(jsonText);

            if (json.has("error") && json.getBoolean("error")) {
                throw new IOException("Cannot perform request: "
                                      + json.getString("message")
                                      + " ("
                                      + c.getResponseCode()
                                      + ")");
            }
            return json;
        }
    }

    private void doUpload(URL url, String method, Path file) throws IOException {
        getLog().info(method + " " + url.toString());

        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setChunkedStreamingMode(8192);
        c.setRequestMethod(method);
        c.setDoOutput(true);

        upload(file, c.getOutputStream());

        if (c.getResponseCode() != 200) {
            throw new IOException("Cannot upload artifact: "
                                  + c.getResponseMessage()
                                  + " ("
                                  + c.getResponseCode()
                                  + ")");
        }
        getLog().info("Artifact successfully uploaded to: " + server);
    }

    private void upload(Path file, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = Files.size(file);
        long bytesSoFar = 0;
        long lastBytesReported = 0;
        long lastTimeReported = System.currentTimeMillis();
        try (InputStream input = java.nio.file.Files.newInputStream(file, StandardOpenOption.READ)) {
            int read = input.read(buffer);
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
                read = input.read(buffer);
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
}
