package org.mockserver.testing.integration.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.test.Retries;
import org.mockserver.uuid.UUIDService;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.mock.Expectation.when;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Cookie.schemaCookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.Header.schemaHeader;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpRequestModifier.requestModifier;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpResponseModifier.responseModifier;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OpenAPIDefinition.openAPI;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.Parameter.schemaParam;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public abstract class AbstractBasicMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    protected HttpResponse localNotFoundResponse() {
        return response()
            .withStatusCode(NOT_FOUND_404.code())
            .withReasonPhrase(NOT_FOUND_404.reasonPhrase());
    }

    @Test
    public void shouldReturnResponseWithOnlyBody() {
        // when
        Expectation[] upsertedExpectations = mockServerClient.when(request()).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(new Expectation(request()).thenRespond(response().withBody("some_body"))));
    }

    @Test
    public void shouldReturnResponseInHttpAndHttps() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                HEADERS_TO_IGNORE)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseWithOnlyStatusCode() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingSchemaPathAndSchemaMethod() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethodSchema("{" + NEW_LINE +
                        "   \"type\": \"string\"," + NEW_LINE +
                        "   \"pattern\": \"^PO[A-Z]{2}$\"" + NEW_LINE +
                        "}")
                    .withPathSchema("{" + NEW_LINE +
                        "   \"type\": \"string\"," + NEW_LINE +
                        "   \"pattern\": \"some_[a-z]{4}$\"" + NEW_LINE +
                        "}")
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_other_path"))
                    .withMethod("POST"),
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("PUT"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingSchemaPathVariable() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some/path/{variableOne}/{variableTwo}")
                    .withPathParameters(
                        schemaParam("variableO[a-z]{2}", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableOneV[a-z]{4}$\"" + NEW_LINE +
                            "}"),
                        schemaParam("variableTwo", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableTwoV[a-z]{4}$\"" + NEW_LINE +
                            "}")
                    )
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path/variableOneValue/variableTwoValue")),
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/other/path")),
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path/variableOneValue/variableTwoOtherValue")),
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path/variableOneOtherValue/variableTwoValue")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingStringBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(exact("some_random_body"))
            )
            .respond(
                response()
                    .withBody("some_string_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_string_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("some_random_body"),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingSchemaHeaderCookieAndParameter() {
        // when
        mockServerClient
            .when(
                request()
                    .withHeader(schemaHeader(
                        "headerName", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"^headerVal[a-z]{2}$\"" + NEW_LINE +
                            "}"
                    ))
                    .withQueryStringParameter(schemaParam(
                        "parameterName", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"^parameterVal[a-z]{2}$\"" + NEW_LINE +
                            "}"
                    ))
                    .withCookie(schemaCookie(
                        "cookieName", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"^cookieVal[a-z]{2}$\"" + NEW_LINE +
                            "}"
                    ))
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterValue"))
                    .withHeader("headerName", "headerValue")
                    .withCookie("cookieName", "cookieValue")
                ,
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterOtherValue"))
                    .withHeader("headerName", "headerValue")
                    .withCookie("cookieName", "cookieValue")
                ,
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterValue"))
                    .withHeader("headerName", "headerOtherValue")
                    .withCookie("cookieName", "cookieValue")
                ,
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterValue"))
                    .withHeader("headerName", "headerValue")
                    .withCookie("cookieName", "cookieOtherValue")
                ,
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingNotBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
                    .withBody(Not.not(regex(".+")))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_response_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("some_random_body"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseFromVelocityTemplate() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                template(
                    HttpTemplate.TemplateType.VELOCITY,
                    "{" + NEW_LINE +
                        "     \"statusCode\": 200," + NEW_LINE +
                        "     \"headers\": [ { \"name\": \"name\", \"values\": [ \"$!request.headers['name'][0]\" ] } ]," + NEW_LINE +
                        "     \"body\": \"$!request.body\"" + NEW_LINE +
                        "}" + NEW_LINE
                )
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("name", "value")
                .withBody("some_request_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withHeader("name", "value")
                    .withBody("some_request_body"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseFromMustacheTemplate() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                template(
                    HttpTemplate.TemplateType.MUSTACHE,
                    "{" + NEW_LINE +
                        "     \"statusCode\": 200," + NEW_LINE +
                        "     \"headers\": [ { \"name\": \"name\", \"values\": [ \"{{ request.headers.name.0 }}\" ] } ]," + NEW_LINE +
                        "     \"body\": \"{{ request.body }}\"" + NEW_LINE +
                        "}" + NEW_LINE
                )
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("name", "value")
                .withBody("some_request_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withHeader("name", "value")
                    .withBody("some_request_body"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethod() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseForExpectationWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
                    .withDelay(new Delay(SECONDS, 2))
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("some_path1")),
            HEADERS_TO_IGNORE
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldReturnResponseByMatchingOpenAPISpecWithOperationId() throws JsonProcessingException {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(openAPI(
                FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json"),
                "listPets"
            ))
            .respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(new Expectation(openAPI(
            ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")).toPrettyString(),
            "listPets"
        )).thenRespond(response().withBody("some_body"))));
    }

    @Test
    public void shouldReturnResponseByMatchingOpenAPIExpectationWithSpecAndResponse() throws JsonProcessingException {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .upsert(openAPIExpectation(
                FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json"),
                ImmutableMap.of(
                    "listPets", "500",
                    "createPets", "default",
                    "showPetById", "200"
                )
            ));

        // then
        assertEquals(
            response()
                .withStatusCode(INTERNAL_SERVER_ERROR_500.code())
                .withReasonPhrase(INTERNAL_SERVER_ERROR_500.reasonPhrase())
                .withHeader("content-type", "application/json")
                .withBody(json("{" + NEW_LINE +
                    "  \"code\" : 0," + NEW_LINE +
                    "  \"message\" : \"some_string_value\"" + NEW_LINE +
                    "}", MediaType.APPLICATION_JSON)),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("content-type", "application/json")
                .withBody(json("{" + NEW_LINE +
                    "  \"code\" : 0," + NEW_LINE +
                    "  \"message\" : \"some_string_value\"" + NEW_LINE +
                    "}", MediaType.APPLICATION_JSON)),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath("/pets")
                    .withBody(json("{" + NEW_LINE +
                        "  \"id\" : 0," + NEW_LINE +
                        "  \"name\" : \"some_string_value\"," + NEW_LINE +
                        "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                        "}")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("content-type", "application/json")
                .withBody(json("{" + NEW_LINE +
                    "  \"id\" : 0," + NEW_LINE +
                    "  \"name\" : \"some_string_value\"," + NEW_LINE +
                    "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                    "}", MediaType.APPLICATION_JSON)),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets/12345")
                    .withHeader("x-request-id", UUIDService.getUUID()),
                HEADERS_TO_IGNORE)
        );

        // and
        assertThat(upsertedExpectations.length, is(3));
        assertThat(upsertedExpectations[0], is(
            when(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")).toPrettyString(), "listPets")
                .thenRespond(
                    response()
                        .withStatusCode(500)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"code\" : 0," + NEW_LINE +
                            "  \"message\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(upsertedExpectations[1], is(
            when(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")).toPrettyString(), "createPets")
                .thenRespond(
                    response()
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"code\" : 0," + NEW_LINE +
                            "  \"message\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(upsertedExpectations[2], is(
            when(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")).toPrettyString(), "showPetById")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldSupportBatchedExpectations() throws Exception {
        // when
        HttpResponse httpResponse = makeRequest(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withHeader(authorisationHeader())
                .withPath(addContextToPath("expectation"))
                .withBody("" +
                    "[" +
                    new ExpectationSerializer(new MockServerLogger())
                        .serialize(
                            new Expectation(request("/path_one"), once(), TimeToLive.unlimited(), 0)
                                .thenRespond(response().withBody("some_body_one"))
                        ) + "," +
                    new ExpectationSerializer(new MockServerLogger())
                        .serialize(
                            new Expectation(request("/path_two"), once(), TimeToLive.unlimited(), 0)
                                .thenRespond(response().withBody("some_body_two"))
                        ) + "," +
                    new ExpectationSerializer(new MockServerLogger())
                        .serialize(
                            new Expectation(request("/path_three"), once(), TimeToLive.unlimited(), 0)
                                .thenRespond(response().withBody("some_body_three"))
                        ) +
                    "]"
                ),
            HEADERS_TO_IGNORE
        );
        assertThat(httpResponse.getStatusCode(), equalTo(201));

        // then
        Expectation[] upsertedExpectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(httpResponse.getBodyAsString(), true);
        assertThat(upsertedExpectations.length, is(3));
        assertThat(upsertedExpectations[0], is(
            new Expectation(request("/path_one"), once(), TimeToLive.unlimited(), 0)
                .thenRespond(response().withBody("some_body_one"))
        ));
        assertThat(upsertedExpectations[1], is(
            new Expectation(request("/path_two"), once(), TimeToLive.unlimited(), 0)
                .thenRespond(response().withBody("some_body_two"))
        ));
        assertThat(upsertedExpectations[2], is(
            new Expectation(request("/path_three"), once(), TimeToLive.unlimited(), 0)
                .thenRespond(response().withBody("some_body_three"))
        ));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withPath(calculatePath("/path_one")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("/path_two")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_three"),
            makeRequest(
                request()
                    .withPath(calculatePath("/path_three")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_other_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body")
                .withHeaders(
                    header("headerName", "headerValue"),
                    header("set-cookie", "cookieName=cookieValue")
                )
                .withCookies(cookie("cookieName", "cookieValue")),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingPath() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_other_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldNotReturnResponseForNottedHeader() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withHeaders(new Headers(header(NottableString.not("headerName"))))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
            );

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withHeaders(header("headerName", "headerValue")),
                HEADERS_TO_IGNORE)
        );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withHeaders(header("otherHeaderName", "headerValue")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingOpenAPI() throws JsonProcessingException {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(openAPI(
                FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json"),
                "listPets"
            ))
            .respond(response().withBody("some_body"));

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("PUT")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(new Expectation(openAPI(
            ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")).toPrettyString(),
            "listPets"
        )).thenRespond(response().withBody("some_body"))));
    }

    @Test
    public void shouldVerifyReceivedRequestsSpecificTimesInHttpAndHttps() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );

        // then
        mockServerClient.verify(request().withPath(calculatePath("some_path")));
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.exactly(1));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );

        // then
        mockServerClient.verify(request().withPath(calculatePath("some_path")));
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.atLeast(1));
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.exactly(2));
        mockServerClient.verify(request().withPath(calculatePath("some_path")).withSecure(true), VerificationTimes.exactly(1));
        mockServerClient.verify(request().withPath(calculatePath("some_path")).withSecure(false), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldVerifyReceivedRequestsByExpectationId() {
        // when
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_other_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );

        // then
        mockServerClient.verify(firstExpectation.getId(), VerificationTimes.atLeast(1));
        mockServerClient.verify(firstExpectation.getId(), VerificationTimes.exactly(2));
        mockServerClient.verify(secondExpectation.getId(), VerificationTimes.never());
        AssertionError firstAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId(), VerificationTimes.atLeast(1)));
        assertThat(firstAssertionError.getMessage(), startsWith("Request not found at least once"));
        AssertionError secondAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(UUID.randomUUID().toString(), VerificationTimes.atLeast(1)));
        assertThat(secondAssertionError.getMessage(), startsWith("No expectation found with id "));
    }

    @Test
    public void shouldVerifyNotReceivedRequestWithEmptyBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path_no_body"))
                    .withBody(Not.not(regex(".+")))
            )
            .respond(response());
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_no_body")),
                HEADERS_TO_IGNORE)
        );

        // and
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path_with_body"))
                    .withBody("some_request_body")
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_response_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_with_body"))
                    .withBody("some_request_body"),
                HEADERS_TO_IGNORE)
        );

        mockServerClient.verify(request().withPath(calculatePath("some_path_no_body")));
        mockServerClient.verify(request().withPath(calculatePath("some_path_no_body")).withBody(regex(".+")), VerificationTimes.atMost(0));
        mockServerClient.verify(request().withPath(calculatePath("some_path_no_body")).withBody(exact("some_random_body")), VerificationTimes.atMost(0));

        mockServerClient.verify(request().withPath(calculatePath("some_path_with_body")));
        mockServerClient.verify(request().withPath(calculatePath("some_path_with_body")).withBody("some_request_body"));
        mockServerClient.verify(request().withPath(calculatePath("some_path_with_body")).withBody(regex(".+")));
        mockServerClient.verify(request().withPath(calculatePath("some_path_with_body")).withBody(exact("some_other_body")), VerificationTimes.atMost(0));
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        try {
            mockServerClient
                .verify(
                    request()
                        .withPath(calculatePath("some_path")), VerificationTimes.atLeast(2)
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "}> but was:<{"));
        }
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceivedWithMaximumNumberOfRequestToReturnInFailure() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(3)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );

        try {
            mockServerClient
                .verify(
                    request()
                        .withPath(calculatePath("some_path")),
                    VerificationTimes.atLeast(4),
                    2
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), equalTo("Request not found at least 4 times, expected:<{\n" +
                "  \"path\" : \"" + calculatePath("some_path") + "\"\n" +
                "}> but was not found, found 3 other requests"));
        }
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceivedWithOpenAPI() {
        // when
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")), exactly(4))
            .respond(response().withBody("some_body"));

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // then
        mockServerClient
            .verify(
                openAPI()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")),
                VerificationTimes.atLeast(2)
            );
    }

    @Test
    public void shouldVerifyNoRequestsReceived() {
        // when
        mockServerClient.reset();

        // then
        mockServerClient.verifyZeroInteractions();
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                HEADERS_TO_IGNORE)
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedByExceptionId() {
        // when
        Expectation firstExpectation = mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"))[0];
        Expectation secondExpectation = mockServerClient.when(request().withPath(calculatePath("some_other_path.*")), exactly(6)).respond(response().withBody("some_body"))[0];

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                HEADERS_TO_IGNORE
            )
        );
        mockServerClient.verify(firstExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId());
        AssertionError firstAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId()));
        assertThat(firstAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError secondAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId()));
        assertThat(secondAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError thirdAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertThat(thirdAssertionError.getMessage(), startsWith("No expectation found with id "));
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedByOpenAPI() throws JsonProcessingException {
        // when
        String specUrlOrPayload = ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")).toPrettyString();
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload(specUrlOrPayload), exactly(4))
            .respond(response().withBody("some_body"));

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // then
        try {
            mockServerClient
                .verify(
                    openAPI()
                        .withSpecUrlOrPayload(specUrlOrPayload)
                        .withOperationId("createPets"),
                    openAPI()
                        .withSpecUrlOrPayload(specUrlOrPayload)
                        .withOperationId("listPets")
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"operationId\" : \"createPets\"," + NEW_LINE +
                "  \"specUrlOrPayload\" : " + specUrlOrPayload.replaceAll("\n", "\n  ") + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"operationId\" : \"listPets\"," + NEW_LINE +
                "  \"specUrlOrPayload\" : " + specUrlOrPayload.replaceAll("\n", "\n  ") + NEW_LINE +
                "} ]> but was:<[ {"));
        }
        mockServerClient
            .verify(
                openAPI()
                    .withSpecUrlOrPayload(specUrlOrPayload)
                    .withOperationId("listPets"),
                openAPI()
                    .withSpecUrlOrPayload(specUrlOrPayload)
                    .withOperationId("createPets")
            );
    }

    @Test
    public void shouldVerifySequenceNotFoundWithMaximumNumberOfRequestToReturnInFailure() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(3)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                HEADERS_TO_IGNORE)
        );

        try {
            mockServerClient
                .verify(
                    2,
                    request()
                        .withPath(calculatePath("some_other_path")),
                    request()
                        .withPath(calculatePath("some_path"))
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), equalTo("Request sequence not found, expected:<[ {\n" +
                "  \"path\" : \"" + calculatePath("some_other_path") + "\"\n" +
                "}, {\n" +
                "  \"path\" : \"" + calculatePath("some_path") + "\"\n" +
                "} ]> but was not found, found 3 other requests"));
        }
    }

    @Test
    public void shouldRetrieveRecordedRequests() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                HEADERS_TO_IGNORE
            )
        );

        // then
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(request().withPath(calculatePath("some_path.*"))),
            request(calculatePath("some_path_one")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(request()),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestsAndResponse() {
        ConfigurationProperties.temporaryLogLevel("INFO", () -> {
            // when
            ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());
            mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
            assertEquals(
                response("some_body"),
                makeRequest(
                    request().withPath(calculatePath("some_path_one")),
                    HEADERS_TO_IGNORE
                )
            );
            assertEquals(
                notFoundResponse(),
                makeRequest(
                    request().withPath(calculatePath("not_found")),
                    HEADERS_TO_IGNORE
                )
            );
            assertEquals(
                response("some_body"),
                makeRequest(
                    request().withPath(calculatePath("some_path_three")),
                    HEADERS_TO_IGNORE
                )
            );

            // then
            Retries.tryWaitForSuccess(() -> {
                    LogEventRequestAndResponse[] logEventRequestAndResponsesOne = mockServerClient.retrieveRecordedRequestsAndResponses(request().withPath(calculatePath("some_path.*")));
                    verifyRequestsMatches(
                        logEventRequestAndResponsesOne,
                        request(calculatePath("some_path_one")),
                        request(calculatePath("some_path_three"))
                    );
                    expectationSerializer.deserializeArray(mockServerClient.retrieveRecordedRequestsAndResponses(request().withPath(calculatePath("some_path.*")), Format.JSON), false);

                    LogEventRequestAndResponse[] logEventRequestAndResponsesTwo = mockServerClient.retrieveRecordedRequestsAndResponses(request());
                    verifyRequestsMatches(
                        logEventRequestAndResponsesTwo,
                        request(calculatePath("some_path_one")),
                        request(calculatePath("not_found")),
                        request(calculatePath("some_path_three"))
                    );
                    expectationSerializer.deserializeArray(mockServerClient.retrieveRecordedRequestsAndResponses(request(), Format.JSON), false);

                    LogEventRequestAndResponse[] logEventRequestAndResponsesThree = mockServerClient.retrieveRecordedRequestsAndResponses(null);
                    verifyRequestsMatches(
                        logEventRequestAndResponsesThree,
                        request(calculatePath("some_path_one")),
                        request(calculatePath("not_found")),
                        request(calculatePath("some_path_three"))
                    );
                    expectationSerializer.deserializeArray(mockServerClient.retrieveRecordedRequestsAndResponses(null, Format.JSON), false);
                },
                10, 1000, MILLISECONDS
            );
        });
    }

    @Test
    public void shouldRetrieveRecordedRequestsByOpenAPI() {
        // when
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")), exactly(4))
            .respond(response().withBody("some_body"));

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // then
        // TODO(jamesdbloom) why is this path not prefixed with context route?
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(openAPI().withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json"))),
            request()
                .withMethod("GET")
                .withPath("/pets")
                .withQueryStringParameter("limit", "10"),
            request()
                .withMethod("POST")
                .withPath("/pets")
                .withHeader("content-type", "application/json")
                .withBody(json("" +
                    "{" + NEW_LINE +
                    "    \"id\": 50, " + NEW_LINE +
                    "    \"name\": \"scruffles\", " + NEW_LINE +
                    "    \"tag\": \"dog\"" + NEW_LINE +
                    "}"
                ))
        );
    }

    @Test
    public void shouldRetrieveActiveExpectations() {
        // when
        HttpRequest complexRequest = request()
            .withPath(calculatePath("some_path.*"))
            .withHeader("some", "header")
            .withQueryStringParameter("some", "parameter")
            .withCookie("some", "parameter")
            .withBody("some_body");
        mockServerClient.when(complexRequest, exactly(4))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_path.*")))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_other_path")))
            .respond(response().withBody("some_other_body"));
        mockServerClient.when(request().withPath(calculatePath("some_forward_path")))
            .forward(forward());

        // then
        assertThat(
            mockServerClient.retrieveActiveExpectations(request().withPath(calculatePath("some_path.*"))),
            arrayContaining(
                new Expectation(complexRequest, exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body"))
            )
        );

        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(complexRequest, exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )
        );

        assertThat(
            mockServerClient.retrieveActiveExpectations(request()),
            arrayContaining(
                new Expectation(complexRequest, exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )
        );
    }

    @Test
    public void shouldRetrieveRecordedExpectations() throws InterruptedException {
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path.*")),
                exactly(4)
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );
        assertEquals(
            response("some_body_one")
                .withHeader("some", "header")
                .withHeader("cookie", "some=parameter")
                .withHeader("set-cookie", "some=parameter")
                .withCookie("some", "parameter"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_one"))
                    .withHeader("some", "header")
                    .withQueryStringParameter("some", "parameter")
                    .withCookie("some", "parameter")
                    .withBody("some_body_one"),
                HEADERS_TO_IGNORE
            )
        );
        MILLISECONDS.sleep(500);
        assertEquals(
            response("some_body_three"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_three"))
                    .withBody("some_body_three"),
                HEADERS_TO_IGNORE
            )
        );

        // then
        Expectation[] recordedExpectations = mockServerClient.retrieveRecordedExpectations(request().withPath(calculatePath("some_path_one")));
        assertThat(recordedExpectations.length, is(1));
        verifyRequestsMatches(
            new RequestDefinition[]{
                recordedExpectations[0].getHttpRequest()
            },
            request(calculatePath("some_path_one")).withBody("some_body_one")
        );
        assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
        // and
        recordedExpectations = mockServerClient.retrieveRecordedExpectations(request());
        assertThat(recordedExpectations.length, is(2));
        verifyRequestsMatches(
            new RequestDefinition[]{
                recordedExpectations[0].getHttpRequest(),
                recordedExpectations[1].getHttpRequest()
            },
            request(calculatePath("some_path_one")).withBody("some_body_one"),
            request(calculatePath("some_path_three")).withBody("some_body_three")
        );
        assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
        assertThat(recordedExpectations[1].getHttpResponse().getBodyAsString(), is("some_body_three"));
        // and
        recordedExpectations = mockServerClient.retrieveRecordedExpectations(null);
        assertThat(recordedExpectations.length, is(2));
        verifyRequestsMatches(
            new RequestDefinition[]{
                recordedExpectations[0].getHttpRequest(),
                recordedExpectations[1].getHttpRequest()
            },
            request(calculatePath("some_path_one")).withBody("some_body_one"),
            request(calculatePath("some_path_three")).withBody("some_body_three")
        );
        assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
        assertThat(recordedExpectations[1].getHttpResponse().getBodyAsString(), is("some_body_three"));
    }

    @Test
    public void shouldRetrieveRecordedLogMessages() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");

            // when
            UUIDService.fixedUUID = true;
            mockServerClient.reset();
            mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
            assertEquals(
                response("some_body"),
                makeRequest(
                    request().withPath(calculatePath("some_path_one")),
                    HEADERS_TO_IGNORE)
            );
            assertEquals(
                notFoundResponse(),
                makeRequest(
                    request().withPath(calculatePath("not_found")),
                    HEADERS_TO_IGNORE)
            );
            assertEquals(
                response("some_body"),
                makeRequest(
                    request().withPath(calculatePath("some_path_three")),
                    HEADERS_TO_IGNORE)
            );

            // then
            String[] actualLogMessages = mockServerClient.retrieveLogMessagesArray(request().withPath(calculatePath(".*")));

            Object[] expectedLogMessages = new Object[]{
                "resetting all expectations and request logs",
                "creating expectation:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"httpResponse\" : {" + NEW_LINE +
                    "      \"body\" : \"some_body\"" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                    "    \"priority\" : 0," + NEW_LINE +
                    "    \"timeToLive\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"times\" : {" + NEW_LINE +
                    "      \"remainingTimes\" : 4" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " with id:" + NEW_LINE +
                    NEW_LINE +
                    "  " + UUIDService.getUUID() + NEW_LINE,

                new String[]{
                    "received request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_one\"," + NEW_LINE +
                        "    \"headers\" : {"
                },
                new String[]{
                    "request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_one\",",
                    " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"body\" : \"some_body\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"remainingTimes\" : 4" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }"
                },
                new String[]{
                    "returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " for request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_one\",",
                    " for action:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE
                },
                new String[]{
                    "received request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/not_found\"," + NEW_LINE +
                        "    \"headers\" : {"
                },
                new String[]{
                    "request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/not_found\",",
                    " didn't match expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"body\" : \"some_body\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"remainingTimes\" : 3" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " because:" + NEW_LINE +
                        NEW_LINE +
                        "  method matched" + NEW_LINE +
                        "  path didn't match" + NEW_LINE
                },
                new String[]{
                    "no expectation for:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/not_found\"," +
                        NEW_LINE,
                    " returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"statusCode\" : 404," + NEW_LINE +
                        "    \"reasonPhrase\" : \"Not Found\"" + NEW_LINE +
                        "  }" + NEW_LINE

                },
                new String[]{
                    "received request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_three\"," + NEW_LINE +
                        "    \"headers\" : {"
                },
                new String[]{
                    "request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_three\",",
                    " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"body\" : \"some_body\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"remainingTimes\" : 3" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }"
                },
                new String[]{
                    "returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " for request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_three\",",
                    " for action:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE
                }
            };

            for (int i = 0; i < expectedLogMessages.length; i++) {
                if (expectedLogMessages[i] instanceof String) {
                    assertThat("matching log message " + i + "\nActual:" + NEW_LINE + Arrays.toString(actualLogMessages), actualLogMessages[i], endsWith((String) expectedLogMessages[i]));
                } else if (expectedLogMessages[i] instanceof String[]) {
                    String[] expectedLogMessage = (String[]) expectedLogMessages[i];
                    for (int j = 0; j < expectedLogMessage.length; j++) {
                        assertThat("matching log message " + i + "-" + j + "\nActual:" + NEW_LINE + Arrays.toString(actualLogMessages), actualLogMessages[i], containsString(expectedLogMessage[j]));
                    }
                }
            }
        } finally {
            UUIDService.fixedUUID = false;
            if (originalLevel != null) {
                ConfigurationProperties.logLevel(originalLevel.name());
            }
        }
    }

    @Test
    public void shouldClearExpectationsAndLogs() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                HEADERS_TO_IGNORE)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1"))
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path2"))
        );

        // and then - remaining expectations not cleared
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldClearExpectationsAndLogsByOpenAPI() {
        // when
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json")), exactly(4))
            .respond(response().withBody("some_body"));
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // when
        mockServerClient
            .clear(
                openAPI()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json"))
            );


        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("not_found"))
        );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );

        // and then - remaining expectations not cleared
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReset() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // when
        mockServerClient.reset();

        // then
        // - in http
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                HEADERS_TO_IGNORE)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path1")),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path2")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldErrorForInvalidExpectation() throws Exception {
        // when
        HttpResponse httpResponse = makeRequest(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withHeader(authorisationHeader())
                .withPath(addContextToPath("expectation"))
                .withBody("{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/path_one\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"incorrectField\" : {" + NEW_LINE +
                    "    \"body\" : \"some_body_one\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"remainingTimes\" : 1" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"),
            HEADERS_TO_IGNORE
        );

        // then
        assertThat(httpResponse.getStatusCode(), is(400));
        assertThat(httpResponse.getBodyAsString(), is("incorrect expectation json format for:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"httpRequest\" : {" + NEW_LINE +
            "      \"path\" : \"/path_one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"incorrectField\" : {" + NEW_LINE +
            "      \"body\" : \"some_body_one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"times\" : {" + NEW_LINE +
            "      \"remainingTimes\" : 1" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"timeToLive\" : {" + NEW_LINE +
            "      \"unlimited\" : true" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "" + NEW_LINE +
            " schema validation errors:" + NEW_LINE +
            "" + NEW_LINE +
            "  12 errors:\n" +
            "   - $.httpError: is missing, but is required, if specifying action of type Error\n" +
            "   - $.httpForward: is missing, but is required, if specifying action of type Forward\n" +
            "   - $.httpForwardClassCallback: is missing, but is required, if specifying action of type ForwardClassCallback\n" +
            "   - $.httpForwardObjectCallback: is missing, but is required, if specifying action of type ForwardObjectCallback\n" +
            "   - $.httpForwardTemplate: is missing, but is required, if specifying action of type ForwardTemplate\n" +
            "   - $.httpOverrideForwardedRequest: is missing, but is required, if specifying action of type OverrideForwardedRequest\n" +
            "   - $.httpResponse: is missing, but is required, if specifying action of type Response\n" +
            "   - $.httpResponseClassCallback: is missing, but is required, if specifying action of type ResponseClassCallback\n" +
            "   - $.httpResponseObjectCallback: is missing, but is required, if specifying action of type ResponseObjectCallback\n" +
            "   - $.httpResponseTemplate: is missing, but is required, if specifying action of type ResponseTemplate\n" +
            "   - $.incorrectField: is not defined in the schema and the schema does not allow additional properties\n" +
            "   - oneOf of the following must be specified [httpError, httpForward, httpForwardClassCallback, httpForwardObjectCallback, httpForwardTemplate, httpOverrideForwardedRequest, httpResponse, httpResponseClassCallback, httpResponseObjectCallback, httpResponseTemplate]" + NEW_LINE +
            "  " + NEW_LINE +
            "  " + OPEN_API_SPECIFICATION_URL.replaceAll(NEW_LINE, NEW_LINE + "  ")));
    }

    @Test
    public void shouldErrorForInvalidRequest() throws Exception {
        // when
        HttpResponse httpResponse = makeRequest(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withHeader(authorisationHeader())
                .withPath(addContextToPath("clear"))
                .withBody("{" + NEW_LINE +
                    "    \"path\" : 500," + NEW_LINE +
                    "    \"method\" : true," + NEW_LINE +
                    "    \"keepAlive\" : \"false\"" + NEW_LINE +
                    "  }"),
            HEADERS_TO_IGNORE
        );

        // then
        assertThat(httpResponse.getStatusCode(), is(400));
        assertThat(httpResponse.getBodyAsString(), is("incorrect request matcher json format for:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "      \"path\" : 500," + NEW_LINE +
            "      \"method\" : true," + NEW_LINE +
            "      \"keepAlive\" : \"false\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            " schema validation errors:" + NEW_LINE +
            "" + NEW_LINE +
            "  4 errors:\n" +
            "   - $.keepAlive: string found, boolean expected\n" +
            "   - $.method: boolean found, string expected\n" +
            "   - $.path: integer found, string expected\n" +
            "   - $.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            "  " + NEW_LINE +
            "  " + OPEN_API_SPECIFICATION_URL.replaceAll(NEW_LINE, NEW_LINE + "  ")));
    }

    @Test
    public void shouldForwardRequestInHTTPWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
                    .withDelay(new Delay(SECONDS, 2))
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("echo"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE);
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldForwardOverriddenRequestWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest()
                    .withRequestOverride(
                        request()
                            .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                            .withBody("some_overridden_body")
                    )
                    .withDelay(SECONDS, 2)
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("echo"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldForwardOverriddenRequestWithRequestModifier() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("/some/path"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest()
                    .withRequestOverride(
                        request()
                            .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                            .withHeader("overrideHeaderToReplace", "originalValue")
                            .withHeader("overrideHeaderToRemove", "originalValue")
                            .withCookie("overrideCookieToReplace", "originalValue")
                            .withBody("some_overridden_body")
                    )
                    .withRequestModifier(
                        requestModifier()
                            .withPath("^/(.+)/(.+)$", "/prefix/$1/infix/$2/postfix")
                            .withHeaders(
                                ImmutableList.of(
                                    header("headerToAddOne", "addedValue"),
                                    header("headerToAddTwo", "addedValue")
                                ),
                                ImmutableList.of(
                                    header("overrideHeaderToReplace", "replacedValue"),
                                    header("requestHeaderToReplace", "replacedValue"),
                                    header("extraHeaderToReplace", "shouldBeIgnore")
                                ),
                                ImmutableList.of("overrideHeaderToRemove", "requestHeaderToRemove")
                            )
                            .withCookies(
                                ImmutableList.of(
                                    cookie("cookieToAddOne", "addedValue"),
                                    cookie("cookieToAddTwo", "addedValue")
                                ),
                                ImmutableList.of(
                                    cookie("overrideCookieToReplace", "replacedValue"),
                                    cookie("requestCookieToReplace", "replacedValue"),
                                    cookie("extraCookieToReplace", "shouldBeIgnore")
                                ),
                                ImmutableList.of("overrideCookieToRemove", "requestCookieToRemove")
                            )
                    )
            );

        // then
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("/some/path"))
                .withMethod("POST")
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("requestHeaderToReplace", "originalValue")
                .withHeader("requestHeaderToRemove", "originalValue")
                .withCookie("requestCookieToReplace", "replacedValue")
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );

        // and
        HttpRequest echoServerRequest = insecureEchoServer.getLastRequest();
        assertEquals(
            echoServerRequest.withHeaders(filterHeaders(HEADERS_TO_IGNORE, echoServerRequest.getHeaderList())),
            request()
                .withMethod("POST")
                .withPath(calculatePath("/prefix/some/infix/path/postfix"))
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("requestHeaderToReplace", "replacedValue")
                .withHeader("overrideHeaderToReplace", "replacedValue")
                .withHeader("headerToAddOne", "addedValue")
                .withHeader("headerToAddTwo", "addedValue")
                .withHeader("cookie", "overrideCookieToReplace=replacedValue; requestCookieToReplace=replacedValue; cookieToAddOne=addedValue; cookieToAddTwo=addedValue")
                .withCookie("cookieToAddOne", "addedValue")
                .withCookie("cookieToAddTwo", "addedValue")
                .withCookie("overrideCookieToReplace", "replacedValue")
                .withCookie("requestCookieToReplace", "replacedValue")
                .withKeepAlive(true)
                .withSecure(false)
                .withSocketAddress("localhost", insecureEchoServer.getPort(), SocketAddress.Scheme.HTTP)
                .withBody("some_overridden_body")
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("requestHeaderToReplace", "replacedValue")
                .withHeader("overrideHeaderToReplace", "replacedValue")
                .withHeader("headerToAddOne", "addedValue")
                .withHeader("headerToAddTwo", "addedValue")
                .withHeader("set-cookie", "cookieToAddOne=addedValue", "cookieToAddTwo=addedValue", "overrideCookieToReplace=replacedValue", "requestCookieToReplace=replacedValue")
                .withHeader("cookie", "overrideCookieToReplace=replacedValue; requestCookieToReplace=replacedValue; cookieToAddOne=addedValue; cookieToAddTwo=addedValue")
                .withCookie("cookieToAddOne", "addedValue")
                .withCookie("cookieToAddTwo", "addedValue")
                .withCookie("overrideCookieToReplace", "replacedValue")
                .withCookie("requestCookieToReplace", "replacedValue")
                .withBody("some_overridden_body"),
            httpResponse
        );
    }

    @Test
    public void shouldForwardOverriddenRequestWithRequestAndResponseModifiers() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("/some/path"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest()
                    .withRequestOverride(
                        request()
                            .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                            .withHeader("overrideHeaderToReplace", "originalValue")
                            .withHeader("overrideHeaderToRemove", "originalValue")
                            .withCookie("overrideCookieToReplace", "replacedValue")
                            .withCookie("requestCookieToReplace", "replacedValue")
                            .withBody("some_overridden_body")
                    )
                    .withRequestModifier(
                        requestModifier()
                            .withPath("^/(.+)/(.+)$", "/prefix/$1/infix/$2/postfix")
                            .withHeaders(
                                ImmutableList.of(
                                    header("headerToAddOne", "addedValue"),
                                    header("headerToAddTwo", "addedValue")
                                ),
                                ImmutableList.of(
                                    header("overrideHeaderToReplace", "replacedValue"),
                                    header("requestHeaderToReplace", "replacedValue")
                                ),
                                ImmutableList.of("overrideHeaderToRemove", "requestHeaderToRemove")
                            )
                            .withCookies(
                                ImmutableList.of(
                                    cookie("cookieToAddOne", "addedValue"),
                                    cookie("cookieToAddTwo", "addedValue")
                                ),
                                ImmutableList.of(
                                    cookie("overrideCookieToReplace", "replacedValue"),
                                    cookie("requestCookieToReplace", "replacedValue")
                                ),
                                ImmutableList.of("overrideCookieToRemove", "requestCookieToRemove")
                            )
                    )
                    .withResponseModifier(
                        responseModifier()
                            .withHeaders(
                                ImmutableList.of(
                                    header("responseHeaderToAddOne", "addedValue"),
                                    header("responseHeaderToAddTwo", "addedValue")
                                ),
                                ImmutableList.of(
                                    header("overrideHeaderToReplace", "responseReplacedValue"),
                                    header("requestHeaderToReplace", "responseReplacedValue")
                                ),
                                ImmutableList.of("headerToAddOne")
                            )
                            .withCookies(
                                ImmutableList.of(
                                    cookie("responseCookieToAddOne", "addedValue"),
                                    cookie("responseCookieToAddTwo", "addedValue")
                                ),
                                ImmutableList.of(
                                    cookie("overrideCookieToReplace", "responseReplacedValue"),
                                    cookie("requestCookieToReplace", "responseReplacedValue")
                                ),
                                ImmutableList.of("cookieToAddOne")
                            )
                    )
            );

        // then
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("/some/path"))
                .withMethod("POST")
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("requestHeaderToReplace", "originalValue")
                .withHeader("requestHeaderToRemove", "originalValue")
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );

        // and
        HttpRequest echoServerRequest = insecureEchoServer.getLastRequest();
        assertEquals(
            echoServerRequest.withHeaders(filterHeaders(HEADERS_TO_IGNORE, echoServerRequest.getHeaderList())),
            request()
                .withMethod("POST")
                .withPath(calculatePath("/prefix/some/infix/path/postfix"))
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("requestHeaderToReplace", "replacedValue")
                .withHeader("overrideHeaderToReplace", "replacedValue")
                .withHeader("headerToAddOne", "addedValue")
                .withHeader("headerToAddTwo", "addedValue")
                .withHeader("cookie", "overrideCookieToReplace=replacedValue; requestCookieToReplace=replacedValue; cookieToAddOne=addedValue; cookieToAddTwo=addedValue")
                .withCookie("cookieToAddOne", "addedValue")
                .withCookie("cookieToAddTwo", "addedValue")
                .withCookie("overrideCookieToReplace", "replacedValue")
                .withCookie("requestCookieToReplace", "replacedValue")
                .withKeepAlive(true)
                .withSecure(false)
                .withSocketAddress("localhost", insecureEchoServer.getPort(), SocketAddress.Scheme.HTTP)
                .withBody("some_overridden_body")
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("responseHeaderToAddOne", "addedValue")
                .withHeader("responseHeaderToAddTwo", "addedValue")
                .withHeader("requestHeaderToReplace", "responseReplacedValue")
                .withHeader("overrideHeaderToReplace", "responseReplacedValue")
                .withHeader("headerToAddTwo", "addedValue")
                .withHeader("set-cookie", "cookieToAddTwo=addedValue", "overrideCookieToReplace=responseReplacedValue", "requestCookieToReplace=responseReplacedValue", "responseCookieToAddOne=addedValue", "responseCookieToAddTwo=addedValue")
                .withHeader("cookie", "overrideCookieToReplace=replacedValue; requestCookieToReplace=replacedValue; cookieToAddOne=addedValue; cookieToAddTwo=addedValue")
                .withCookie("cookieToAddOne", "addedValue")
                .withCookie("cookieToAddTwo", "addedValue")
                .withCookie("overrideCookieToReplace", "responseReplacedValue")
                .withCookie("requestCookieToReplace", "responseReplacedValue")
                .withCookie("responseCookieToAddOne", "addedValue")
                .withCookie("responseCookieToAddTwo", "addedValue")
                .withBody("some_overridden_body"),
            httpResponse
        );
    }

    @Test
    public void shouldForwardOverriddenRequestWithEmptyRequestAndResponseModifiers() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("/some/path"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest()
                    .withRequestOverride(
                        request()
                            .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                            .withHeader("overrideHeaderToReplace", "originalValue")
                            .withHeader("overrideHeaderToRemove", "originalValue")
                            .withCookie("overrideCookieToReplace", "replacedValue")
                            .withCookie("requestCookieToReplace", "replacedValue")
                            .withBody("some_overridden_body")
                    )
                    .withRequestModifier(
                        requestModifier()
                    )
                    .withResponseModifier(
                        responseModifier()
                    )
            );

        // then
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("/some/path"))
                .withMethod("POST")
                .withHeader("x-test", "test_headers_and_body")
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );

        // and
        HttpRequest echoServerRequest = insecureEchoServer.getLastRequest();
        assertEquals(
            echoServerRequest.withHeaders(filterHeaders(HEADERS_TO_IGNORE, echoServerRequest.getHeaderList())),
            request()
                .withMethod("POST")
                .withPath(calculatePath("/some/path"))
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("overrideHeaderToReplace", "originalValue")
                .withHeader("overrideHeaderToRemove", "originalValue")
                .withHeader("cookie", "overrideCookieToReplace=replacedValue; requestCookieToReplace=replacedValue")
                .withCookie("overrideCookieToReplace", "replacedValue")
                .withCookie("requestCookieToReplace", "replacedValue")
                .withKeepAlive(true)
                .withSecure(false)
                .withSocketAddress("localhost", insecureEchoServer.getPort(), SocketAddress.Scheme.HTTP)
                .withBody("some_overridden_body")
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("x-test", "test_headers_and_body")
                .withHeader("overrideHeaderToReplace", "originalValue")
                .withHeader("overrideHeaderToRemove", "originalValue")
                .withHeader("set-cookie", "overrideCookieToReplace=replacedValue", "requestCookieToReplace=replacedValue")
                .withHeader("cookie", "overrideCookieToReplace=replacedValue; requestCookieToReplace=replacedValue")
                .withCookie("overrideCookieToReplace", "replacedValue")
                .withCookie("requestCookieToReplace", "replacedValue")
                .withBody("some_overridden_body"),
            httpResponse
        );
    }

    @Test
    public void shouldForwardRequestInHTTP() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );

        // then
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    forward()
                        .withHost("127.0.0.1")
                        .withPort(insecureEchoServer.getPort())
                )
        ));
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_https"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldForwardRequestInHTTPS() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(secureEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
            );
        // then
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    forward()
                        .withHost("127.0.0.1")
                        .withPort(secureEchoServer.getPort())
                        .withScheme(HttpForward.Scheme.HTTPS)
                )
        ));

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_https"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardOverriddenRequest() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                        .withBody("some_overridden_body")
                ).withDelay(MILLISECONDS, 10)
            );
        Expectation[] upsertedSecureExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + secureEchoServer.getPort())
                        .withBody("some_overridden_body")
                ).withDelay(MILLISECONDS, 10)
            );

        // then
        // - in http
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
                .thenForward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                            .withBody("some_overridden_body")
                    ).withDelay(MILLISECONDS, 10)
                )
        ));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE

            )
        );
        // - in https
        assertThat(upsertedSecureExpectations.length, is(1));
        assertThat(upsertedSecureExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
                .thenForward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "localhost:" + secureEchoServer.getPort())
                            .withBody("some_overridden_body")
                    ).withDelay(MILLISECONDS, 10)
                )
        ));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body_https")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldForwardOverriddenRequestWithOverriddenResponse() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                        .withBody("some_overridden_body"),
                    response()
                        .withHeader("extra_header", "some_value")
                        .withHeader("content-length", "29")
                        .withBody("some_overridden_response_body")
                ).withDelay(MILLISECONDS, 10)
            );
        Expectation[] upsertedSecureExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + secureEchoServer.getPort())
                        .withBody("some_overridden_body"),
                    response()
                        .withHeader("extra_header", "some_value")
                        .withHeader("content-length", "29")
                        .withBody("some_overridden_response_body")
                ).withDelay(MILLISECONDS, 10)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body"),
                    header("extra_header", "some_value")
                )
                .withBody("some_overridden_response_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE

            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
                .thenForward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                            .withBody("some_overridden_body"),
                        response()
                            .withHeader("extra_header", "some_value")
                            .withHeader("content-length", "29")
                            .withBody("some_overridden_response_body")
                    ).withDelay(MILLISECONDS, 10)
                )
        ));
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body_https"),
                    header("extra_header", "some_value")
                )
                .withBody("some_overridden_response_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedSecureExpectations.length, is(1));
        assertThat(upsertedSecureExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
                .thenForward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "localhost:" + secureEchoServer.getPort())
                            .withBody("some_overridden_body"),
                        response()
                            .withHeader("extra_header", "some_value")
                            .withHeader("content-length", "29")
                            .withBody("some_overridden_response_body")
                    ).withDelay(MILLISECONDS, 10)
                )
        ));
    }

    @Test
    public void shouldForwardOverriddenRequestWithSocketAddress() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "incorrect_host:1234")
                        .withBody("some_overridden_body")
                        .withSocketAddress(
                            "localhost",
                            insecureEchoServer.getPort(),
                            SocketAddress.Scheme.HTTP
                        )
                ).withDelay(MILLISECONDS, 10)
            );
        Expectation[] upsertedSecureExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "incorrect_host:1234")
                        .withBody("some_overridden_body")
                        .withSocketAddress(
                            "localhost",
                            secureEchoServer.getPort(),
                            SocketAddress.Scheme.HTTPS
                        )
                ).withDelay(MILLISECONDS, 10)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE

            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
                .thenForward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "incorrect_host:1234")
                            .withBody("some_overridden_body")
                            .withSocketAddress(
                                "localhost",
                                insecureEchoServer.getPort(),
                                SocketAddress.Scheme.HTTP
                            )
                    ).withDelay(MILLISECONDS, 10)
                )
        ));
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body_https")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedSecureExpectations.length, is(1));
        assertThat(upsertedSecureExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
                .thenForward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "incorrect_host:1234")
                            .withBody("some_overridden_body")
                            .withSocketAddress(
                                "localhost",
                                secureEchoServer.getPort(),
                                SocketAddress.Scheme.HTTPS
                            )
                    ).withDelay(MILLISECONDS, 10)
                )
        ));
    }

    @Test
    public void shouldForwardTemplateInVelocity() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                template(HttpTemplate.TemplateType.VELOCITY,
                    "{" + NEW_LINE +
                        "    'path' : \"/somePath\"," + NEW_LINE +
                        "    'headers' : [ {" + NEW_LINE +
                        "        'name' : \"Host\"," + NEW_LINE +
                        "        'values' : [ \"127.0.0.1:" + insecureEchoServer.getPort() + "\" ]" + NEW_LINE +
                        "    }, {" + NEW_LINE +
                        "        'name' : \"x-test\"," + NEW_LINE +
                        "        'values' : [ \"$!request.headers['x-test'][0]\" ]" + NEW_LINE +
                        "    } ]," + NEW_LINE +
                        "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                        "}")
                    .withDelay(MILLISECONDS, 10)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("{'name': 'value'}"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    template(HttpTemplate.TemplateType.VELOCITY,
                        "{" + NEW_LINE +
                            "    'path' : \"/somePath\"," + NEW_LINE +
                            "    'headers' : [ {" + NEW_LINE +
                            "        'name' : \"Host\"," + NEW_LINE +
                            "        'values' : [ \"127.0.0.1:" + insecureEchoServer.getPort() + "\" ]" + NEW_LINE +
                            "    }, {" + NEW_LINE +
                            "        'name' : \"x-test\"," + NEW_LINE +
                            "        'values' : [ \"$!request.headers['x-test'][0]\" ]" + NEW_LINE +
                            "    } ]," + NEW_LINE +
                            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                            "}")
                        .withDelay(MILLISECONDS, 10)
                )
        ));
    }

    @Test
    public void shouldForwardTemplateInMustache() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                template(HttpTemplate.TemplateType.MUSTACHE,
                    "{" + NEW_LINE +
                        "    'path' : \"/somePath\"," + NEW_LINE +
                        "    'headers' : [ {" + NEW_LINE +
                        "        'name' : \"Host\"," + NEW_LINE +
                        "        'values' : [ \"127.0.0.1:" + insecureEchoServer.getPort() + "\" ]" + NEW_LINE +
                        "    }, {" + NEW_LINE +
                        "        'name' : \"x-test\"," + NEW_LINE +
                        "        'values' : [ \"{{ request.headers.x-test.0 }}\" ]" + NEW_LINE +
                        "    } ]," + NEW_LINE +
                        "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                        "}")
                    .withDelay(MILLISECONDS, 10)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("{'name': 'value'}"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    template(HttpTemplate.TemplateType.MUSTACHE,
                        "{" + NEW_LINE +
                            "    'path' : \"/somePath\"," + NEW_LINE +
                            "    'headers' : [ {" + NEW_LINE +
                            "        'name' : \"Host\"," + NEW_LINE +
                            "        'values' : [ \"127.0.0.1:" + insecureEchoServer.getPort() + "\" ]" + NEW_LINE +
                            "    }, {" + NEW_LINE +
                            "        'name' : \"x-test\"," + NEW_LINE +
                            "        'values' : [ \"{{ request.headers.x-test.0 }}\" ]" + NEW_LINE +
                            "    } ]," + NEW_LINE +
                            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                            "}")
                        .withDelay(MILLISECONDS, 10)
                )
        ));
    }

    @Test
    public void shouldAllowSimultaneousForwardAndResponseExpectations() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo")),
                once()
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                once()
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        // - forward
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body"),
                HEADERS_TO_IGNORE)
        );
        // - respond
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                HEADERS_TO_IGNORE)
        );
        // - no response or forward
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                HEADERS_TO_IGNORE)
        );
    }
}
