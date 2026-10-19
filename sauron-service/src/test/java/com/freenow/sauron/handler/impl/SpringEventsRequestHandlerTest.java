package com.freenow.sauron.handler.impl;

import com.freenow.sauron.datatransferobject.BuildRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringEventsRequestHandlerTest
{
    @Test
    public void handlePublishesTheRequestItselfAsAnEvent()
    {
        List<Object> events = new ArrayList<>();
        BuildRequest request = BuildRequest.builder().serviceName("svc").build();

        new SpringEventsRequestHandler(events::add).handle(request);

        assertThat(events).containsExactly(request);
    }


    @Test
    public void consumeForwardsToTheConsumer()
    {
        List<BuildRequest> consumed = new ArrayList<>();
        SpringEventsRequestHandler handler = new SpringEventsRequestHandler(event -> {});
        handler.setConsumer(consumed::add);
        BuildRequest request = new BuildRequest();

        handler.consume(request);

        assertThat(consumed).containsExactly(request);
    }


    @Test
    public void consumeWithoutConsumerDiscardsTheRequest()
    {
        SpringEventsRequestHandler handler = new SpringEventsRequestHandler(event -> {});

        handler.consume(new BuildRequest());

        assertThat(handler).extracting("consumer").isNull();
    }


    @Test
    public void springDeliversPublishedRequestsToConsume()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.registerBean(SpringEventsRequestHandler.class, () -> new SpringEventsRequestHandler(context));
            context.refresh();
            SpringEventsRequestHandler handler = context.getBean(SpringEventsRequestHandler.class);
            List<BuildRequest> consumed = new ArrayList<>();
            handler.setConsumer(consumed::add);
            BuildRequest request = BuildRequest.builder().serviceName("svc").build();

            handler.handle(request);

            assertThat(consumed).containsExactly(request);
        }
    }
}
