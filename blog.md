Securing Spring Boot with OpenShift and OAuth
======

Quickly securing an application has become quite simple with Spring Boot.
However, if you''re running on OpenShift - it can be even easier by relying on
the builtin OAuth resource server that OpenShift provides.

### Prerequisites
To follow along, you''ll need access to an OpenShift instance (here I''m using
minishift). Optionally, setup the `oc` tool to copy commands verbatim, however
all of the steps here can be done using the Web Console as well.

With OpenShift, we''ll create a new project and add a template for our example app:

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

Then, you''ll need to import the image stream into your project:

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

In addition to importing the image, you''ll need to add the `IMAGE_STREAM_NAMESPACE` parameter to change
where to find the ImageStream.

Now to create our OpenShift app, run the following:

```
oc process
```

