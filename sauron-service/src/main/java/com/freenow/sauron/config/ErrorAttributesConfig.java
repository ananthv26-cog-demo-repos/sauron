package com.freenow.sauron.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.WebRequest;

/**
 * Keeps the default error body shape of Spring Boot 2.3: {@code message} is present and blank when
 * messages are not included. Boot 2.5+ drops the key instead.
 */
@Configuration
public class ErrorAttributesConfig
{
    @Bean
    public ErrorAttributes errorAttributes()
    {
        return new BlankMessageErrorAttributes();
    }


    static class BlankMessageErrorAttributes extends DefaultErrorAttributes
    {
        @Override
        public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options)
        {
            Map<String, Object> withMessage = new LinkedHashMap<>();
            super.getErrorAttributes(webRequest, options).forEach((key, value) -> {
                if ("path".equals(key))
                {
                    withMessage.putIfAbsent("message", "");
                }
                withMessage.put(key, value);
            });
            withMessage.putIfAbsent("message", "");
            return withMessage;
        }
    }
}
