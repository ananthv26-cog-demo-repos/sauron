package com.freenow.sauron.exception.handler;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.MESSAGE;

/**
 * Keeps {@code "message":""} in Boot's default error body, as Spring Boot 2.3 wrote it. Boot 2.5+ drops the key instead.
 */
@Component
public class BlankMessageErrorAttributes extends DefaultErrorAttributes
{
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options)
    {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options.including(MESSAGE));
        if (!options.isIncluded(MESSAGE))
        {
            attributes.put("message", "");
        }
        return attributes;
    }
}
