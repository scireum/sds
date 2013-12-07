package sds;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.commons.PriorityCollector;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.WebContext;
import sirius.web.http.WebDispatcher;

import java.io.File;
import java.io.FileInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    public boolean dispatch(WebContext ctx) throws Exception {
        if (!ctx.getRequest().getUri().startsWith("/artifacts")) {
            return false;
        }
        try {
            if (HttpMethod.POST == ctx.getRequest().getMethod() || HttpMethod.PUT == ctx.getRequest().getMethod()) {
                Matcher m = UPLOAD_URI.matcher(ctx.getRequestedURI());
                if (m.matches()) {
                    File file = ctx.getFileContent();
                    if (file == null) {
                        ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "File-Upload expected!");
                        return true;
                    }
                    if (ctx.get("contentHash").isFilled()) {
                        Hasher h = Hashing.md5().newHasher();
                        try (FileInputStream in = new FileInputStream(ctx.getFileContent())) {
                            byte[] buffer = new byte[8192];
                            int read = in.read(buffer);
                            while (read > 0) {
                                h.putBytes(buffer, 0, read);
                                read = in.read(buffer);
                            }
                            if (!h.hash().toString().equals(ctx.get("hash").asString())) {
                                ctx.respondWith().error(HttpResponseStatus.BAD_REQUEST, "MD5 checksum mismatch");
                                return true;
                            }
                        }
                    }
                    if (repository.canWriteAccess(m.group(1),
                                                  ctx.get("user").asString(),
                                                  ctx.get("hash").asString(),
                                                  ctx.get("timestamp").asInt(0))) {
                        repository.handleUpload(m.group(1), ctx.getFileContent());
                        ctx.respondWith().status(HttpResponseStatus.OK);
                    } else {
                        ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
                    }
                    return true;
                } else {
                    ctx.respondWith()
                       .error(HttpResponseStatus.BAD_REQUEST, "Expected an URI like /artifacts/package-name");
                    return true;
                }
            } else {
                Matcher m = DOWNLOAD_URI.matcher(ctx.getRequestedURI());
                if (m.matches() && !"/_index".equals(m.group(3))) {
                    String artifact = m.group(1);
                    String version = m.group(2);
                    String path = m.group(3);
                    if (repository.canAccess(m.group(1),
                                             ctx.get("user").asString(),
                                             ctx.get("hash").asString(),
                                             ctx.get("timestamp").asInt(0))) {
                        int v = repository.convertVersion(artifact, version);
                        repository.sendContent(artifact, v, path, ctx);
                    } else {
                        ctx.respondWith().status(HttpResponseStatus.UNAUTHORIZED);
                    }

                    return true;
                }
            }
        } catch (Throwable t) {
            ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.handle(t));
            return true;
        }

        return false;
    }
}
