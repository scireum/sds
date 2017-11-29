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
import com.google.common.io.Files;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.nls.NLS;
import sirius.web.http.TestRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SDSTestHelper {

    public static final String AUTH_USER = "user";
    public static final String AUTH_TIMESTAMP = "timestamp";
    public static final String AUTH_HASH = "hash";

    private SDSTestHelper() {

    }

    public static TestRequest getClosedRepositoryAuthenticatedRequest(String uri) {
        return getRepositoryAuthenticationRequest(uri, "closed");
    }

    public static TestRequest getRepositoryAuthenticationRequest(String uri, String user) {
        return TestRequest.GET(uri, getAuthenticationCredentials(user));
    }

    protected static Context getAuthenticationCredentials(String user) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String key = Sirius.getSettings().getExtension("users", user).get("key").asString();
        Context context = new Context();
        context.put(AUTH_USER, user);
        context.put(AUTH_TIMESTAMP, timestamp);
        context.put(AUTH_HASH,
                    Hashing.md5().newHasher().putString(user + timestamp + key, Charsets.UTF_8).hash().toString());
        return context;
    }

    public static String buildUri(String uri, Context context, Tuple<String, String>... additionalParams) {
        if (context.isEmpty() && additionalParams.length == 0) {
            return uri;
        }
        Arrays.stream(additionalParams).forEach(param -> context.put(param.getFirst(), param.getSecond()));
        uri += "?" + context.entrySet()
                            .stream()
                            .map(e -> e.getKey() + "=" + Strings.urlEncode(NLS.toMachineString(e.getValue())))
                            .collect(Collectors.joining("&"));
        return uri;
    }
}
