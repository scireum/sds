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
import sirius.web.http.WebContext;
import sirius.web.http.WebDispatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handle file up- and download.
 * <p>
 * Where the uploads are artifacts as ZIP files and downloads are single files from within those ZIPs.
 */
@Register
public class ArtifactDispatcher implements WebDispatcher {
    @Override
    public int getPriority() {
        return PriorityCollector.DEFAULT_PRIORITY - 1;
    }

    private static final Pattern UPLOAD_URI = Pattern.compile("/artifacts/([a-zA-Z0-9_\\-]+)");
    private static final Pattern DOWNLOAD_URI = Pattern.compile("/artifacts/([a-zA-Z0-9_\\-]+)/(latest|\\d+)(/.+)");

    @Part
    private Repository repository;

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
                handleZIPUpload(ctx);
                return true;
            } else {
                Matcher m = DOWNLOAD_URI.matcher(ctx.getRequestedURI());
                if (m.matches() && !"/_index".equals(m.group(3))) {
                    handleFileDownload(ctx, m);
                    return true;
                }
            }
        } catch (IOException t) {
            ctx.respondWith()
               .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.createHandled().error(t).handle());
            return true;
        } catch (Throwable t) {
            ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.handle(t));
            return true;
        }

        return false;
    }

    @Part
    private Tasks tasks;

    private void handleFileDownload(WebContext ctx, Matcher m) throws IOException {
        final String artifact = m.group(1);
        String version = m.group(2);
        final String path = m.group(3);
        if (repository.canAccess(m.group(1),
                                 ctx.get("user").asString(),
                                 ctx.get("hash").asString(),
                                 ctx.get("timestamp").asInt(0))) {
            final int v = repository.convertVersion(artifact, version);
            tasks.executor("content")
                 .dropOnOverload(() -> ctx.respondWith()
                                          .error(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                                 "Request dropped - System overload!"))
                 .fork(() -> {
                     try {
                         repository.sendContent(artifact, v, path, ctx);
                     } catch (IOException e) {
                         Exceptions.ignore(e);
                     }
                 });
        } else {
            ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
        }
    }

    private void handleZIPUpload(WebContext ctx) throws IOException {
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
            String computedContentHash = computeContentHash(ctx);
            String givenContentHash = ctx.get("contentHash").asString();
            if (!computedContentHash.equals(givenContentHash)) {
                ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "MD5 checksum mismatch");
                return;
            }
        }
        if (repository.canWriteAccess(m.group(1),
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get("timestamp").asInt(0))) {
            repository.handleUpload(m.group(1), ctx.getContentAsFile());
            ctx.respondWith().status(HttpResponseStatus.OK);
        } else {
            ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
        }
    }

    private String computeContentHash(WebContext ctx) throws IOException {
        Hasher h = Hashing.md5().newHasher();
        try (FileInputStream in = new FileInputStream(ctx.getContentAsFile())) {
            byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            while (read > 0) {
                h.putBytes(buffer, 0, read);
                read = in.read(buffer);
            }
        }
        return h.hash().toString();
    }
}
