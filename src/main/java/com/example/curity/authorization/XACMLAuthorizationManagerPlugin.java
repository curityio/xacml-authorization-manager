/*
 *  Copyright 2022 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.example.curity.authorization;

import com.example.curity.config.XACMLAuthorizationManagerPluginConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.attribute.ContextAttributes;
import se.curity.identityserver.sdk.attribute.SubjectAttributes;
import se.curity.identityserver.sdk.authorization.AuthorizationDecision;
import se.curity.identityserver.sdk.authorization.AuthorizationResult;
import se.curity.identityserver.sdk.authorization.GraphQLObligation;
import se.curity.identityserver.sdk.authorization.graphql.GraphQLAuthorizationActionAttributes;
import se.curity.identityserver.sdk.authorization.graphql.GraphQLAuthorizationManager;
import se.curity.identityserver.sdk.authorization.graphql.GraphQLAuthorizationResourceAttributes;
import se.curity.identityserver.sdk.http.HttpResponse;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.HttpClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static se.curity.identityserver.sdk.http.HttpRequest.fromString;

public final class XACMLAuthorizationManagerPlugin implements GraphQLAuthorizationManager
{
    private static final Logger _logger = LoggerFactory.getLogger(XACMLAuthorizationManagerPlugin.class);
    private final XACMLAuthorizationManagerPluginConfig _config;
    private final ExceptionFactory _exceptionFactory;
    private final HttpClient _pdpClient;

    public XACMLAuthorizationManagerPlugin(XACMLAuthorizationManagerPluginConfig config,
                                           ExceptionFactory exceptionFactory)
    {
        _config = config;
        _exceptionFactory = exceptionFactory;
        _pdpClient = config.getHttpClient();
    }

    @Override
    public AuthorizationResult<GraphQLObligation> getGraphQLAuthorizationResult(
            SubjectAttributes subjectAttributes,
            GraphQLAuthorizationActionAttributes graphQLAuthorizationActionAttributes,
            GraphQLAuthorizationResourceAttributes graphQLAuthorizationResourceAttributes,
            ContextAttributes contextAttributes) {

        String subject = subjectAttributes.getSubject();
        String group = subjectAttributes.get("groups").getValue().toString();
        String action = "POST"; //Hard-coded, graphQLAuthorizationActionAttributes does not yet contain the method
        String resource = graphQLAuthorizationResourceAttributes.get("resourceType").getValue().toString();

        String pdpRequestBody = XacmlHelper.getXacmlRequest(subject, group, action, resource);

        try {
            URI pdpURI = new URI(String.format("http://%s:%s%s", _config.getPDPHost(), _config.getPDPPort(), _config.getPDPPath()));
            HttpResponse pdpResponse = _pdpClient.request(pdpURI)
                    .contentType("application/xacml+json") //The payload in this example is JSON but could also be XML
                    .body(fromString(pdpRequestBody, StandardCharsets.UTF_8))
                    .post()
                    .response();

            if (pdpResponse.statusCode() != 200) {
                return AuthorizationResult.deny();
            }

            JSONObject pdpResponseBody = new JSONObject(pdpResponse.body(HttpResponse.asString()));

            String decision = pdpResponseBody.getJSONArray("Response").getJSONObject(0).getString("Decision");

            switch (decision) {
                case "Permit" -> {
                    boolean hasObligations = pdpResponseBody.getJSONArray("Response").getJSONObject(0).has("Obligations");

                    GraphQLObligation curityAuthzObligation = null;

                    if (hasObligations) {
                        JSONArray obligationAttributes = pdpResponseBody.getJSONArray("Response")
                                .getJSONObject(0)
                                .getJSONArray("Obligations");

                        curityAuthzObligation = new XacmlFilterObligation(obligationAttributes);

                    }
                    /* If there are no obligations in the XACML Response, create
                    a XacmlBeginObligation using the decision */
                    else {
                        curityAuthzObligation = new XacmlBeginObligation(decision);
                    }

                    return AuthorizationResult.allow(curityAuthzObligation);
                }
                case "Deny" -> {
                    _logger.debug("The PDP decision is Deny");
                    return AuthorizationResult.deny("The PDP decision is Deny");
                }
                case "NotApplicable" -> {
                    _logger.debug("The PDP decision is NotApplicable");
                    return AuthorizationResult.notApplicable();
                }
                case "Indeterminate" -> {
                    _logger.debug("The PDP decision is Indeterminate");
                    return AuthorizationResult.fromDecision(AuthorizationDecision.fromString(decision));
                }
                default -> {
                    _logger.debug("Unable to determine decision, returning Deny");
                    return AuthorizationResult.deny("Unable to determine decision, returning Deny");
                }
            }
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
            throw _exceptionFactory.externalServiceException("Unable to connect to PDP using configured URI.");
        } catch (HttpClient.HttpClientException e)
        {
            throw _exceptionFactory.externalServiceException("Unable to connect to PDP.Check the connection.");
        }
    }
}