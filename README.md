# XACML Authorization Manager Plugin

[![Quality](https://img.shields.io/badge/quality-demo-red)](https://curity.io/resources/code-examples/status/)
[![Availability](https://img.shields.io/badge/availability-source-blue)](https://curity.io/resources/code-examples/status/)

A prototype Authorization Manager written in Java using an external [XACML (eXtensible Access Control Markup Language)](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=xacml) authorization engine also known as a [Policy Decision Point (PDP)](https://curity.io/resources/learn/entitlement-management-system/#the-policy-decision-point) for authorization.

**Note**: The plugin requires at least version 7.3 of the Curity Identity Server.

## Introduction

The Curity Identity Server can leverage Authorization Managers to control access to exposed GraphQL APIs for DCR and User Management. Authorization Managers can be custom built using the [Curity Java Plugin SDK](https://curity.io/docs/idsvr-java-plugin-sdk/latest/). This is an example of a custom Authorization manager that acts as a [Policy Enforcement Point (PEP)](https://curity.io/resources/learn/entitlement-management-system/#the-policy-enforcement-point) in a XACML architecture. The XACML Authorization Manager sends a JSON formatted request to a configured PDP that holds a policy. The PDP responds with a decision or optionally with obligations. The XACML Authorization Manager handles the response and allows/denies access to the requested resource. The XACML Authorization Manager can also filter data and based on the policy in use by the PDP can allow access to the resource but deny access to specific fields within that resource.

## Building the Plugin

Build the plugin by issuing the command `mvn package`. This will produce a JAR file in the `target/xacml-authorization-manager` directory, which can be installed.

## Installing the Plugin

To install the plugin, copy the compiled JAR (and all of its dependencies) from `target/xacml-authorization-manager` into `${IDSVR_HOME}/usr/share/plugins/${pluginGroup}` on each node, including the admin node.

For more information about installing plugins, refer to the [Plugin Installation section of the Documentation](https://curity.io/docs/idsvr/latest/developer-guide/plugins/index.html#plugin-installation).

## Required Dependencies

For a list of the dependencies and their versions, run `mvn dependency:list`. Ensure that all of these are installed in the plugin group; otherwise, they will not be accessible to this plug-in and run-time errors will result.

## Configuring the Plugin

The plugin needs an HttpClient, which currently has to be set using the Curity Identity Server CLI. First create an Http client, e.g. xacml-http-client, then execute the following command to create the XACML Authorization Manager and set the HttpClient.

**NOTE:** This creates a XACML Authorization Manager with the ID `my-xacml-authz-manager`.

```sh
set processing authorization-managers authorization-manager my-xacml-authz-manager xacml-authorization-manager http-client id xacml-http-client
```

### The configuration parameters
| Name | Type | Description | Example | Default |
|------|------|-------------|---------|---------|
| `HttpClient`| String | The ID of the HttpClient that the Authorization Manager will use to call the XACML PDP. | `xacml-http-client` |  |
| `PDP Host`  | String | The hostname of the XACML PDP. | `xacml-pdp.example.com` | `localhost` |
| `PDP Port`  | String | The port that the XACML PDP is exposing its service on.  | `8443` | `8080` |
| `PDP Path`  | String | The path of the XACML PDP that accepts authorization requests. | `/pdp` |  `/services/pdp` |

When committed, the Authorization Manager is avialble to be used throughout the Curoity Identity Server.

### DCR GraphQL API

In order to protect the DCR GraphQL API the Authorization Manager needs to be added to the Token Service Profile. Navigate to `Token Service` -> `General`, in the drop-down for Authorization Manager, choose the newly created Authorization Manager (`my-xacml-authz-manager` in the example above).

### User Management GraphQL API

In order to protect the User Management GraphQL API the Authorization Manager needs to be added to the User Management Profile. Navigate to `User Management` -> `General`, in the drop-down for Authorization Manager, choose the newly created Authorization Manager (`my-xacml-authz-manager` in the example above).

## Testing

The repository contains a docker compose file that will run an instance of the Curity Identity Server, a data source with test data and an [AuthzForce XACML PDP](https://github.com/authzforce/restful-pdp). Running this environment will provide a fully configured environment that can be used to test the use cases and the plugin.

A scipt is available that will build and deploy the XACML Authorization Manager Plugin and start the docker containers. Run `/deploy.sh` to get everything up and running. Run `./teardown.sh` to stop and remove all the containers.

**NOTE** DEBUG logging is enabled for the Curity Identity Server and the XACML PDP.

1. Using [OAuth.tools](https://oauth.tools/), initiate a code flow using the `xacml-demo` client (secret is `Password1`).
2. Log in with a user, `admin` or `demouser` (by default both have the password `Password1`). The `admin` user belongs to the group `admin` that has full access to the GraphQL APIs. The `demouser` belongs to the `devops` group that is subject to filtration of certain fields for both DCR and User Management data. This should be clear when reviewing the policy used by the XACML PDP. Note that the group claim is issued by default per the configuration.
3. The JWT that is obtained from running the code flow can be used in a call to either of the GraphQL APIs. Using for example Postman or GraphiQL, construct a query and add the JWT in the `Authorize` header.

### Example User Query
```json
query getAccounts
{
   accounts(first: 5) {
    edges {
      node {
        id
        name {
          givenName
          middleName
          familyName
        }
        title
        active
        emails {
          value
          primary
        }
        phoneNumbers {
          value
          primary
        }
      }
    }
  }
}
```

### XACML Policies
The policies are written in [ALFA](https://en.wikipedia.org/wiki/ALFA_(XACML)) and available in xacml-pdp/alfa. They are compiled into XACML artifacts using a [Visual Studio Code Plugin](https://axiomatics.github.io/alfa-vscode-doc/). The compiled representation of the policies are available in xacml-pdp/pdp/conf/policies/ and loaded by the PDP at startup.

**NOTE**: The ALFA representation of the policies are much easier to read than the XACML artifacts.

#### DCR Policies

DCR Policies are defined and grouped by the `graphQLDCR` policyset where the PDP will check that `resourceType == "dcr"`. 

If the user has `group == "admin"`, access is permitted to all different possible actions (GET, POST, etc.). If the user instead belongs to the `devops` group, access is only permitted for a `POST` request. Access is also limited by obligations that are returned as part of the response from the PDP for the `POST` action. 

#### User Management Policies

Access to the User Management API is defined in the `graphQLUM` policyset. The structure is very similar to the `graphQLDCR` policyset. Here, the `phoneNumbers` and `name` fields are filtered for users in the `devops` group.

#### Sample Request/Response

To test the PDP alone without the involvement of the Authorization Manager a sample request can be sent using for example Postman.

```json
POST /services/pdp HTTP/1.1
Host: localhost:8080
Content-Type: application/xacml+json
{"Request":
    {"Category":[{
        "CategoryId":"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject",
            "Attribute":[{
                "AttributeId":"subject-id",
                "Value":"alice"
            },
            {
                "AttributeId":"group",
                "Value":"devops"
            }]
        },
        {
        "CategoryId":"urn:oasis:names:tc:xacml:3.0:attribute-category:action",
            "Attribute":[{
                "AttributeId":"apiAction",
                "Value":"POST"
            }]
        },
        {
        "CategoryId":"urn:oasis:names:tc:xacml:3.0:attribute-category:resource",
            "Attribute":[{
                "AttributeId":"resourceType",
                "Value":"user-management"
            }]
        }]
    }
}
```

This should return the response below that includes an obligation.

```json
{
    "Response": [
        {
            "Decision": "Permit",
            "Obligations": [
                {
                    "AttributeAssignment": [
                        {
                            "Value": "true",
                            "DataType": "http://www.w3.org/2001/XMLSchema#boolean",
                            "Category": "urn:oasis:names:tc:xacml:3.0:attribute-category:resource",
                            "AttributeId": "phoneNumbers"
                        },
                        {
                            "Value": "true",
                            "DataType": "http://www.w3.org/2001/XMLSchema#boolean",
                            "Category": "urn:oasis:names:tc:xacml:3.0:attribute-category:resource",
                            "AttributeId": "name"
                        }
                    ],
                    "Id": "curity-filters"
                }
            ]
        }
    ]
}
```

## More Information

- Please visit [curity.io](https://curity.io/) for more information about the Curity Identity Server
- [OASIS eXtensible Access Control Markup Language (XACML) TC](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=xacml)
- More details on [Abbreviated Language For Authorization (ALFA)](https://en.wikipedia.org/wiki/ALFA_(XACML))
- [Curity Identity Server GraphQL APIs](https://curity.io/docs/idsvr/latest/developer-guide/graphql/index.html)

Copyright (C) 2022 Curity AB.
