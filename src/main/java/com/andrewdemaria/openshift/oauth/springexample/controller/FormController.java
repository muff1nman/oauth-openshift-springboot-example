package com.andrewdemaria.openshift.oauth.springexample.controller;

import com.andrewdemaria.openshift.oauth.springexample.domain.FormRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;

@Controller
@RequestMapping("/")
public class FormController {

    private static final Logger logger = LoggerFactory.getLogger(FormController.class);

    @RequestMapping(method = RequestMethod.GET)
    public String getForm(Model model) {
        FormRequest formRequest = new FormRequest();
        model.addAttribute("formRequest", formRequest);
        return "form";
    }

    @RequestMapping(method = RequestMethod.POST)
    public String formSubmit(@Valid @ModelAttribute FormRequest formRequest, BindingResult bindingResult) {

        if(bindingResult.hasErrors()) {
            return "form";
        }

        logger.info("Form received: {}", formRequest);

        return "result";
    }
}
