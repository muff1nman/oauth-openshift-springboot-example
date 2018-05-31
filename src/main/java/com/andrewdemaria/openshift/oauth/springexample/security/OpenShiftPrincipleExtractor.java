package com.andrewdemaria.openshift.oauth.springexample.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.oauth2.resource.PrincipalExtractor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenShiftPrincipleExtractor implements PrincipalExtractor {

    private static final Logger logger = LoggerFactory.getLogger(OpenShiftPrincipleExtractor.class);

    @SuppressWarnings("unchecked")
    @Override
    public Object extractPrincipal(Map<String, Object> map) {
        try {
            return ((Map<String, Object>) map.get("metadata")).get("name");
        } catch (Exception e) {
            logger.warn("Could not read principle name", e);
            return null;
        }
    }
}
