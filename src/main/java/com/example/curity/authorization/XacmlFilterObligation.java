package com.example.curity.authorization;

import org.json.JSONArray;
import org.json.JSONObject;
import se.curity.identityserver.sdk.attribute.scim.v2.ResourceAttributes;
import se.curity.identityserver.sdk.authorization.GraphQLObligation;
import se.curity.identityserver.sdk.authorization.ObligationAlterationResult;

import java.util.HashSet;
import java.util.Set;

class XacmlFilterObligation implements GraphQLObligation.CanReadAttributes {

    Set _attributesToFilter = new HashSet<>();
    public XacmlFilterObligation(JSONArray returnedObligations)
    {
        for (Object obj: returnedObligations.getJSONObject(0).getJSONArray("AttributeAssignment"))
        {
            JSONObject obligation = (JSONObject) obj;
            String fieldToFilter = obligation.getString("AttributeId");
            Boolean filter = obligation.getBoolean("Value");

            if(!filter) //if the obligation is false access is not allowed to the field and should be filtered from the response
            {
                _attributesToFilter.add(fieldToFilter);
            }
        }
    }

    @Override
    public ObligationAlterationResult<ResourceAttributes<?>> filterReadAttributes(Input input) {
        ResourceAttributes returnAttributes = input.getResourceAttributes();

        for (Object s : _attributesToFilter)
        {
            returnAttributes = returnAttributes.removeAttribute(s.toString());
        }

        return ObligationAlterationResult.of(returnAttributes);
    }
}
