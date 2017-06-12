/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.xml.StructuredOutput;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.util.List;

/**
 * Answers the requests made by the SDS client.
 */
@Register
public class ArtifactController implements Controller {

    public static final String PARAM_TIMESTAMP = "timestamp";
    private static final String PARAM_ERROR = "error";
    private static final String PARAM_MESSAGE = "message";
    private static final String PARAM_TOKEN = "token";
    private static final String ACCESS_ERROR_MESSAGE = "Cannot access '%s' as '%s'";

    @Part
    private Repository repository;

    @Override
    public void onError(WebContext ctx, HandledException error) {
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult(PARAM_ERROR);
        try {
            out.property(PARAM_ERROR, true);
            out.property(PARAM_MESSAGE, error.getMessage());
        } finally {
            out.endResult();
        }
    }

    /**
     * Shows the main site of SDS.
     *
     * @param ctx the current request
     */
    @Routed("/")
    public void index(WebContext ctx) {
        ctx.respondWith().template("view/main.html");
    }

    /**
     * Provides download of SDS client.
     *
     * @param ctx the current request
     * @throws IOException if providing file fails
     */
    @Routed("/sds.class")
    public void sdsClass(WebContext ctx) throws IOException {
        ctx.respondWith().download("SDS.class").resource(getClass().getResource("/SDS.class").openConnection());
    }

    /**
     * Provides access to all available artifacts. Access is checked for user so only artifacts are shown which the user
     * has access to.
     *
     * @param ctx the current request
     */
    @Routed("/artifacts")
    public void artifacts(WebContext ctx) {
        List<String> artifacts = repository.getArtifacts();
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            out.property(PARAM_ERROR, false);
            out.beginArray("artifacts");
            for (String name : artifacts) {
                if (repository.canAccess(name,
                                         ctx.get("user").asString(),
                                         ctx.get("hash").asString(),
                                         ctx.get(PARAM_TIMESTAMP).asInt(0))) {
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

    /**
     * Provides list of files for the requested artifact. Access is checked for given user.
     *
     * @param ctx      the current request
     * @param artifact to list files of
     */
    @Routed("/artifacts/:1/_index")
    public void index(WebContext ctx, String artifact) {
        final StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            if (!repository.canAccess(artifact,
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get(PARAM_TIMESTAMP).asInt(0))) {
                out.property(PARAM_ERROR, true);
                out.property(PARAM_MESSAGE, Strings.apply(ACCESS_ERROR_MESSAGE, artifact, ctx.get("user").asString()));
                return;
            }
            out.beginArray("files");
            writeFileInfo(out, artifact);
        } catch (Exception e) {
            out.property(PARAM_ERROR, true);
            out.property(PARAM_MESSAGE, Exceptions.handle(e).getMessage());
        } finally {
            out.endResult();
        }
    }

    private void writeFileInfo(StructuredOutput out, String artifact) {
        try {
            repository.getFileIndex(artifact, indexFile -> {
                out.beginObject("entry");
                out.property("name", indexFile.getPath());
                out.property("crc", indexFile.getCRC());
                out.property("size", indexFile.getSize());
                out.endObject();
            });
            out.endArray();
            out.property(PARAM_ERROR, false);
        } catch (Exception e) {
            out.endArray();
            out.property(PARAM_ERROR, true);
            out.property(PARAM_MESSAGE, Exceptions.handle(e).getMessage());
        }
    }

    /**
     * Triggers SDS server to prepare for a new version of artifact.
     *
     * @param ctx      the current request
     * @param artifact the artifact to prepare a new version for
     */
    @Routed("/artifacts/:1/_new-version")
    public void newVersion(WebContext ctx, String artifact) {
        final StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            if (!repository.canAccess(artifact,
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get(PARAM_TIMESTAMP).asInt(0))) {
                out.property(PARAM_ERROR, true);
                out.property(PARAM_MESSAGE, Strings.apply(ACCESS_ERROR_MESSAGE, artifact, ctx.get("user").asString()));
                return;
            }
            out.property(PARAM_TOKEN, repository.handleNewArtifactVersion(artifact));
            out.property(PARAM_ERROR, false);
        } catch (Exception e) {
            out.property(PARAM_ERROR, true);
            out.property(PARAM_MESSAGE, Exceptions.handle(e).getMessage());
        } finally {
            out.endResult();
        }
    }

    /**
     * Triggers SDS server to finalize an updated version and release it.
     *
     * @param ctx      the current request
     * @param artifact the artifact finalize and release
     */
    @Routed("/artifacts/:1/_finalize")
    public void finalizeArtifact(WebContext ctx, String artifact) {
        final StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            if (!repository.canAccess(artifact,
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get(PARAM_TIMESTAMP).asInt(0))) {
                out.property(PARAM_ERROR, true);
                out.property(PARAM_MESSAGE, Strings.apply(ACCESS_ERROR_MESSAGE, artifact, ctx.get("user").asString()));
                return;
            }
            repository.handleFinalizeNewVersion(artifact, ctx.getParameter(PARAM_TOKEN));
            out.property(PARAM_ERROR, false);
        } catch (Exception e) {
            out.property(PARAM_ERROR, true);
            out.property(PARAM_MESSAGE, Exceptions.handle(e).getMessage());
        } finally {
            out.endResult();
        }
    }

    /**
     * Signalizes the SDS server that something went wrong on the client's side while uploading a new version. The
     * creation of the new artifact version is aborted.
     *
     * @param ctx      the current request
     * @param artifact the artifact to cancel creation of new version
     */
    @Routed("/artifacts/:1/_finalize-error")
    public void finalizeErrorArtifact(WebContext ctx, String artifact) {
        final StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        try {
            if (!repository.canAccess(artifact,
                                      ctx.get("user").asString(),
                                      ctx.get("hash").asString(),
                                      ctx.get(PARAM_TIMESTAMP).asInt(0))) {
                out.property(PARAM_ERROR, true);
                out.property(PARAM_MESSAGE, Strings.apply(ACCESS_ERROR_MESSAGE, artifact, ctx.get("user").asString()));
                return;
            }
            repository.handleFinalizeError(artifact, ctx.getParameter(PARAM_TOKEN));
            out.property(PARAM_ERROR, false);
        } catch (Exception e) {
            out.property(PARAM_ERROR, true);
            out.property(PARAM_MESSAGE, Exceptions.handle(e).getMessage());
        } finally {
            out.endResult();
        }
    }
}
