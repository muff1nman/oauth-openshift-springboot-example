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

## Part 3 - AUTOMATIC!! configuration for OAuth, Spring Boot and OpenShift

Remember that in Part 2, we had to manually configure a couple of properties in our `application.yml`:

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
      scope: "user:info user:check-access user:list-projects"
    resource:
      userInfoUri: https://openshift.default.svc/oapi/v1/users/~
```

### Adding a `run.sh` script

Instead of configuring the `REPLACE_ME` items manually, we can inject these properties into our application at runtime.
Now do note that in order to do this we are relying on functionality within the
OpenShift OpenJDK S2I image: `redhat-openjdk-18/openjdk18-openshift`. Namely, the fact that we can supply a `run.sh`
script that the image will run instead of its normal script, `run-java.sh`. This can be seen by taking a look at the
image contents via `oc run`:

```
 oc run -it --restart=Never javatest --image=redhat-openjdk-18/openjdk18-openshift --command -- /bin/bash
If you don't see a command prompt, try pressing enter.
[jboss@javatest ~]$
```

Since this is an [S2I][1] image, we can examine the `run` script:

```
[jboss@javatest ~]$ cd /usr/local/s2i/
[jboss@javatest s2i]$ cat run
#!/bin/bash

# Command line arguments given to this script
args="$*"

# Global S2I variable setup
source `dirname "$0"`/s2i-setup

# Always include jolokia-opts, which can be empty if switched off via env
export JAVA_OPTIONS="${JAVA_OPTIONS:+${JAVA_OPTIONS} }$(/opt/jolokia/jolokia-opts) $(source /opt/hawkular/hawkular-opts && get_hawkular_opts)"

if [ -f "${DEPLOYMENTS_DIR}/bin/run.sh" ]; then
    echo "Starting the application using the bundled ${DEPLOYMENTS_DIR}/bin/run.sh ..."
    exec ${DEPLOYMENTS_DIR}/bin/run.sh $args ${JAVA_ARGS}
else
    echo "Starting the Java application using /opt/run-java/run-java.sh ..."
    exec /opt/run-java/run-java.sh $args ${JAVA_ARGS}
fi
```

Looking at the final `if` statement, we can see that if we send a `run.sh` script within a `bin` directory, it will be
run. How can we do this? First, we need to ensure that this script is copied over during the assemble process. I'd
encourage taking a detour and reading the `assemble` script within the S2I image to see that we can specify
`ARTIFACT_COPY_ARGS` to customize what gets copied out of the build process. We will specify this within our
`BuildConfig` as follows:

```
- apiVersion: v1
  kind: BuildConfig
  ...
  spec:
    ...
    strategy:
      sourceStrategy:
        env:
          - name: ARTIFACT_COPY_ARGS
            value: "-r *.jar bin"
        forcePull: true
        from:
          kind: ImageStreamTag
          name: redhat-openjdk18-openshift:1.2
          namespace: ${IMAGE_STREAM_NAMESPACE}
      type: Source
    ...
```

So we've added the `-r` option to recursively copy directories and also added our `bin` directory. However, note that
the copy command is run relative to the ephemeral `target` directory that maven creates. So, we will need to copy our
`bin` directory to the `target` directory as part of the maven build process. We will utilize two plugins to do this for
us as seen in the `pom.xml`:

```
    <build>
        ...
        <plugins>
            ...
            <plugin>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <id>copy-resources</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${basedir}/target/bin</outputDirectory>
                            <resources>
                                <resource>
                                    <directory>bin</directory>
                                    <filtering>false</filtering>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>1.6</version>
                <executions>
                    <execution>
                        <id>process-classes</id>
                        <phase>process-classes</phase>
                        <configuration>
                            <target>
                                <chmod file="target/bin/run.sh" perm="755"/>
                            </target>
                        </configuration>
                        <goals>
                            <goal>run</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

The first plugin is the `maven-resources-plugin` which we setup to copy the `bin` directory into the build directory.
The second plugin is the `maven-antrun-plugin` which ensures the `run.sh` script is executable. With these plugins in
place, we can get to creating our `run.sh` script.

#### Trusting the OpenShift CA Certificate

Since our application will be interacting with the OpenShift OAuth API, it needs to trust the OpenShift CA certificate
to communicate properly over SSL. The OpenShift CA certificate gets [mounted][2] into our running container but is not
trusted by default. To trust this certificate, it can be imported into a keystore for our application:

```
#!/bin/bash

rm -f cacerts
cp /etc/pki/java/cacerts cacerts
chmod u+w cacerts
keytool -import -trustcacerts -alias ocp -file /run/secrets/kubernetes.io/serviceaccount/ca.crt -keystore cacerts -storepass changeit -noprompt

export JAVA_OPTIONS="-Djavax.net.ssl.trustStore=$(readlink -f cacerts) $JAVA_OPTIONS"
```

In the above, the default cacert is copied to a new file that the runtime user can modify. Then the
[Java `keytool` command][3] is used to add the CA cert at `/run/secrets/kubernetes.io/serviceaccount/ca.crt` as a
trusted certificate. With this `cacert` file ready to go, we use it as the `javax.net.ssl.trustStore` to override the
default location.

#### OAuth Authorization URI

Now that our application can securely interact with the OAuth API, we can move on to setting up the User Authorization
URI. Remember in Part 2 that this was the public facing URI for the `userAuthorizationUri` property. This value can be
discovered by the application through the `https://openshift.default.svc/.well-known/oauth-authorization-server`
endpoint which returns something like this:

