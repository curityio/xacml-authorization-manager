package com.example.curity.authorization;

import se.curity.identityserver.sdk.authorization.GraphQLObligation;
import se.curity.identityserver.sdk.authorization.ObligationDecisionResult;

class XacmlBeginObligation implements GraphQLObligation.BeginOperation {

    private boolean _operationAllowed = false;
    private ObligationDecisionResult _decisionResult;

    public XacmlBeginObligation(String decision)
    {
        switch (decision)
        {
//            case "Permit": _operationAllowed = true;
            case "Permit": _decisionResult = ObligationDecisionResult.allow();
        }
    }

    @Override
    public ObligationDecisionResult canPerformOperation(Input input) {
        return _decisionResult;
    }
}
