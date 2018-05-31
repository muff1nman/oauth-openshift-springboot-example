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
