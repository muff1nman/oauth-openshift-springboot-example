package com.andrewdemaria.openshift.oauth.springexample.controller;

import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IProject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rest")
public class ListProjectsController {

    @RequestMapping(method = RequestMethod.GET)
    public Object getProjects() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof OAuth2Authentication)) {
            throw new RuntimeException("Unexpected authentication type: " + authentication.getClass().getCanonicalName());
        }
        OAuth2Authentication oAuth2 = ((OAuth2Authentication) authentication);
        OAuth2AuthenticationDetails details = (OAuth2AuthenticationDetails) oAuth2.getDetails();
        System.out.println("Token is: " + details.getTokenValue());
        IClient client = new ClientBuilder("https://openshift.default.svc")
                .usingToken(details.getTokenValue())
                .build();
        List<IProject> list = client.list(ResourceKind.PROJECT);
        return list.stream().map(IProject::getName).collect(Collectors.toList());
    }
}
