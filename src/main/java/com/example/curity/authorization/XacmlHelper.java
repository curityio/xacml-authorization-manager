package com.example.curity.authorization;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

public class XacmlHelper {

    private static String SUBJECT = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private static String ACTION = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private static String RESOURCE = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource";
//    private static String ENVIRONMENT = "urn:oasis:names:tc:xacml:3.0:attribute-category:environment";

    public static String getXacmlRequest(String subject, String group, String action, String resource){

        JsonArray subjectAttributes = Json.createArrayBuilder()
                .add(createAttribute("subject-id", subject))
                .add(createAttribute("group", group))
                .build();

        JsonArray actionAttributes = Json.createArrayBuilder()
                .add(createAttribute("apiAction", action))
                .build();

        JsonArray resourceAttributes = Json.createArrayBuilder()
                .add(createAttribute("resourceType", resource))
                .build();

        String request = Json.createObjectBuilder()
                .add("Request",
                        Json.createObjectBuilder()
                                .add("Category", Json.createArrayBuilder()
                                        .add(createCategory(SUBJECT, subjectAttributes))
                                        .add(createCategory(ACTION, actionAttributes))
                                        .add(createCategory(RESOURCE, resourceAttributes))
                                )
                )
                .build()
                .toString();

        return request;
    }

    private static JsonObject createAttribute(String attributeId, String attributeValue)
    {
        return Json.createObjectBuilder()
                .add("AttributeId", attributeId)
                .add("Value", attributeValue)
                .build();
    }

    private static JsonObject createCategory(String categoryId, JsonArray attributes)
    {
        return  Json.createObjectBuilder()
                .add("CategoryId", categoryId)
                .add("Attribute", attributes)
                .build();
    }
}
