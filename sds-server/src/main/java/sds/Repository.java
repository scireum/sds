/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.web.http.MimeHelper;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Stores uploaded artifacts in the file system.
 */
@Register(classes = Repository.class)
public class Repository {

    private static final Log LOG = Log.get("sds");

    private static final String BACKUP_DIR = "backup";
    private static final String CURRENT_DIR = "current";
    private static final String UPLOAD_DIR = "upload";

    private static final String PARAM_ARTIFACTS = "artifacts";
    private static final String ERROR_PART_REJECTED_BY_USER = "Rejected access by user: ";

    private ReentrantLock lock = new ReentrantLock();

    private static ConcurrentHashMap<String, Tuple<String, LocalDateTime>> artifactsLocks = new ConcurrentHashMap<>();

    private static final long ONE_DAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

    @ConfigValue("sds.repositoryPath")
    private String repositoryPath;

    @ConfigValue("sds.artifactsLockTime")
    private long artifactsLockTime;

    /**
     * Startes creation of new version of artifact. This MUST be called before files are uploaded for a new version.
     * Otherwise the uploads will fail. This method will also create a custom lock for the artifact so that no
     * duplicated uploads can occur.
     *
     * @param artifact the artifact to start new version of
     * @return token which MUST be used for uploads for the new version
     * @throws IOException if file operations went wrong
     */
    public String handleNewArtifactVersion(String artifact) throws IOException {
        lock.lock();
        String artifactToken = null;
        try {
            artifactToken = lockArtifact(artifact).orElseThrow(() -> Exceptions.createHandled()
                                                                               .withSystemErrorMessage(
                                                                                       "Artifact is already locked!")
                                                                               .handle());
            Path baseDir = getArtifactBaseDir(artifact);
            if (!baseDir.toFile().exists()) {
                Files.createDirectories(baseDir);
            }
            Path currentDir = getCurrentDir(artifact);
            if (!currentDir.toFile().exists()) {
                Files.createDirectories(currentDir);
            }
            Path uploadDir = baseDir.resolve(UPLOAD_DIR);

            Files.deleteIfExists(uploadDir);

            Files.copy(currentDir, uploadDir);
        } catch (HandledException e) {
            throw e;
        } catch (Exception e) {
            releaseArtifactLock(artifact, artifactToken);
            throw e;
        } finally {
            lock.unlock();
        }
        return artifactToken;
    }

