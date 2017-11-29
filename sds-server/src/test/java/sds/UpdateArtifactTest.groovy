/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds

import io.netty.handler.codec.http.HttpResponseStatus
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Strings
import sirius.kernel.commons.Tuple
import sirius.kernel.di.std.Part
import sirius.web.http.TestRequest
import sirius.web.security.UserContextHelper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UpdateArtifactTest extends BaseSpecification {

    @Part
    private static Repository repository

    private static Path dataPath = Paths.get("data")

    private static final String BACKUP_DIR = "backup"
    private static final String CURRENT_DIR = "current"
    private static final String UPLOAD_DIR = "upload"

    def "GET /artifacts/newcreate/_new-version prepares an artifact for new files"() {
        given:
        def request = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/newcreate/_new-version", "uploader")
        when:
        def response = request.executeAndBlock()
        then:
        response.getContentAsJson().get("error") == false
        and:
        Strings.isFilled(response.getContentAsJson().get("token"))
        and:
        dataPath.resolve("newcreate").resolve(UPLOAD_DIR).toFile().exists()
        dataPath.resolve("newcreate").resolve(UPLOAD_DIR).resolve("presentfile.txt").toFile().exists()
        dataPath.resolve("newcreate").resolve(CURRENT_DIR).resolve("presentfile.txt").toFile().exists()
        com.google.common.io.Files.equal(dataPath.resolve("newcreate").resolve(UPLOAD_DIR).resolve("presentfile.txt").toFile(), dataPath.resolve("newcreate").resolve(CURRENT_DIR).resolve("presentfile.txt").toFile())
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts/doublecreate/_new-version called twice returns an error on second call"() {
        given:
        SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/doublecreate/_new-version", "uploader").executeAndBlock()
        def request = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/doublecreate/_new-version", "uploader")
        when:
        def response = request.executeAndBlock()
        then:
        response.getContentAsJson().get("error") == true
        and:
        response.getContentAsJson().getString("message").contains("Artifact is already locked!")
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "POST /artifacts/uploadpost with a file uploads the file to the artifact"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/uploadpost/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def postUrl = SDSTestHelper.buildUri("/artifacts/uploadpost", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "test1.txt"))
        def request = TestRequest.POST(postUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("uploadpost").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "POST /artifacts/maliciouspost with a path outside the upload directory causes an error"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/maliciouspost/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def postUrl = SDSTestHelper.buildUri("/artifacts/maliciouspost", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "../test1.txt"))
        def request = TestRequest.POST(postUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        !dataPath.resolve("maliciouspost").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
        !dataPath.resolve("maliciouspost").resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/uploadput with a file uploads the file to the artifact"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/uploadput/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def putUrl = SDSTestHelper.buildUri("/artifacts/uploadput", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "test1.txt"))
        def request = TestRequest.PUT(putUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("uploadput").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/validhash with a file uploads the file to the artifact with a valid hash"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/validhash/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def putUrl = SDSTestHelper.buildUri("/artifacts/validhash", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "test1.txt"), Tuple.create("contentHash", "4173164061"))
        def request = TestRequest.PUT(putUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("validhash").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/invalidhash with a file uploads the file to the artifact with a valid hash"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/invalidhash/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def putUrl = SDSTestHelper.buildUri("/artifacts/invalidhash", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "test1.txt"), Tuple.create("contentHash", "99999999999"))
        def request = TestRequest.PUT(putUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.BAD_REQUEST
        and:
        response.getErrorMessage() == "CRC32 checksum mismatch"
        and:
        !dataPath.resolve("invalidhash").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/invalidtoken with an invalid token returns an error"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/invalidtoken/_new-version", "uploader").executeAndBlock()
        def putUrl = SDSTestHelper.buildUri("/artifacts/invalidtoken", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", "invalid"), Tuple.create("path", "test1.txt"))
        def request = TestRequest.PUT(putUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage().contains("Lock token is invalid.")
        and:
        !dataPath.resolve("invalidtoken").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/notoken without a token results in an error"() {
        given:
        SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/notoken/_new-version", "uploader").executeAndBlock()
        def putUrl = SDSTestHelper.buildUri("/artifacts/invalidhash", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("path", "test1.txt"))
        def request = TestRequest.PUT(putUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage().contains("Lock token is invalid.")
        and:
        !dataPath.resolve("notoken").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/noinit without first calling new version url returns an error"() {
        given:
        def putUrl = SDSTestHelper.buildUri("/artifacts/noinit", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", "invalid"), Tuple.create("path", "test1.txt"))
        def request = TestRequest.PUT(putUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage().contains("No lock for artifact present. Call new version first.")
        and:
        !dataPath.resolve("noinit").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    // TODO Sirius-Web does not support DELETE in the current version for tests
    /*def "DELETE /artifacts/delete deletes the given file in the artifacts upload directory"() {
        given:
        def initResponse = SDSAuthenticationHelper.getRepositoryAuthenticationRequest("/artifacts/delete/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def deleteUrl = SDSAuthenticationHelper.buildUri("/artifacts/delete", SDSAuthenticationHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "test1.txt"))
        def request = TestRequest.DELETE(deleteUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt")))
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("uploadput").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }*/

    def "GET /artifacts/errorwhileupload/_finalize-error reverts an artifact back to its original state as an error occured"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/errorwhileupload/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def postUrl = SDSTestHelper.buildUri("/artifacts/errorwhileupload", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token), Tuple.create("path", "test1.txt"))
        TestRequest.POST(postUrl, Files.newInputStream(Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt"))).executeAndBlock()
        def requestUrl = SDSTestHelper.buildUri("/artifacts/errorwhileupload/_finalize-error", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token))
        def request = TestRequest.GET(requestUrl)
        when:
        def response = request.executeAndBlock()
        then:
        response.getContentAsJson().get("error") == false
        and:
        dataPath.resolve("errorwhileupload").resolve(CURRENT_DIR).resolve("test1.txt").toFile().exists()
        and:
        com.google.common.io.Files.equal(dataPath.resolve("errorwhileupload").resolve(CURRENT_DIR).resolve("test1.txt").toFile(), Paths.get("src/test/resources/testfiles/basefiles/errorwhileupload/current/test1.txt").toFile())
    }

    def "GET /artifacts/finalizewithoutchanges/_finalize finalizes a new version of an artifact and releases it"() {
        given:
        def initResponse = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/finalizewithoutchanges/_new-version", "uploader").executeAndBlock()
        def token = initResponse.getContentAsJson().getString("token")
        def requestUrl = SDSTestHelper.buildUri("/artifacts/finalizewithoutchanges/_finalize", SDSTestHelper.getAuthenticationCredentials("uploader"), Tuple.create("token", token))
        def request = TestRequest.GET(requestUrl)
        when:
        def response = request.executeAndBlock()
        then:
        response.getContentAsJson().get("error") == false
        and:
        !dataPath.resolve("finalizewithoutchanges").resolve(UPLOAD_DIR).toFile().exists()
        and:
        dataPath.resolve("finalizewithoutchanges").resolve(CURRENT_DIR).resolve("test2.txt").toFile().exists()
        and:
        dataPath.resolve("finalizewithoutchanges").resolve(BACKUP_DIR).resolve("test2.txt").toFile().exists()
        and:
        com.google.common.io.Files.equal(dataPath.resolve("finalizewithoutchanges").resolve(CURRENT_DIR).resolve("test2.txt").toFile(), Paths.get("src/test/resources/testfiles/basefiles/finalizewithoutchanges/current/test2.txt").toFile())
    }

    def "GET /artifacts/indexwhilepush/_index while pushing a new version shows an error"() {
        given:
        SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/indexwhilepush/_new-version", "uploader").executeAndBlock()
        def request = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/indexwhilepush/_index", "all-access")
        when:
        def response = request.executeAndBlock()
        then:
        response.getContentAsJson().get("error") == true
        and:
        response.getContentAsJson().getString("message").contains("Artifact is currently being uploaded.")
    }

    def "GET /artifacts/pullwhilepush/test3.txt while pushing a new version shows an error"() {
        given:
        SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/pullwhilepush/_new-version", "uploader").executeAndBlock()
        def request = SDSTestHelper.getRepositoryAuthenticationRequest("/artifacts/pullwhilepush/test3.txt", "all-access")
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage() == "Artifact is currently being uploaded."
    }

    // TODO new upload with delete and push, check if file is in final version afterwards

}
