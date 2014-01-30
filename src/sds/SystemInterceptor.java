/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.Interceptor;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;

import java.lang.reflect.Method;

/**
 * Blocks access to /system/console, /system/logs, /system/errors and /system/fail for all untrusted clients.
 * <p>
 * By default an untrusted client is everything but localhost, but can be configured using
 * <dd>http.firewall.trustedIPs</dd>
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/01
 */
@Register
public class SystemInterceptor implements Interceptor {

    @Override
    public boolean before(WebContext ctx, Controller controller, Method method) throws Exception {
        if ("/system/console".equals(method.getAnnotation(Routed.class).value()) ||
                "/system/errors".equals(method.getAnnotation(Routed.class).value()) ||
                "/system/fail".equals(method.getAnnotation(Routed.class).value()) ||
                "/system/logs".equals(method.getAnnotation(Routed.class).value())) {
            if (!ctx.isTrusted()) {
                ctx.respondWith().error(HttpResponseStatus.FORBIDDEN);
                return true;
            }
        }
        return false;
    }

}
