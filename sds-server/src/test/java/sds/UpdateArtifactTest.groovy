/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds

import com.google.common.io.Files
import io.netty.handler.codec.http.HttpResponseStatus
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Strings
import sirius.kernel.di.std.Part
import sirius.web.security.UserContextHelper

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
        def request = SDSTestRequest.GET("/artifacts/newcreate/_new-version").asUser("uploader")
        when:
        def response = request.execute()
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
        SDSTestRequest.GET("/artifacts/doublecreate/_new-version").asUser("uploader").execute()
        def request = SDSTestRequest.GET("/artifacts/doublecreate/_new-version").asUser("uploader")
        when:
        def response = request.execute()
        then:
        response.getContentAsJson().get("error") == true
        and:
        response.getContentAsJson().getString("message").contains("Artifact is already locked!")
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "POST /artifacts/uploadpost with a file uploads the file to the artifact"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/uploadpost/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.POST("/artifacts/uploadpost")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("uploadpost").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "POST /artifacts/maliciouspost with a path outside the upload directory causes an error"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/maliciouspost/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.POST("/artifacts/maliciouspost")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "../uploadWRONG/test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        !dataPath.resolve("maliciouspost").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
        !dataPath.resolve("maliciouspost").resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/uploadput with a file uploads the file to the artifact"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/uploadput/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.PUT("/artifacts/uploadput")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("uploadput").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/validhash with a file uploads the file to the artifact with a valid hash"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/validhash/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.PUT("/artifacts/validhash")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "test1.txt")
                .withParameter("contentHash", "4173164061")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        dataPath.resolve("validhash").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/invalidhash with an invalid hash returns an error"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/invalidhash/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.PUT("/artifacts/invalidhash")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "test1.txt")
                .withParameter("contentHash", "99999999999")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.BAD_REQUEST
        and:
        response.getErrorMessage() == "CRC32 checksum mismatch"
        and:
        !dataPath.resolve("invalidhash").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/invalidtoken with an invalid token returns an error"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/invalidtoken/_new-version").asUser("uploader").execute()
        def request = SDSTestRequest.PUT("/artifacts/invalidtoken")
                .asUser("uploader")
                .withToken("invalid")
                .withParameter("path", "test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage().contains("Lock token is invalid.")
        and:
        !dataPath.resolve("invalidtoken").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "PUT /artifacts/notoken without a token results in an error"() {
        given:
        SDSTestRequest.GET("/artifacts/notoken/_new-version").asUser("uploader").execute()
        def request = SDSTestRequest.PUT("/artifacts/invalidhash")
                .asUser("uploader")
                .withParameter("path", "test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
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
        def request = SDSTestRequest.PUT("/artifacts/noinit")
                .asUser("uploader")
                .withToken("invalid")
                .withParameter("path", "test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage().contains("No lock for artifact present. Call new version first.")
        and:
        !dataPath.resolve("noinit").resolve(UPLOAD_DIR).resolve("test1.txt").toFile().exists()
    }

    def "DELETE /artifacts/delete deletes the given file in the artifacts upload directory"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/delete/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.DELETE("/artifacts/delete")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "deleteme.txt")
        when:
        def response = request.executeAndBlock()
        then:
        response.getStatus() == HttpResponseStatus.OK
        and:
        !dataPath.resolve("delete").resolve(UPLOAD_DIR).resolve("deleteme.txt").toFile().exists()
    }

    def "GET /artifacts/errorwhileupload/_finalize-error reverts an artifact back to its original state as an error occured"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/errorwhileupload/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        SDSTestRequest.POST("/artifacts/errorwhileupload")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "test1.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
                .execute()
        def request = SDSTestRequest.GET("/artifacts/errorwhileupload/_finalize-error").asUser("uploader").withToken(token)
        when:
        def response = request.execute()
        then:
        response.getContentAsJson().get("error") == false
        and:
        dataPath.resolve("errorwhileupload").resolve(CURRENT_DIR).resolve("test1.txt").toFile().exists()
        and:
        com.google.common.io.Files.equal(dataPath.resolve("errorwhileupload").resolve(CURRENT_DIR).resolve("test1.txt").toFile(), Paths.get("src/test/resources/testfiles/basefiles/errorwhileupload/current/test1.txt").toFile())
    }

    def "GET /artifacts/finalizewithoutchanges/_finalize finalizes a new version of an artifact and releases it"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/finalizewithoutchanges/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        def request = SDSTestRequest.GET("/artifacts/finalizewithoutchanges/_finalize").asUser("uploader").withToken(token)
        when:
        def response = request.execute()
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
        SDSTestRequest.GET("/artifacts/indexwhilepush/_new-version").asUser("uploader").execute()
        def request = SDSTestRequest.GET("/artifacts/indexwhilepush/_index").asUser("all-access")
        when:
        def response = request.execute()
        then:
        response.getContentAsJson().get("error") == true
        and:
        response.getContentAsJson().getString("message").contains("Artifact is currently being uploaded.")
    }

    def "GET /artifacts/pullwhilepush/test3.txt while pushing a new version shows an error"() {
        given:
        SDSTestRequest.GET("/artifacts/pullwhilepush/_new-version").asUser("uploader").execute()
        def request = SDSTestRequest.GET("/artifacts/pullwhilepush/test3.txt").asUser("all-access")
        when:
        def response = request.execute()
        then:
        response.getStatus() == HttpResponseStatus.INTERNAL_SERVER_ERROR
        and:
        response.getErrorMessage() == "Artifact is currently being uploaded."
    }

    def "GET /artifacts/finalizewithchanges/... performs a complete update procedure"() {
        given:
        def initResponse = SDSTestRequest.GET("/artifacts/finalizewithchanges/_new-version").asUser("uploader").execute()
        def token = initResponse.getContentAsJson().getString("token")
        when:
        def createRequest = SDSTestRequest.PUT("/artifacts/finalizewithchanges")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "createme.txt")
                .sendResource("/testfiles/patchfiles/uploadfile.txt")
        def createResponse = createRequest.execute()
        def updateRequest = SDSTestRequest.PUT("/artifacts/finalizewithchanges")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "updateme.txt")
                .sendResource("/testfiles/patchfiles/uploadfile2.txt")
        def updateResponse = updateRequest.execute()
        def deleteRequest = SDSTestRequest.DELETE("/artifacts/finalizewithchanges")
                .asUser("uploader")
                .withToken(token)
                .withParameter("path", "deleteme.txt")
        def deleteResponse = deleteRequest.execute()
        def finalizeRequest = SDSTestRequest.GET("/artifacts/finalizewithchanges/_finalize").asUser("uploader").withToken(token)
        def finalizeResponse = finalizeRequest.execute()
        def backuppedDeleteMeFile = dataPath.resolve("finalizewithchanges").resolve(BACKUP_DIR).resolve("deleteme.txt").toFile()
        def backuppedDoNotTouchMeFile = dataPath.resolve("finalizewithchanges").resolve(BACKUP_DIR).resolve("donottouchme.txt").toFile()
        def backuppedUpdateMeFile = dataPath.resolve("finalizewithchanges").resolve(BACKUP_DIR).resolve("updateme.txt").toFile()
        def backuppedCreateMeFile = dataPath.resolve("finalizewithchanges").resolve(BACKUP_DIR).resolve("createme.txt").toFile()
        def currentDeleteMeFile = dataPath.resolve("finalizewithchanges").resolve(CURRENT_DIR).resolve("deleteme.txt").toFile()
        def currentDoNotTouchMeFile = dataPath.resolve("finalizewithchanges").resolve(CURRENT_DIR).resolve("donottouchme.txt").toFile()
        def currentUpdateMeFile = dataPath.resolve("finalizewithchanges").resolve(CURRENT_DIR).resolve("updateme.txt").toFile()
        def currentCreateMeFile = dataPath.resolve("finalizewithchanges").resolve(CURRENT_DIR).resolve("createme.txt").toFile()
        then:
        createResponse.getStatus() == HttpResponseStatus.OK
        updateResponse.getStatus() == HttpResponseStatus.OK
        deleteResponse.getStatus() == HttpResponseStatus.OK
        finalizeResponse.getContentAsJson().get("error") == false
        and:
        !dataPath.resolve("finalizewithchanges").resolve(UPLOAD_DIR).toFile().exists()
        assert backuppedDeleteMeFile.exists()
        assert backuppedDoNotTouchMeFile.exists()
        assert backuppedUpdateMeFile.exists()
        assert !backuppedCreateMeFile.exists()
        assert !currentDeleteMeFile.exists()
        assert currentDoNotTouchMeFile.exists()
        assert currentUpdateMeFile.exists()
        assert currentCreateMeFile.exists()
        and:
        assert Files.equal(backuppedDeleteMeFile, Paths.get("src/test/resources/testfiles/basefiles/finalizewithchanges/current/deleteme.txt").toFile())
        assert Files.equal(backuppedDoNotTouchMeFile, Paths.get("src/test/resources/testfiles/basefiles/finalizewithchanges/current/donottouchme.txt").toFile())
        assert Files.equal(backuppedUpdateMeFile, Paths.get("src/test/resources/testfiles/basefiles/finalizewithchanges/current/updateme.txt").toFile())
        assert Files.equal(currentCreateMeFile, Paths.get("src/test/resources/testfiles/patchfiles/uploadfile.txt").toFile())
        assert Files.equal(currentUpdateMeFile, Paths.get("src/test/resources/testfiles/patchfiles/uploadfile2.txt").toFile())
        assert Files.equal(currentDoNotTouchMeFile, Paths.get("src/test/resources/testfiles/basefiles/finalizewithchanges/current/donottouchme.txt").toFile())
    }
}
