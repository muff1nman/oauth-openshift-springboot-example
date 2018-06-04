Securing Spring Boot with OpenShift and OAuth
======

Quickly securing an application has become quite simple with Spring Boot.
However, if you're running on OpenShift - it can be even easier by relying on
the builtin OAuth resource server that OpenShift provides. In this multipart blog,
we'll take a look at how this can be done.

1. Deploying an OAuth Spring Boot Example Application onto OpenShift
2. The Basic configuration for OAuth, Spring Boot and OpenShift
3. AUTOMATIC!! configuration for OAuth, Spring Boot and OpenShift
4. Principle Extractors for OpenShift OAuth
5. Interacting with the OpenShift API using an OAuth Token

## Part 1 - Deploying an OAuth Spring Boot Example Application onto OpenShift

### Prerequisites
To follow along, you'll need access to an OpenShift instance (here I'm using
minishift and OpenShift 3.6). Optionally, setup the `oc` tool to copy commands verbatim, however
all of the steps here can be done using the Web Console as well.

### Deploying the Example Project

With OpenShift, we'll create a new project and add a template for our example app:

```
$ oc new-project oauth-example
$ oc apply -f https://raw.githubusercontent.com/muff1nman/oauth-openshift-springboot-example/master/openshift/template.yml
```

Now we need to instantiate our example application with the imported template. Before doing so, check that the required
OpenJDK image is available:

```
$ oc get imagestreamtag redhat-openjdk18-openshift:1.2 -n openshift -o name
imagestreamtags/redhat-openjdk18-openshift:1.2
```

The above output is good to go and you can jump to the template processing step, however if you instead get:

```
Error from server (NotFound): imagestreamtags.image.openshift.io "redhat-openjdk18-openshift:1.2" not found
```

Then, you'll need to import the image stream into your project:

```
$ oc import-image \
  --from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift:1.2 \
  redhat-openjdk18-openshift:1.2 \
  --confirm
The import completed successfully.

Name:			redhat-openjdk18-openshift
Namespace:		oauth-example
Created:		1 second ago
...
```

In addition to importing the image, you'll need to add the `IMAGE_STREAM_NAMESPACE` parameter to change
where to find the ImageStream.

Now to create our OpenShift app, run the following:

```
oc process openjdk18-oauth-springboot-s2i | oc apply -f -
```

If you imported an image stream, use this:

```
oc process openjdk18-oauth-springboot-s2i -p IMAGE_STREAM_NAMESPACE=oauth-example | oc apply -f -
```

After the above, a new build and then a new deployment will start. Monitor and wait for both to finish:

```
$ oc status
In project oauth-example on server https://192.168.42.46:8443

https://spring-boot-oauth-app-oauth-example.192.168.42.46.nip.io (redirects) (svc/spring-boot-oauth-app)
  dc/spring-boot-oauth-app deploys istag/spring-boot-oauth-app:latest <-
    bc/spring-boot-oauth-app source builds https://github.com/muff1nman/oauth-openshift-springboot-example.git on openshift/redhat-openjdk18-openshift:1.2
      build #1 running for 12 seconds
    deployment #1 waiting on image or update

View details with `oc describe <resource>/<name>` or list everything with `oc get all`.
...
$ oc get dc
NAME                    REVISION   DESIRED   CURRENT   TRIGGERED BY
spring-boot-oauth-app   1          1         1         config,image(spring-boot-oauth-app:latest)
```

### Trying out OAuth

With the app deployed, now we can take a look at our handiwork. Get the route that was created, and visit it in your
favorite web browser:

```
$ oc get route spring-boot-oauth-app -o custom-columns=URL:spec.host
URL
spring-boot-oauth-app-oauth-example.192.168.42.46.nip.io
```

Note that it redirected us to the OpenShift login page:

[[images/oauth-prompt-1.png]]

After filling out your password, we get another page telling us about what this application is requesting some
permissions. These permissions are also called scopes:

[[images/oauth-prompt-2.png]]

If these permissions are accepted, then the user is redirected to the login page and is successfully authenticated:

[[images/oauth-form.png]]

Stay tuned for the next part where we will take a look at the underlying configuration. If you're interested in more
about how OAuth works, I like the graphical document [1](here). For documentation on OAuth and OpenShift see the [1]
(OpenShift Docs).

1. https://docs.openshift.com/container-platform/3.6/architecture/additional_concepts/authentication.html#oauth
2. https://docs.oracle.com/cd/E50612_01/doc.11122/oauth_guide/content/oauth_flows.html
