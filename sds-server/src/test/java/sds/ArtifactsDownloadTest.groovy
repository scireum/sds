/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpResponseStatus
import sirius.kernel.BaseSpecification
import sirius.web.http.TestRequest

class ArtifactsDownloadTest extends BaseSpecification {

    def "GET /artifacts/closed/textfile3.txt not authenticated returns an error"() {
        given:
        def request = TestRequest.GET("/artifacts/closed/textfile3.txt")
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.UNAUTHORIZED
    }

    def "GET /artifacts/closed/textfile3.txt authenticated provides the file to download"() {
        given:
        def request = SDSTestHelper.getClosedRepositoryAuthenticatedRequest("/artifacts/closed/textfile3.txt")
        when:
        def response = request.executeAndBlock()
        then:
        Hashing.md5().hashBytes(response.getRawContent()).toString() == "9b8e5eddca6bc187247d24b7d086f791"
    }

    def "GET /artifacts/public/textfile.txt provides the file to download"() {
        given:
        def request = TestRequest.GET("/artifacts/public/textfile.txt")
        when:
        def response = request.executeAndBlock()
        then:
        Hashing.md5().hashBytes(response.getRawContent()).toString() == "df0a9498a65ca6e20dc58022267f339a"
    }

    def "GET /artifacts/public/latest/textfile.txt provides the file to download (legacy)"() {
        given:
        def request = TestRequest.GET("/artifacts/public/latest/textfile.txt")
        when:
        def response = request.executeAndBlock()
        then:
        Hashing.md5().hashBytes(response.getRawContent()).toString() == "df0a9498a65ca6e20dc58022267f339a"
    }

    def "GET /artifacts/public/nofile.txt returns an error"() {
        given:
        def request = TestRequest.GET("/artifacts/public/nofile.txt")
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.NOT_FOUND
    }

}
