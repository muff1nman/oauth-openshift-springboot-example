% Securing Spring Boot with OpenShift and OAuth Part 2
% Andrew DeMaria
% 2018-06-04

Quickly securing an application has become quite simple with Spring Boot.
However, if you're running on OpenShift - it can be even easier by relying on
the builtin OAuth resource server that OpenShift provides. In this multipart blog,
we'll take a look at how this can be done.

1. Deploying an OAuth Spring Boot Example Application onto OpenShift
2. The Basic configuration for OAuth, Spring Boot and OpenShift
3. AUTOMATIC!! configuration for OAuth, Spring Boot and OpenShift
4. Authorities Extractor for OpenShift OAuth
5. Interacting with the OpenShift API using an OAuth Token

## Part 2 - The Basic configuration for OAuth, Spring Boot and OpenShift

### Maven and Spring Security

To begin covering the plumbing for what was shown in Part 1, we will begin with the maven `pom.xml`. As discussed in the
[Spring Boot OAuth Documentation][1], we will need the basics for a spring web app along with spring security and the
OAuth2 autoconfigure dependencies:

```
<project>
...

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.0.1.RELEASE</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security.oauth.boot</groupId>
            <artifactId>spring-security-oauth2-autoconfigure</artifactId>
            <version>2.0.1.RELEASE</version>
        </dependency>
        ...
    </dependencies>
    ...
</project>
```

### Configuring Spring Security OAuth

Next, we will want to enable OAuth integration with Spring Security. This can be done with the `@EnableOAuth2Sso` on
your Spring Boot entry-point class:

```
@SpringBootApplication
@EnableOAuth2Sso
public class Application extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
```

This annotation configures the `WebSecurityConfigurerAdapter` for OAuth authentication by adding a filter and
configuring an SSO entry point. With the annotation in place, we now need to configure the OAuth2 client. This is done
via Spring properties and can be shown below in an `application.yml`:

```
security:
  oauth2:
    client:
      clientId: REPLACE_ME
      clientSecret: REPLACE_ME
      accessTokenUri: https://openshift.default.svc/oauth/token
      userAuthorizationUri: REPLACE_ME
      tokenName: access_token
      authenticationScheme: header
      clientAuthenticationScheme: query
      scope: "user:info user:check-access"
    resource:
      userInfoUri: https://openshift.default.svc/oapi/v1/users/~
```

Most of the values here are the same between OpenShift clusters. For example, the `accessTokenUri` is used by the
application to retrieve an access token from OpenShift. Since the application will live in an OpenShift cluster, this
can use the `cluster.local` OpenShift search domain to access the API. However, there are several values that are
cluster specific and are denoted by `REPLACE_ME` above. The `userAuthorizationUri`
needs to reference the external OpenShift URL that users will see. An example of this value for minishift would be
`https://192.168.42.47:8443/oauth/authorize`. In addition, `clientId` and `clientSecret` are specific to your cluster.
These values allow your application to authenticate against the OpenShift API. For OpenShift we will need to do a bit of
preparation in order to get `clientId` and `clientSecret`.

#### Setting up a Service Account

For the client to interact with the OpenShift OAuth server, an identity is needed. This identity is setup via an
[OpenShift service account][2]. This service account is declared within the OpenShift template and is instantiated with
the other OpenShift objects when one processes the template as was done in Part 1. The service account declaration is as
follows:

```
- apiVersion: v1
  kind: ServiceAccount
  metadata:
    annotations:
      serviceaccounts.openshift.io/oauth-redirectreference.primary:
        '{"kind":"OAuthRedirectReference","apiVersion":"v1","reference":{"kind":"Route","name":"${APPLICATION_NAME}"}}'
    name: ${APPLICATION_NAME}
```

There is one interesting piece going on here and that is the annotation,
`serviceaccounts.openshift.io/oauth-redirectreference`. To explain this annotation, first let's take a look at
the simpler `serviceaccounts.openshift.io/oauth-redirecturi` parameter as documented on the
[Authentication Docs][3]. The `oauth-redirecturi` is
a static url and tells OpenShift the valid applications to redirect to. To compare this to
other OAuth providers, this would be equivalent to registering an OAuth Client via the Web UI, like is done for GitLab.
In OpenShift there is no Web UI to do this, and it is instead done via this annotation. However, in our template we do
not have a static URI to input here as the Route host is parametrized and can be generated by OpenShift in the case the
user leaves the `HOSTNAME_HTTP` parameter blank:

