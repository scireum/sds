/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import io.netty.handler.codec.http.HttpMethod;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Context;
import sirius.web.http.TestRequest;

public class SDSTestRequest extends TestRequest {

    public static final String AUTH_USER = "user";
    public static final String AUTH_TIMESTAMP = "timestamp";
    public static final String AUTH_HASH = "hash";

    private SDSTestRequest(HttpMethod method, String uri) {
        super(method, uri);
    }

    public static SDSTestRequest GET(String uri) {
        return new SDSTestRequest(HttpMethod.GET, uri);
    }

    public static SDSTestRequest POST(String uri) {
        return new SDSTestRequest(HttpMethod.POST, uri);
    }

    public static SDSTestRequest DELETE(String uri) {
        return new SDSTestRequest(HttpMethod.DELETE, uri);
    }

    public static SDSTestRequest PUT(String uri) {
        return new SDSTestRequest(HttpMethod.PUT, uri);
    }

    public SDSTestRequest asUser(String user) {
        withParameters(getAuthenticationCredentials(user));

        return this;
    }

    public SDSTestRequest withToken(String token) {
        withParameter("token", token);

        return this;
    }

    public static Context getAuthenticationCredentials(String user) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String key = Sirius.getSettings().getExtension("users", user).get("key").asString();
        Context context = new Context();
        context.put(AUTH_USER, user);
        context.put(AUTH_TIMESTAMP, timestamp);
        context.put(AUTH_HASH,
                    Hashing.md5().newHasher().putString(user + timestamp + key, Charsets.UTF_8).hash().toString());
        return context;
    }
}
