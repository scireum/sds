package sds;

import org.joda.time.DateTime;
import sirius.kernel.commons.BasicCollector;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.kernel.xml.StructuredOutput;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;

@Register
public class ArtifactController implements Controller {
    @Override
    public void onError(WebContext ctx, HandledException error) {
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult("error");
        try {
            out.property("error", true);
            out.property("message", error.getMessage());
        } finally {
            out.endResult();
        }
    }

    @Part
    private Repository repository;

    @Routed("/artifacts")
    public void artifacts(WebContext ctx) throws IOException {
        List<String> artifacts = repository.getArtifacts();
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            out.property("error", false);
            out.beginArray("artifacts");
            for (String name : artifacts) {
                if (repository.canAccess(name,
                                         ctx.get("user").asString(),
                                         ctx.get("hash").asString(),
                                         ctx.get("timestamp").asInt(0))) {
                    out.beginObject("artifact");
                    out.property("name", name);
                    out.endObject();
                }
            }
            out.endArray();
        } finally {
            out.endResult();
        }
    }

    @Routed("/artifacts/:1")
    public void versions(WebContext ctx, String artifact) throws IOException {
        List<Tuple<String, File>> versions = repository.getVersions(artifact);
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            out.property("error", false);
            out.beginArray("versions");
            if (repository.canAccess(artifact,
                                     ctx.get("user").asString(),
                                     ctx.get("hash").asString(),
                                     ctx.get("timestamp").asInt(0))) {
                for (Tuple<String, File> v : versions) {
                    out.beginObject("version");
                    out.property("artifact", artifact);
                    out.property("name", v.getFirst());
                    out.property("date", NLS.toUserString(new DateTime(v.getSecond().lastModified())));
                    out.property("size", NLS.formatSize(v.getSecond().length()));
                    out.endObject();
                }
            }
            out.endArray();
        } finally {
            out.endResult();
        }
    }

    @Routed("/artifacts/:1/:2/_index")
    public void index(WebContext ctx, String artifact, String version) {
        final StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            out.property("error", false);
            version = String.valueOf(repository.convertVersion(artifact, version));
            out.property("version", version);
            out.beginArray("files");
            try {
                if (repository.canAccess(artifact,
                                         ctx.get("user").asString(),
                                         ctx.get("hash").asString(),
                                         ctx.get("timestamp").asInt(0))) {
                    repository.generateIndex(artifact, version, new BasicCollector<ZipEntry>() {
                        @Override
                        public void add(ZipEntry entity) {
                            out.beginObject("entry");
                            out.property("name", entity.getName());
                            out.property("crc", entity.getCrc());
                            out.property("size", entity.getSize());
                            out.endObject();
                        }
                    });
                }
                out.endArray();
            } catch (Throwable e) {
                out.endArray();
                out.property("error", true);
                out.property("message", Exceptions.handle(e).getMessage());
            }
        } catch (Throwable e) {
            out.property("error", true);
            out.property("message", Exceptions.handle(e).getMessage());
        } finally {
            out.endResult();
        }
    }
}
