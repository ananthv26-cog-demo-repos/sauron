package com.freenow.sauron.config;

import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import org.junit.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorAttributesConfigTest
{
    private final ErrorAttributes errorAttributes = new ErrorAttributesConfig().errorAttributes();


    @Test
    public void defaultErrorBodyKeepsBlankMessageBeforePath()
    {
        Map<String, Object> body = errorAttributes.getErrorAttributes(request("/does-not-exist"), ErrorAttributeOptions.defaults());

        assertThat(body).containsOnlyKeys("timestamp", "status", "error", "message", "path");
        assertThat(body.keySet()).containsExactly("timestamp", "status", "error", "message", "path");
        assertThat(body.get("message")).isEqualTo("");
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("path")).isEqualTo("/does-not-exist");
    }


    @Test
    public void messageIsKeptWhenIncluded()
    {
        ServletWebRequest request = request("/x");
        request.getRequest().setAttribute(RequestDispatcher.ERROR_MESSAGE, "boom");

        Map<String, Object> body = errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE));

        assertThat(body.get("message")).isEqualTo("boom");
    }


    @Test
    public void blankMessageIsAppendedWhenThereIsNoPath()
    {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);

        Map<String, Object> body = errorAttributes.getErrorAttributes(new ServletWebRequest(raw), ErrorAttributeOptions.defaults());

        assertThat(body).doesNotContainKey("path");
        assertThat(body.get("message")).isEqualTo("");
    }


    private static ServletWebRequest request(String path)
    {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        raw.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        return new ServletWebRequest(raw);
    }
}
