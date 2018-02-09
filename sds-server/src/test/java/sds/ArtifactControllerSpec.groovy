/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sds

import com.alibaba.fastjson.JSONObject
import sirius.kernel.BaseSpecification
import sirius.web.http.TestRequest
import sirius.web.http.TestResponse
import sirius.web.security.UserContextHelper

class ArtifactControllerSpec extends BaseSpecification {

    def "GET / shows main page"() {
        when:
        def response = TestRequest.GET("/").execute()
        then:
        response.getTemplateName() == "view/main.html.pasta"
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /sds.class downloads the sds client binary"() {
        when:
        def response = TestRequest.GET("/sds.class").execute()
        then:
        response.getType() == TestResponse.ResponseType.RESOURCE
        and:
        response.getRawContent().size() > 0
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts not authenticated returns only public visible artifacts"() {
        when:
        def response = TestRequest.GET("/artifacts").execute()
        then:
        response.getContentAsJson().get("error") == false
        and:
        response.getContentAsJson().getJSONArray("artifacts").size() == 1
        response.getContentAsJson().getJSONArray("artifacts").find { artifact -> ((JSONObject) artifact).getString("name") == "public" }
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts authenticated returns public visible artifacts and artifacts visible to user"() {
        when:
        def response = SDSTestRequest.GET("/artifacts").asUser("closed").execute()
        then:
        response.getContentAsJson().get("error") == false
        and:
        response.getContentAsJson().getJSONArray("artifacts").size() == 2
        response.getContentAsJson().getJSONArray("artifacts").find { artifact -> ((JSONObject) artifact).getString("name") == "closed" }
        response.getContentAsJson().getJSONArray("artifacts").find { artifact -> ((JSONObject) artifact).getString("name") == "public" }
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts/closed/_index not authenticated returns an error"() {
        when:
        def response = TestRequest.GET("/artifacts/closed/_index").execute()
        then:
        response.getContentAsJson().get("error") == true
        and:
        response.getContentAsJson().containsKey("files") == false
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts/closed/_index authenticated returns files of repository"() {
        when:
        def response = SDSTestRequest.GET("/artifacts/closed/_index").asUser("closed").execute()
        then:
        response.getContentAsJson().get("error") == false
        and:
        response.getContentAsJson().containsKey("files") == true
        and:
        response.getContentAsJson().getJSONArray("files").size() == 1
        and:
        def textfile3Object = new JSONObject()
        !textfile3Object.put("size", 21)
        !textfile3Object.put("crc", 2526507779)
        !textfile3Object.put("name", "textfile3.txt")
        assert response.getContentAsJson().getJSONArray("files").contains(textfile3Object)
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts/public/_index shows all files present for the artifact"() {
        when:
        def response = TestRequest.GET("/artifacts/public/_index").execute()
        then:
        response.getContentAsJson().get("error") == false
        and:
        response.getContentAsJson().containsKey("files") == true
        and:
        def files = response.getContentAsJson().getJSONArray("files")
        and:
        files.size() == 2
        and:
        def textfile1Map = new HashMap<>()
        !textfile1Map.put("size", 19)
        !textfile1Map.put("crc", 3150753763)
        !textfile1Map.put("name", "textfile.txt")
        def textfile2Map = new HashMap<>()
        !textfile2Map.put("size", 20)
        !textfile2Map.put("crc", 1084811105)
        !textfile2Map.put("name", "subdirectory/textfile2.txt")
        files.removeAll { file -> file == textfile1Map || file == textfile2Map }
        files.size() == 0
        and:
        UserContextHelper.expectNoMessages(response)
    }

    def "GET /artifacts/public/latest/_index shows all files present for the artifact (legacy)"() {
        when:
        def response = TestRequest.GET("/artifacts/public/latest/_index").execute()
        then:
        response.getContentAsJson().get("error") == false
        and:
        response.getContentAsJson().containsKey("files") == true
        and:
        def files = response.getContentAsJson().getJSONArray("files")
        and:
        files.size() == 2
        and:
        def textfile1Map = new HashMap<>()
        !textfile1Map.put("size", 19)
        !textfile1Map.put("crc", 3150753763)
        !textfile1Map.put("name", "textfile.txt")
        def textfile2Map = new HashMap<>()
        !textfile2Map.put("size", 20)
        !textfile2Map.put("crc", 1084811105)
        !textfile2Map.put("name", "subdirectory/textfile2.txt")
        files.removeAll { file -> file == textfile1Map || file == textfile2Map }
        files.size() == 0
        and:
        UserContextHelper.expectNoMessages(response)
    }

}
