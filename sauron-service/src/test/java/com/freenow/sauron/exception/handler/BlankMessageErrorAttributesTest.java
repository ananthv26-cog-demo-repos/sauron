package com.freenow.sauron.exception.handler;

import java.util.Map;
import org.junit.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.MESSAGE;

public class BlankMessageErrorAttributesTest
{
    private final BlankMessageErrorAttributes errorAttributes = new BlankMessageErrorAttributes();


    @Test
    public void defaultOptionsKeepABlankMessageBetweenErrorAndPath()
    {
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(request(), ErrorAttributeOptions.defaults());

        assertThat(attributes).containsOnlyKeys("timestamp", "status", "error", "message", "path");
        assertThat(attributes.keySet()).containsExactly("timestamp", "status", "error", "message", "path");
        assertThat(attributes.get("message")).isEqualTo("");
        assertThat(attributes.get("path")).isEqualTo("/nope");
    }


    @Test
    public void includeMessageStillShowsTheRealMessage()
    {
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(request(), ErrorAttributeOptions.defaults().including(MESSAGE));

        assertThat(attributes.keySet()).containsExactly("timestamp", "status", "error", "message", "path");
        assertThat(attributes.get("message")).isEqualTo("No message available");
    }


    private static ServletWebRequest request()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nope");
        request.setAttribute(errorAttribute("request_uri"), "/nope");
        request.setAttribute(errorAttribute("status_code"), 404);
        return new ServletWebRequest(request);
    }


    private static String errorAttribute(String name)
    {
        return javaxOrJakarta() + ".servlet.error." + name;
    }


    private static String javaxOrJakarta()
    {
        try
        {
            Class.forName("jakarta.servlet.RequestDispatcher");
            return "jakarta";
        }
        catch (ClassNotFoundException e)
        {
            return "javax";
        }
    }
}
