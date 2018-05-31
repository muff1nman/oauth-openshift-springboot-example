Securing Spring Boot with OpenShift and OAuth
======

Quickly securing an application has become quite simple with Spring Boot.
However, if you''re running on OpenShift - it can be even easier by relying on
the builtin OAuth resource server that OpenShift provides.

### Prerequisites
To follow along, you''ll need access to an OpenShift instance (here I''m using
minishift). Optionally, setup the `oc` tool to copy commands verbatim, however
all of the steps here can be done using the Web Console as well.

With OpenShift, we''ll create a new project:

```
oc new-project oauth-example
```

