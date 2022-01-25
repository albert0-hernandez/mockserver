package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.HttpRequestSerializer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator.jsonSchemaHttpRequestValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidatorIntegrationTest {

    private final JsonSchemaValidator jsonSchemaValidator = jsonSchemaHttpRequestValidator(new MockServerLogger());

    @Test
    public void shouldValidateValidCompleteRequestFromRawJson() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "  \"socketAddress\" : {" + NEW_LINE +
            "    \"host\" : \"someHost\"," + NEW_LINE +
            "    \"port\" : 1234," + NEW_LINE +
            "    \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithBinaryBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(binary("somebody".getBytes(UTF_8)))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithJsonBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(json("{" + NEW_LINE +
                    "    \"id\": 1," + NEW_LINE +
                    "    \"name\": \"A green door\"," + NEW_LINE +
                    "    \"price\": 12.50," + NEW_LINE +
                    "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                    "}"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithJsonSchemaBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"description\": \"The unique identifier for a product\"," + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"description\": \"Name of the product\"," + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 1," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithParameterBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(params(
                    param("paramOne", "paramOneValueOne"),
                    param("paramOne", "paramOneValueTwo"),
                    param("parameterRegexName.*", "parameterRegexValue.*")
                ))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithRegexBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(regex("[a-z]{1,3}"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithStringBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(exact("string_body"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithXPathBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(xpath("/bookstore/book[year=2005]/price"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithXMLBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(xml("" +
                    "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                    "<bookstore>" + NEW_LINE +
                    "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
                    "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
                    "    <author>Giada De Laurentiis</author>" + NEW_LINE +
                    "    <year>2005</year>" + NEW_LINE +
                    "    <price>30.00</price>" + NEW_LINE +
                    "  </book>" + NEW_LINE +
                    "</bookstore>"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithXMLSchemaBody() {
        // when
        assertThat(jsonSchemaValidator.isValid(new HttpRequestSerializer(new MockServerLogger()).serialize(
            request()
                .withBody(xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                    "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                    "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                    "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                    "    <xs:element name=\"notes\">" + NEW_LINE +
                    "        <xs:complexType>" + NEW_LINE +
                    "            <xs:sequence>" + NEW_LINE +
                    "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                    "                    <xs:complexType>" + NEW_LINE +
                    "                        <xs:sequence>" + NEW_LINE +
                    "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                        </xs:sequence>" + NEW_LINE +
                    "                    </xs:complexType>" + NEW_LINE +
                    "                </xs:element>" + NEW_LINE +
                    "            </xs:sequence>" + NEW_LINE +
                    "        </xs:complexType>" + NEW_LINE +
                    "    </xs:element>" + NEW_LINE +
                    "</xs:schema>"))
        )), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithExpandedHeaderParametersAndCookies() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteRequestWithCompactHeaderParametersAndCookies() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidShortHandJsonObjectBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"body\" : {\"foo\":\"bar\"}" + NEW_LINE +
                "  }"),
            is(""));
    }

    @Test
    public void shouldValidateValidShortHandJsonArrayBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"body\" : [{\"foo\":\"bar\"},{\"bar\":\"foo\"}]" + NEW_LINE +
                "  }"),
            is(""));
    }

    @Test
    public void shouldValidateInvalidBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"body\" : 1" + NEW_LINE +
                "  }"),
            is(
                "1 error:" + NEW_LINE +
                    " - $.body: should match one of its valid types: {" + NEW_LINE +
                    "     \"title\": \"request body matcher\"," + NEW_LINE +
                    "     \"anyOf\": [" + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"BINARY\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"base64Bytes\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"json\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"matchType\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"STRICT\"," + NEW_LINE +
                    "               \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": true" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"array\"," + NEW_LINE +
                    "         \"additionalProperties\": true" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON_SCHEMA\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"jsonSchema\": {" + NEW_LINE +
                    "             \"oneOf\": [" + NEW_LINE +
                    "               {" + NEW_LINE +
                    "                 \"type\": \"string\"" + NEW_LINE +
                    "               }," + NEW_LINE +
                    "               {" + NEW_LINE +
                    "                 \"$ref\": \"http://json-schema.org/draft-07/schema\"" + NEW_LINE +
                    "               }" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON_PATH\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"jsonPath\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"PARAMETERS\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"parameters\": {" + NEW_LINE +
                    "             \"$ref\": \"#/definitions/keyToMultiValue\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"REGEX\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"regex\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"STRING\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"string\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"subString\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"string\"" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XML\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xml\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XML_SCHEMA\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xmlSchema\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XPATH\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xpath\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }" + NEW_LINE +
                    "     ]" + NEW_LINE +
                    "   }" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateInvalidExtraField() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"invalidField\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"value\" : \"someBody\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }"),
            is(
                "1 error:" + NEW_LINE +
                    " - $.invalidField: is not defined in the schema and the schema does not allow additional properties" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateMultipleInvalidFieldTypes() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"method\" : 100," + NEW_LINE +
                "    \"path\" : false" + NEW_LINE +
                "  }"),
            is(
                "2 errors:" + NEW_LINE +
                    " - $.method: integer found, string expected" + NEW_LINE +
                    " - $.path: boolean found, string expected" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateInvalidListItemType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"headers\" : [ \"invalidValueOne\", \"invalidValueTwo\" ]" + NEW_LINE +
                "  }"),
            is("3 errors:" + NEW_LINE +
                " - $.headers: array found, object expected" + NEW_LINE +
                " - $.headers[0]: string found, object expected" + NEW_LINE +
                " - $.headers[1]: string found, object expected" + NEW_LINE +
                NEW_LINE +
                OPEN_API_SPECIFICATION_URL));
    }

}
