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
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.extensions.Extension;
import sirius.kernel.extensions.Extensions;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.http.MimeHelper;
import sirius.web.http.WebContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stores uploaded artifacts in the file system.
 */
@Register(classes = Repository.class)
public class Repository {

    private static final Log LOG = Log.get("sds");

    private ReentrantLock lock = new ReentrantLock();

    @ConfigValue("sds.repositoryPath")
    private String repositoryPath;

    @ConfigValue("sds.maxArtifacts")
    private int maxArtifacts;

    public void handleUpload(String artifact, File data) throws IOException {
        lock.lock();
        try {
            int version = 1;
            File baseDir = getArtifactBaseDir(artifact);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            } else {
                version = getLatestVersion(artifact) + 1;
            }
            File versionDir = new File(baseDir, String.valueOf(version));
            versionDir.mkdirs();
            final File artifactFile = new File(versionDir, "artifact.zip");
            Files.copy(data, artifactFile);
            while (baseDir.listFiles().length > maxArtifacts) {
                if (!deleteOldest(baseDir, versionDir)) {
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean deleteOldest(File baseDir, File versionDir) {
        try {
            File oldest = null;
            for (File dir : baseDir.listFiles()) {
                if (!dir.equals(versionDir) && (oldest == null || dir.lastModified() < oldest.lastModified())) {
                    oldest = dir;
                }
            }
            if (oldest == null) {
                return false;
            }

            for (File child : oldest.listFiles()) {
                child.delete();
            }
            oldest.delete();

            return true;
        } catch (Exception e) {
            Exceptions.handle(e);
            return false;
        }
    }

    private File getArtifactBaseDir(String artifact) throws IOException {
        File repo = getRepositoryPath();
        return new File(repo, artifact);
    }

    private File getRepositoryPath() throws IOException {
        File repo = new File(repositoryPath);
        if (!repo.exists()) {
            throw new IOException(Strings.apply("Repository base path does not exist: %s", repositoryPath));
        }
        if (!repo.isDirectory()) {
            throw new IOException(Strings.apply("Repository base path isn't a directory: %s", repositoryPath));
        }
        return repo;
    }

    public int getLatestVersion(String artifact) throws IOException {
        lock.lock();
        try {
            File baseDir = getArtifactBaseDir(artifact);
            if (!baseDir.exists()) {
                throw new IOException(Strings.apply("Unknown artifact: %s", artifact));
            }
            int maxVersion = 0;
            for (File child : baseDir.listFiles()) {
                if (child.isDirectory() && child.getName().matches("\\d+")) {
                    int version = Integer.parseInt(child.getName());
                    maxVersion = Math.max(maxVersion, version);
                }
            }
            if (maxVersion == 0) {
                throw new IOException(Strings.apply("No version available for: %s", artifact));
            }
            return maxVersion;
        } finally {
            lock.unlock();
        }
    }

    public void sendContent(String artifact, int version, String path, WebContext ctx) throws IOException {
        File baseDir = getArtifactBaseDir(artifact);
        if (!baseDir.exists()) {
            ctx.respondWith().error(HttpResponseStatus.NOT_FOUND, Strings.apply("Unknown artifact: %s", artifact));
            return;
        }
        File versionDir = new File(baseDir, String.valueOf(version));
        if (!versionDir.exists()) {
            ctx.respondWith().error(HttpResponseStatus.NOT_FOUND, Strings.apply("Unknown version: %d", version));
            return;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try (ZipFile zf = new ZipFile(new File(versionDir, "artifact.zip"))) {
            ZipEntry entry = zf.getEntry(path);
            if (entry == null) {
                ctx.respondWith().error(HttpResponseStatus.NOT_FOUND, Strings.apply("Unknown file: %s", path));
                return;
            }
            try (OutputStream out = ctx.respondWith()
                                       .notCached()
                                       .download(entry.getName())
                                       .outputStream(HttpResponseStatus.OK, MimeHelper.guessMimeType(path))) {
                try (InputStream in = zf.getInputStream(entry)) {
                    ByteStreams.copy(in, out);
                }
            }
        }
    }

    public List<String> getArtifacts() throws IOException {
        List<String> result = Lists.newArrayList();
        for (Extension e : Extensions.getExtensions("artifacts")) {
            result.add(e.getId());
        }

        return result;
    }

    public List<Tuple<String, File>> getVersions(String artifact) throws IOException {
        File baseDir = getArtifactBaseDir(artifact);
        if (!baseDir.exists()) {
            throw new IOException(Strings.apply("Unknown Artifact: %s", artifact));
        }
        List<Tuple<String, File>> result = Lists.newArrayList();
        for (File version : getArtifactBaseDir(artifact).listFiles()) {
            if (version.isDirectory() && !version.getName().startsWith(".")) {
                result.add(Tuple.create(version.getName(), new File(version, "artifact.zip")));
            }
        }
        Collections.sort(result, new Comparator<Tuple<String, File>>() {
            @Override
            public int compare(Tuple<String, File> o1, Tuple<String, File> o2) {
                try {
                    return Integer.parseInt(o2.getFirst()) - Integer.parseInt(o1.getFirst());
                } catch (Throwable e) {
                    Exceptions.ignore(e);
                    return 0;
                }
            }
        });

        return result;
    }

    public void generateIndex(String artifact, String version, Consumer<ZipEntry> indexCollector) throws IOException {
        File baseDir = getArtifactBaseDir(artifact);
        if (!baseDir.exists()) {
            throw new IOException(Strings.apply("Unknown Artifact: %s", artifact));
        }
        File versionDir = new File(baseDir, version);
        if (!versionDir.exists()) {
            throw new IOException(Strings.apply("Unknown Version: %s", version));
        }
        try (ZipFile zf = new ZipFile(new File(versionDir, "artifact.zip"))) {
            Enumeration<? extends ZipEntry> ze = zf.entries();
            while (ze.hasMoreElements()) {
                ZipEntry entry = ze.nextElement();
                if (!entry.isDirectory()) {
                    indexCollector.accept(entry);
                }
            }
        }
    }

    public int convertVersion(String artifact, String version) throws IOException {
        if ("latest".equals(version)) {
            return getLatestVersion(artifact);
        } else {
            return Integer.parseInt(version);
        }
    }

    private static final long ONE_DAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

    public boolean canAccess(String artifact, String user, String hash, int timestamp) {
        return canAccess(artifact, user, hash, timestamp, true);
    }

    @SuppressWarnings("unchecked")
    public boolean canAccess(String artifact, String user, String hash, int timestamp, boolean acceptPublic) {
        try {
            Extension artExt = Extensions.getExtension("artifacts", artifact);
            if (artExt.isDefault()) {
                LOG.WARN("Rejected access to unknown artifact: " + artifact);
                return false;
            }
            if (artExt.get("publicAccessible").asBoolean(false)) {
                if (acceptPublic) {
                    return acceptPublic;
                }
            }
            if (timestamp < TimeUnit.SECONDS.convert(System.currentTimeMillis() - ONE_DAY, TimeUnit.MILLISECONDS)) {
                LOG.WARN("Rejected access to artifact: " + artifact + " - timestamp is outdated!");
                return false;
            }
            Extension userExt = Extensions.getExtension("users", user);
            if (userExt.isDefault()) {
                LOG.WARN("Rejected access by unknown user: " + user);
                return false;
            }
            List<String> arts = (List<String>) userExt.get("artifacts").get();
            if (arts == null || (!arts.contains(artifact) && !arts.contains("*"))) {
                LOG.WARN("Rejected access by user: " + user + ". No access to artifact: " + artifact);
                return false;
            }
            String key = userExt.get("key").asString();
            if (Strings.isEmpty(key)) {
                LOG.WARN("Rejected access by user: " + user + ". No key was given!");
                return false;
            }
            String input = user + timestamp + key;
            if (!Hashing.md5().newHasher().putString(input, Charsets.UTF_8).hash().toString().equals(hash)) {
                LOG.WARN("Rejected access by user: " + user + ". Invalid hash!");
                return false;
            }

            return true;
        } catch (Exception e) {
            Exceptions.handle(e);
            return false;
        }
    }

    public boolean canWriteAccess(String artifact, String user, String hash, int timestamp) {
        if (!canAccess(artifact, user, hash, timestamp, false)) {
            return false;
        }

        return Extensions.getExtension("users", user).get("writeAccess").asBoolean(false);
    }
}