```
- apiVersion: v1
  id: ${APPLICATION_NAME}-https
  kind: Route
  ...
  spec:
    host: ${HOSTNAME_HTTP}
  ...
```

To address the dynamic host name, we will instead use the `oauth-redirectreference` parameter which allows us to
reference a Route via name and OpenShift will pull the host from the provided Route. With this service account in
place, we can use it for the `clientId` and `clientSecret` for our OAuth client. The `clientId` will be the fully
qualified name of the service account of the form `system:serviceaccounts:<project_name>:<service account name>`, i.e.
`system:serviceaccounts:oauth-example:spring-boot-oauth-app`.

For the `clientSecret`, the service account's secret token is used. One can retrieve this via the `oc serviceaccounts
get-token` command:

```
$ oc serviceaccounts get-token spring-boot-oauth-app
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3...
```

With these values, the `application.yml` file can be finalized:

```
security:
  oauth2:
    client:
      clientId: system:serviceaccounts:oauth-example:spring-boot-oauth-app
      clientSecret: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3...
      accessTokenUri: https://openshift.default.svc/oauth/token
      userAuthorizationUri: https://192.168.42.47:8443/oauth/authorize
      tokenName: access_token
      authenticationScheme: header
      clientAuthenticationScheme: query
      scope: "user:info user:check-access"
    resource:
      userInfoUri: https://openshift.default.svc/oapi/v1/users/~
```

The above is just an example and you will need to input your own values for `userAuthorizationUri`, `clientId` and
`clientSecret`.

#### OAuth `PrincipalExtractor`

The configuration is in place, but there is one remaining piece to add for the basics to function. Spring needs to know
information about the user to inject into the `OAuth2Authentication` object that the application can use for
things like displaying the users name. There are two interfaces that can be implemented to tell Spring how to get this
information. The first is the `PrincipalExtractor` and tells Spring how to get the Principal. In most cases, this is
just a simple string representing the user's name. The second is the
`AuthoritiesExtractor` which is an optional component which tells spring which authorities (I like to think of them
as groups) the user is associated to. We won't touch the `AuthoritiesExtractor` in this part. A basic implementation for
the `PrincipalExtractor` is shown below. This class will need to be copied into your own source code - make sure the
package name is scanned so that the bean gets created!

```
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.oauth2.resource.PrincipalExtractor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenShiftPrincipalExtractor implements PrincipalExtractor {

    private static final Logger logger = LoggerFactory.getLogger(OpenShiftPrincipalExtractor.class);

    @SuppressWarnings("unchecked")
    @Override
    public Object extractPrincipal(Map<String, Object> map) {
        try {
            return ((Map<String, Object>) map.get("metadata")).get("name");
        } catch (Exception e) {
            logger.warn("Could not read principal name", e);
            return null;
        }
    }
}
```

In the above, Spring passes in a map object which is parsed from the output of the `userInfoUri`:

```
$ curl -k -H "Authorization: Bearer $(oc whoami -t)" https://192.168.42.46:8443/oapi/v1/users/~
{
  "kind": "User",
  "apiVersion": "v1",
  "metadata": {
    "name": "developer",
    "selfLink": "/oapi/v1/users/developer",
    "uid": "eb15e4e7-5d60-11e8-80d2-525400601131",
    "resourceVersion": "797",
    "creationTimestamp": "2018-05-22T01:39:07Z"
  },
  "identities": [
    "anypassword:developer"
  ],
  "groups": []
}
```

The above implementation for the `PrincipalExtractor` traverses this object though `metadata.name` to get `developer` as
the Principal object.

To recount the parts here, we covered:

- Maven Dependencies for Spring Security and OAuth
- Enabling OAuth client via an annotation
- Configuring the OAuth client in the application properties
- Setting up an OpenShift service account with an OAuth Redirect URI annotation
- Adding a Spring bean to map from the OpenShift JSON object to a Spring Principal object.

In the next part, we'll take a look at automating the OAuth client configuration.

[1]: https://docs.spring.io/spring-security-oauth2-boot/docs/2.0.1.RELEASE/reference/html5/#maven
[2]: https://docs.openshift.com/container-platform/3.6/dev_guide/service_accounts.html
[3]: https://docs.openshift.com/container-platform/3.6/architecture/additional_concepts/authentication.html#redirect-uris-for-service-accounts
