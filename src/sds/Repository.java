package sds;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.commons.BasicCollector;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Register(classes = Repository.class)
public class Repository {

    private ReentrantLock lock = new ReentrantLock();

    private Log LOG = Log.get("repo");

    @ConfigValue("sds.repositoryPath")
    private String repositoryPath;

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
        } finally {
            lock.unlock();
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
        ZipFile zf = new ZipFile(new File(versionDir, "artifact.zip"));
        try {
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
        } finally {
            zf.close();
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
                    return 0;
                }
            }
        });

        return result;
    }

    public void generateIndex(String artifact,
                              String version,
                              BasicCollector<ZipEntry> indexCollector) throws IOException {
        File baseDir = getArtifactBaseDir(artifact);
        if (!baseDir.exists()) {
            throw new IOException(Strings.apply("Unknown Artifact: %s", artifact));
        }
        File versionDir = new File(baseDir, version);
        if (!versionDir.exists()) {
            throw new IOException(Strings.apply("Unknown Version: %s", version));
        }
        ZipFile zf = new ZipFile(new File(versionDir, "artifact.zip"));
        try {
            Enumeration<? extends ZipEntry> ze = zf.entries();
            while (ze.hasMoreElements()) {
                ZipEntry entry = ze.nextElement();
                if (!entry.isDirectory()) {
                    indexCollector.add(entry);
                }
            }
        } finally {
            zf.close();
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

    public boolean canAccess(String artifact, String user, String hash, int timestamp, boolean acceptPublic) {
        try {
            Extension artExt = Extensions.getExtension("artifacts", artifact);
            if (artExt.isDefault()) {
                return false;
            }
            if (artExt.get("publicAccessible").asBoolean(false)) {
                return true;
            }
            if (timestamp < TimeUnit.SECONDS.convert(System.currentTimeMillis() - ONE_DAY, TimeUnit.MILLISECONDS)) {
                return false;
            }
            Extension userExt = Extensions.getExtension("users", user);
            if (userExt.isDefault()) {
                return false;
            }
            List<String> arts = (List<String>) userExt.get("artifacts").get();
            if (arts == null || !arts.contains(artifact)) {
                return false;
            }
            String key = userExt.get("key").asString();
            if (Strings.isEmpty(key)) {
                return false;
            }
            String input = user + String.valueOf(timestamp) + key;
            return Hashing.md5().newHasher().putString(input, Charsets.US_ASCII).hash().toString().equals(hash);
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