```
$ curl -k https://192.168.42.46:8443/.well-known/oauth-authorization-server
{
  "issuer": "https://192.168.42.46:8443",
  "authorization_endpoint": "https://192.168.42.46:8443/oauth/authorize",
  "token_endpoint": "https://192.168.42.46:8443/oauth/token",
  "scopes_supported": [
    "user:check-access",
    "user:full",
    "user:info",
    "user:list-projects",
    "user:list-scoped-projects"
  ],
  "response_types_supported": [
    "code",
    "token"
  ],
  "grant_types_supported": [
    "authorization_code",
    "implicit"
  ],
  "code_challenge_methods_supported": [
    "plain",
    "S256"
  ]
}
```

In the above, I'm using the external OpenShift API endpoint - but the internal `openshift.default.svc` returns the same
exact content. The value we're interested in is the `authorization_endpoint` value. We can pull this out via a bit of
scripting and use it to override the value in the `application.yml` file
(remember the [ordering of Spring Boot property sources][4]):

```
authUri=$(curl -s -k \
    https://openshift.default.svc/.well-known/oauth-authorization-server \
    | python -c \
    'import json,sys;obj=json.load(sys.stdin);print obj["authorization_endpoint"]')
export JAVA_OPTIONS="-Dsecurity.oauth2.client.userAuthorizationUri=${authUri} $JAVA_OPTIONS"
```

#### Setting up `clientId` and `clientSecret`

The other two properties we did manually in Part 2 were the `clientId` and the `clientSecret` which came from the
Service Account. For the `clientId`, we can add the necessary bits as environment variables to the DeploymentConfig.
For the namespace value, we can utilize the [downward API][5] to pull it from `metadata.namespace`:

```
containers:
- env:
  - name: NAMESPACE
    valueFrom:
      fieldRef:
        fieldPath: metadata.namespace
  - name: SERVICE_ACCOUNT_NAME
    value: ${APPLICATION_NAME}
```

With these environment variables, we can construct the `clientId` property in our `run.sh` script:

```
export JAVA_OPTIONS="-Dsecurity.oauth2.client.clientId=system:serviceaccount:${NAMESPACE}:${SERVICE_ACCOUNT_NAME} $JAVA_OPTIONS"
```

Next, we need the Service Account secret for the `clientSecret` property. To access this value within the container, we
can change the Service Account the pod will run with and then OpenShift will mount the secret at
`/run/secrets/kubernetes.io/serviceaccount/token` during runtime. Changing the Service Account is done in the
DeploymentConfig in the following snippet where we utilize the fact that the Service Account we created in Part 2 has
the same name as the `APPLICATION_NAME` parameter.

```
- apiVersion: v1
  kind: DeploymentConfig
  spec:
    ...
    template:
      ...
      spec:
        serviceAccountName: ${APPLICATION_NAME}
        ...
```


With the `serviceAccountName`, we tell OpenShift to run the container with our specific Service Account, rather than the
default one. Now within our `run.sh` script, we can setup the `clientSecret` property:

```
export JAVA_OPTIONS="-Dsecurity.oauth2.client.clientSecret=$(cat /run/secrets/kubernetes.io/serviceaccount/token) $JAVA_OPTIONS"
```

Putting all of this together, the `run.sh` script now looks like:

```
#!/bin/bash

rm -f cacerts
cp /etc/pki/java/cacerts cacerts
chmod u+w cacerts
keytool -import -trustcacerts -alias ocp -file /run/secrets/kubernetes.io/serviceaccount/ca.crt -keystore cacerts -storepass changeit -noprompt

export JAVA_OPTIONS="-Djavax.net.ssl.trustStore=$(readlink -f cacerts) $JAVA_OPTIONS"
export JAVA_OPTIONS="-Dsecurity.oauth2.client.clientId=system:serviceaccount:${NAMESPACE}:${SERVICE_ACCOUNT_NAME} $JAVA_OPTIONS"
export JAVA_OPTIONS="-Dsecurity.oauth2.client.clientSecret=$(cat /run/secrets/kubernetes.io/serviceaccount/token) $JAVA_OPTIONS"
authUri=$(curl -s -k \
    https://openshift.default.svc/.well-known/oauth-authorization-server \
    | python -c \
    'import json,sys;obj=json.load(sys.stdin);print obj["authorization_endpoint"]')
export JAVA_OPTIONS="-Dsecurity.oauth2.client.userAuthorizationUri=${authUri} $JAVA_OPTIONS"

exec /opt/run-java/run-java.sh $*
```

Notice that at the end, we call the same script that is executed by default if we had provided our own `run.sh` script.
In addition, we are utilizing the functionality of the `run-java.sh` script to pull in our changes to the `JAVA_OPTIONS`
environment variable.

With this Part, we have improved upon the configuration done in Part 2 to make our OpenShift OAuth configuration
portable and automatic. In Part 4, we'll take a look at adding an OpenShift Authorities Extractor.

[1]: https://docs.openshift.com/container-platform/3.6/creating_images/s2i.html#s2i-scripts
[2]: https://kubernetes.io/docs/tasks/tls/managing-tls-in-a-cluster/
[3]: https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html
[4]: https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html
[5]: https://docs.openshift.com/container-platform/3.6/dev_guide/downward_api.html