    /**
     * Deletes a file from a new version which is currently in creation. In order to create a new version, {@link
     * #handleNewArtifactVersion} has to be called first.
     * <p>
     * If no token was provided or the lock for the artifact has timed out, an exception is thrown.
     *
     * @param artifact the artifact to delete a file from
     * @param token    token for identifying lock for artifact
     * @param file     the file to delete
     * @throws IOException if deletion of file went wrong
     */
    public void handleDelete(String artifact, String token, Path file) throws IOException {
        lock.lock();
        try {
            assertArtifactLock(artifact, token);
            Path filePath = getArtifactBaseDir(artifact).resolve(UPLOAD_DIR).resolve(file);
            Files.deleteIfExists(filePath);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a file to a new version which is currently in creation. In order to create a new version, {@link
     * #handleNewArtifactVersion} has to be called first.
     * <p>
     * If no token was provided or the lock for the artifact has timed out, an exception is thrown.
     *
     * @param artifact    the artifact to add a file to
     * @param token       the token for identifying lock for artifact
     * @param file        Path object containing the new file's path in artifact
     * @param fileContent the content of the file
     * @throws IOException if writing the file fails
     */
    public void handleUpload(String artifact, String token, Path file, InputStream fileContent) throws IOException {
        lock.lock();
        try {
            assertArtifactLock(artifact, token);
            Path filePath = getArtifactBaseDir(artifact).resolve(UPLOAD_DIR).resolve(file);
            Files.createDirectories(filePath.getParent());
            Files.copy(fileContent, filePath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finishes creation of new version of artifact. This is the last method to call in the creation of a new version.
     * The custom lock aquired in {@link #handleNewArtifactVersion} will be released and the new artifact will be
     * available via SDS client.
     *
     * @param artifact the artifact to finish creation of new version for
     * @param token    token for identifying lock for artifact
     * @throws IOException if file operations went wrong
     */
    public void handleFinalizeNewVersion(String artifact, String token) throws IOException {
        lock.lock();
        try {
            assertArtifactLock(artifact, token);
            Path baseDir = getArtifactBaseDir(artifact);

            Path currentDir = getCurrentDir(artifact);

            Path backupDir = baseDir.resolve(BACKUP_DIR);

            Path uploadDir = baseDir.resolve(UPLOAD_DIR);

            Files.deleteIfExists(backupDir);
            Files.move(currentDir, backupDir);
            try {
                Files.move(uploadDir, currentDir);
            } catch (IOException e) {
                Files.move(backupDir, currentDir);
                throw e;
            }
            releaseArtifactLock(artifact, token);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels creation of new version of artifact.
     *
     * @param artifact the artifact to cancel creation of new version for
     * @param token    token for identifying lock for artifact
     * @throws IOException if deleting of backup or upload directory fails
     */
    public void handleFinalizeError(String artifact, String token) throws IOException {
        lock.lock();
        try {
            assertArtifactLock(artifact, token);

            Path baseDir = getArtifactBaseDir(artifact);

            Path backupDir = baseDir.resolve(BACKUP_DIR);

            Path uploadDir = baseDir.resolve(UPLOAD_DIR);

            Files.deleteIfExists(uploadDir);
            Files.deleteIfExists(backupDir);
            releaseArtifactLock(artifact, token);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the {@link Path} leading to the directory containing the current artfact's valid version.
     *
     * @param artifact the artifact to get the path for
     * @return Path object leading to directory containing current valid version
     * @throws IOException if directory is not present
     */
    public Path getCurrentDir(String artifact) throws IOException {
        return getArtifactBaseDir(artifact).resolve(CURRENT_DIR);
    }

    private Path getArtifactBaseDir(String artifact) throws IOException {
        return getRepositoryPath().resolve(artifact);
    }

    private Path getRepositoryPath() throws IOException {
        Path repo = Paths.get(repositoryPath);
        if (!repo.toFile().exists()) {
            throw new IOException(Strings.apply("Repository base path does not exist: %s", repositoryPath));
        }
        if (!repo.toFile().isDirectory()) {
            throw new IOException(Strings.apply("Repository base path isn't a directory: %s", repositoryPath));
        }
        return repo;
    }

    /**
     * Lists all files of current valid version of artifact.
     *
     * @param artifact  the artifact to list files of
     * @param collector the collector which collects all files in the artifact
     * @throws IOException if an exception occures while listing files
     */
    public void getFileIndex(String artifact, Consumer<IndexFile> collector) throws IOException {
        if (!getArtifactBaseDir(artifact).toFile().exists()) {
            throw Exceptions.handle().withSystemErrorMessage("Unknown Artifact: %s", artifact).handle();
        }
        if (isArtifactLocked(artifact)) {
            throw Exceptions.createHandled().withSystemErrorMessage("Artifact is currently being uploaded.").handle();
        }
        Path currentDir = getCurrentDir(artifact);
        if (!currentDir.toFile().exists()) {
            return;
        }
        Files.walkFileTree(currentDir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                collector.accept(new IndexFile(currentDir.relativize(file).toString(),
                                               Files.size(file),
                                               Hashing.md5().hashBytes(Files.readAllBytes(file)).toString()));

                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Sends file of artifact to the requesting client if present.
     *
     * @param artifact the artifact containing the file
     * @param path     the relative path to the file
     * @param ctx      the current request to send file to
     * @throws IOException if sending file fails
     */
    public void sendContent(String artifact, String path, WebContext ctx) throws IOException {
        if (!getArtifactBaseDir(artifact).toFile().exists()) {
            ctx.respondWith().error(HttpResponseStatus.NOT_FOUND, Strings.apply("Unknown artifact: %s", artifact));
            return;
        }
        if (isArtifactLocked(artifact)) {
            ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Artifact is currently being uploaded.");
            return;
        }
        String cuttedPath = path;
        if (cuttedPath.startsWith("/")) {
            cuttedPath = cuttedPath.substring(1);
        }
        Path filePath = getCurrentDir(artifact).resolve(cuttedPath);
        if (!filePath.toFile().exists() || !filePath.toFile().isFile()) {
            ctx.respondWith().error(HttpResponseStatus.NOT_FOUND, Strings.apply("Unknown file: %s", cuttedPath));
            return;
        }
        try (OutputStream out = ctx.respondWith()
                                   .notCached()
                                   .download(filePath.getFileName().toString())
                                   .outputStream(HttpResponseStatus.OK, MimeHelper.guessMimeType(cuttedPath))) {
            Files.copy(filePath, out);
        }
    }

    /**
     * Lists all available artifacts.
     *
     * @return list of artifacts
     */
    public List<String> getArtifacts() {
        List<String> result = Lists.newArrayList();
        for (Extension e : Sirius.getSettings().getExtensions(PARAM_ARTIFACTS)) {
            result.add(e.getId());
        }

        return result;
    }

    /**
     * Determines if the given user can access the a given artifact.
     *
     * @param artifact  the artifact the user ties to access
     * @param user      the username
     * @param hash      the authentication hash
     * @param timestamp timestamp used to calculate hash
     * @return <tt>true</tt> if user can access artifact, <tt>false</tt> otherwise
     */
    public boolean canAccess(String artifact, String user, String hash, int timestamp) {
        return canAccess(artifact, user, hash, timestamp, true);
    }

    /**
     * Determines if the given user can access the a given artifact.
     *
     * @param artifact     the artifact the user ties to access
     * @param user         the username
     * @param hash         the authentication hash
     * @param timestamp    timestamp used to calculate hash
     * @param acceptPublic if access should be granted for public artifacts
     * @return <tt>true</tt> if user can access artifact, <tt>false</tt> otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean canAccess(String artifact, String user, String hash, int timestamp, boolean acceptPublic) {
        try {
            Extension artExt = Sirius.getSettings().getExtension(PARAM_ARTIFACTS, artifact);
            if (artExt.isDefault()) {
                LOG.WARN("Rejected access to unknown artifact: " + artifact);
                return false;
            }
            if (artExt.get("publicAccessible").asBoolean(false) && acceptPublic) {
                return true;
            }
            if (timestamp < TimeUnit.SECONDS.convert(System.currentTimeMillis() - ONE_DAY, TimeUnit.MILLISECONDS)) {
                LOG.WARN("Rejected access to artifact: " + artifact + " - timestamp is outdated!");
                return false;
            }
            Extension userExt = Sirius.getSettings().getExtension("users", user);
            if (userExt.isDefault()) {
                LOG.WARN("Rejected access by unknown user: " + user);
                return false;
            }
            List<String> arts = (List<String>) userExt.get(PARAM_ARTIFACTS).get();
            if (arts == null || (!arts.contains(artifact) && !arts.contains("*"))) {
                LOG.WARN(ERROR_PART_REJECTED_BY_USER + user + ". No access to artifact: " + artifact);
                return false;
            }
            String key = userExt.get("key").asString();
            if (Strings.isEmpty(key)) {
                LOG.WARN(ERROR_PART_REJECTED_BY_USER + user + ". No key was given!");
                return false;
            }
            String input = user + timestamp + key;
            if (!Hashing.md5().newHasher().putString(input, Charsets.UTF_8).hash().toString().equals(hash)) {
                LOG.WARN(ERROR_PART_REJECTED_BY_USER + user + ". Invalid hash!");
                return false;
            }

            return true;
        } catch (Exception e) {
            Exceptions.handle(e);
            return false;
        }
    }

    /**
     * Determines if the given user can write to a given artifact.
     *
     * @param artifact  the artifact to check write access for
     * @param user      the username
     * @param hash      the authentication hash
     * @param timestamp timestamp used to calculate hash
     * @return <tt>true</tt> if user can write to the artifact, <tt>false</tt> otherwise
     */
    public boolean canWriteAccess(String artifact, String user, String hash, int timestamp) {
        if (!canAccess(artifact, user, hash, timestamp, false)) {
            return false;
        }

        return Sirius.getSettings().getExtension("users", user).get("writeAccess").asBoolean(false);
    }

    private synchronized Optional<String> lockArtifact(String artifact) {
        if (isArtifactLocked(artifact)) {
            return Optional.empty();
        }

        String token = Strings.generateCode(7);
        artifactsLocks.put(artifact, Tuple.create(token, LocalDateTime.now()));
        return Optional.of(token);
    }

    private void assertArtifactLock(String artifact, String token) {
        if (!artifactsLocks.containsKey(artifact)) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("No lock for artifact present. Call new version first.")
                            .handle();
        }
        if (artifactsLocks.get(artifact).getSecond().plusSeconds(artifactsLockTime).isBefore(LocalDateTime.now())) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Lock has timed out. Upload needs to be restarted.")
                            .handle();
        }
        if (!Strings.areEqual(artifactsLocks.get(artifact).getFirst(), token)) {
            throw Exceptions.createHandled().withSystemErrorMessage("Lock token is invalid.").handle();
        }
        artifactsLocks.get(artifact).setSecond(LocalDateTime.now());
    }

    private boolean isArtifactLocked(String artifact) {
        return artifactsLocks.containsKey(artifact) && artifactsLocks.get(artifact)
                                                                     .getSecond()
                                                                     .plusSeconds(artifactsLockTime)
                                                                     .isAfter(LocalDateTime.now());
    }

    private void releaseArtifactLock(String artifact, String token) {
        assertArtifactLock(artifact, token);
        artifactsLocks.remove(artifact);
    }
}
