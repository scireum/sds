/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.PriorityCollector;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.StructuredOutput;
import sirius.web.http.WebContext;
import sirius.web.http.WebDispatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handle file up- and download.
 */
@Register
public class ArtifactDispatcher implements WebDispatcher {

    private static final Pattern UPLOAD_URI = Pattern.compile("/artifacts/([a-zA-Z0-9_\\-]+)");
    private static final Pattern DOWNLOAD_URI = Pattern.compile("/artifacts/([a-zA-Z0-9_\\-]+)/(.+)");
    private static final Pattern OLD_DOWNLOAD_LATEST_URI =
            Pattern.compile("/artifacts/([a-zA-Z0-9_\\-]+)/(latest)/(.+)");

    /**
     * @deprecated (remove with next version when all clients has been upgraded)
     */
    @Deprecated
    private static final Pattern OLD_DOWNLOAD_VERSION_URI =
            Pattern.compile("/artifacts/([a-zA-Z0-9_\\-]+)/(\\d+)/(.+)");

    @Part
    private Tasks tasks;

    @Part
    private Repository repository;

    @Override
    public int getPriority() {
        return PriorityCollector.DEFAULT_PRIORITY - 1;
    }

    @Override
    public boolean preDispatch(WebContext ctx) throws Exception {
        return false;
    }

    @Override
    public boolean dispatch(final WebContext ctx) throws Exception {
        if (!ctx.getRequest().uri().startsWith("/artifacts")) {
            return false;
        }
        try {
            if (HttpMethod.POST == ctx.getRequest().method() || HttpMethod.PUT == ctx.getRequest().method()) {
                handleFileUpload(ctx);
                return true;
            } else if (HttpMethod.DELETE == ctx.getRequest().method()) {
                handleFileDelete(ctx);
                return true;
            } else {
                return dispatchDownloads(ctx);
            }
        } catch (IOException t) {
            ctx.respondWith()
               .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.createHandled().error(t).handle());
            return true;
        } catch (Exception t) {
            ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.handle(t));
            return true;
        }
    }

    private boolean dispatchDownloads(final WebContext ctx) {
        if (versionsNotSupportedCheck(ctx)) {
            return true;
        }
        Matcher oldM = OLD_DOWNLOAD_LATEST_URI.matcher(ctx.getRequestedURI());
        if (oldM.matches()) {
            if ("_index".equals(oldM.group(3))) {
                return false;
            }
            handleFileDownload(ctx, oldM.group(1), oldM.group(3));
            return true;
        }

        Matcher m = DOWNLOAD_URI.matcher(ctx.getRequestedURI());
        if (m.matches() && !wellKnownRoute(m.group(2))) {
            handleFileDownload(ctx, m.group(1), m.group(2));
            return true;
        }
        return false;
    }

    /**
     * Dispatcher to inform old sds clients that versions are no longer supported.
     *
     * @param ctx the current request
     * @return <tt>true</tt> if the requesting sds client requested a version which is no longer supported,
     * <tt>false</tt> otherwiese
     * @deprecated (remove with next version when all clients has been upgraded)
     */
    @Deprecated
    private boolean versionsNotSupportedCheck(final WebContext ctx) {
        Matcher m = OLD_DOWNLOAD_VERSION_URI.matcher(ctx.getRequestedURI());
        if (!m.matches()) {
            return false;
        }
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult("error");
        try {
            out.property("error", true);
            out.property("message", "Versions are no longer supported by SDS server.");
        } finally {
            out.endResult();
        }
        return true;
    }

    private void handleFileDownload(final WebContext ctx, final String artifact, final String path) {
        if (repository.canAccess(artifact,
                                 ctx.get("user").asString(),
                                 ctx.get("hash").asString(),
                                 ctx.get(ArtifactController.PARAM_TIMESTAMP).asInt(0))) {
            tasks.executor("content")
                 .dropOnOverload(() -> ctx.respondWith()
                                          .error(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                                 "Request dropped - System overload!"))
                 .fork(() -> {
                     try {
                         repository.sendContent(artifact, path, ctx);
                     } catch (IOException e) {
                         Exceptions.ignore(e);
                     }
                 });
        } else {
            ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
        }
    }

    private void handleFileUpload(WebContext ctx) throws IOException {
        Matcher m = UPLOAD_URI.matcher(ctx.getRequestedURI());
        if (!m.matches()) {
            ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "Expected an URI like /artifacts/package-name");
            return;
        }
        File file = ctx.getContentAsFile();
        if (file == null) {
            ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "File-Upload expected!");
            return;
        }
        if (ctx.get("contentHash").isFilled()) {
            long computedContentHash = computeContentHash(ctx);
            long givenContentHash = ctx.get("contentHash").asLong(0);
            if (computedContentHash != givenContentHash) {
                ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "MD5 checksum mismatch");
                return;
            }
        }
        if (repository.canWriteAccess(m.group(1),
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get(ArtifactController.PARAM_TIMESTAMP).asInt(0))) {
            repository.handleUpload(m.group(1),
                                    ctx.get("token").asString(),
                                    Paths.get(ctx.getParameter("path")),
                                    ctx.getContent());
            ctx.respondWith().status(HttpResponseStatus.OK);
        } else {
            ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
        }
    }

    private void handleFileDelete(WebContext ctx) throws IOException {
        Matcher m = UPLOAD_URI.matcher(ctx.getRequestedURI());
        if (!m.matches()) {
            ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "Expected an URI like /artifacts/package-name");
            return;
        }
        if (repository.canWriteAccess(m.group(1),
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get(ArtifactController.PARAM_TIMESTAMP).asInt(0))) {
            repository.handleDelete(m.group(1), ctx.get("token").asString(), Paths.get(ctx.getParameter("path")));
            ctx.respondWith().status(HttpResponseStatus.OK);
        } else {
            ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
        }
    }

    private long computeContentHash(WebContext ctx) throws IOException {
        Hasher h = Hashing.crc32().newHasher();
        try (FileInputStream in = new FileInputStream(ctx.getContentAsFile())) {
            byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            while (read > 0) {
                h.putBytes(buffer, 0, read);
                read = in.read(buffer);
            }
        }
        return h.hash().padToLong();
    }

    private boolean wellKnownRoute(String routePart) {
        return "_index".equals(routePart)
               || "_finalize".equals(routePart)
               || "_new-version".equals(routePart)
               || "_finalize-error".equals(routePart);
    }
}
