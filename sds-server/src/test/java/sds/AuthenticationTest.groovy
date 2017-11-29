/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds

import org.apache.log4j.Level
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.kernel.health.LogHelper

class AuthenticationTest extends BaseSpecification {

    @Part
    private static Repository repository

    private int currentTimestamp

    def setup() {
        currentTimestamp = System.currentTimeMillis() / 1000
    }

    def "Authentication to unknown artifact fails"() {
        LogHelper.clearMessages()

        expect:
        !repository.canAccess("unknown", "", "", 0, true)
        LogHelper.hasMessage(Level.WARN, "sds", "Rejected access to unknown artifact: unknown")
    }

    def "Authentication to public succeeds even with invalid credentials"() {
        expect:
        repository.canAccess("public", "", "", 0, true)
        repository.canAccess("public", "unknown", "aaaa", 0, true)
    }

    def "Authentication to closed artifact fails for invalid timestamp"() {
        LogHelper.clearMessages()

        expect:
        !repository.canAccess("closed", "", "", 0, false)
        LogHelper.hasMessage(Level.WARN, "sds", "Rejected access to artifact: closed - timestamp is outdated!")
    }

    def "Authentication to closed artifact fails for unknown user"() {
        LogHelper.clearMessages()

        expect:
        !repository.canAccess("closed", "unknown", "", currentTimestamp, false)
        LogHelper.hasMessage(Level.WARN, "sds", "Rejected access by unknown user: unknown")
    }

    def "Authentication to closed artifact fails for restricted user"() {
        LogHelper.clearMessages()

        expect:
        !repository.canAccess("closed", "no-artifacts", "", currentTimestamp, false)
        LogHelper.hasMessage(Level.WARN, "sds", "Rejected access by user: no-artifacts. No access to artifact: closed")
    }

    def "Authentication to closed artifact fails for users without key"() {
        LogHelper.clearMessages()

        expect:
        !repository.canAccess("closed", "no-key", "", currentTimestamp, false)
        LogHelper.hasMessage(Level.WARN, "sds", "Rejected access by user: no-key. No key was given!")
    }

    def "Authentication to closed artifact fails for invalid hash"() {
        LogHelper.clearMessages()

        expect:
        !repository.canAccess("closed", "closed", "invalid", currentTimestamp, false)
        LogHelper.hasMessage(Level.WARN, "sds", "Rejected access by user: closed. Invalid hash!")
    }

    def "Authentication to closed artifact succeeds for valid data"() {
        def authContext = SDSTestHelper.getAuthenticationCredentials("closed")

        expect:
        repository.canAccess("closed", authContext.getValue(SDSTestHelper.AUTH_USER).asString(), authContext.getValue(SDSTestHelper.AUTH_HASH).asString(), authContext.getValue(SDSTestHelper.AUTH_TIMESTAMP).asInt(0), false)
    }

    def "Authentication to closed artifact succeeds for user with access to all repositories"() {
        def authContext = SDSTestHelper.getAuthenticationCredentials("all-access")

        expect:
        repository.canAccess("closed", authContext.getValue(SDSTestHelper.AUTH_USER).asString(), authContext.getValue(SDSTestHelper.AUTH_HASH).asString(), authContext.getValue(SDSTestHelper.AUTH_TIMESTAMP).asInt(0), false)
    }

    def "Write access to closed artifact fails if user does not have the right"() {
        def authContext = SDSTestHelper.getAuthenticationCredentials("closed")

        expect:
        !repository.canWriteAccess("closed", authContext.getValue(SDSTestHelper.AUTH_USER).asString(), authContext.getValue(SDSTestHelper.AUTH_HASH).asString(), authContext.getValue(SDSTestHelper.AUTH_TIMESTAMP).asInt(0))
    }

    def "Write access to closed artifact succeeds for write access being granted"() {
        def authContext = SDSTestHelper.getAuthenticationCredentials("writer")

        expect:
        repository.canWriteAccess("closed", authContext.getValue(SDSTestHelper.AUTH_USER).asString(), authContext.getValue(SDSTestHelper.AUTH_HASH).asString(), authContext.getValue(SDSTestHelper.AUTH_TIMESTAMP).asInt(0))
    }
}