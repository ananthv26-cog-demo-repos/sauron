package com.freenow.sauron.handler.impl;

import com.freenow.sauron.datatransferobject.BuildRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class SpringEventsRequestHandlerTest
{
    @Test
    public void handlePublishesTheRequestAsAnEventThatTheSameBeanConsumes()
    {
        List<BuildRequest> consumed = new ArrayList<>();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.registerBean(SpringEventsRequestHandler.class, () -> new SpringEventsRequestHandler(context));
            context.refresh();
            SpringEventsRequestHandler handler = context.getBean(SpringEventsRequestHandler.class);
            handler.setConsumer(consumed::add);
            BuildRequest request = BuildRequest.builder().serviceName("svc").buildId("b1").build();

            handler.handle(request);

            assertThat(consumed).containsExactly(request);
        }
    }


    @Test
    public void anyBuildRequestEventInTheContextIsConsumed()
    {
        List<BuildRequest> consumed = new ArrayList<>();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.registerBean(SpringEventsRequestHandler.class, () -> new SpringEventsRequestHandler(context));
            context.refresh();
            context.getBean(SpringEventsRequestHandler.class).setConsumer(consumed::add);
            BuildRequest request = BuildRequest.builder().serviceName("other-publisher").build();

            context.publishEvent(request);

            assertThat(consumed).containsExactly(request);
        }
    }


    @Test
    public void consumeWithoutConsumerDiscardsTheMessage()
    {
        List<Object> published = new ArrayList<>();
        SpringEventsRequestHandler handler = new SpringEventsRequestHandler(published::add);

        assertThatCode(() -> handler.consume(BuildRequest.builder().serviceName("svc").build())).doesNotThrowAnyException();
        assertThat(published).isEmpty();
    }


    @Test
    public void handleOnlyPublishesAndDoesNotCallTheConsumerDirectly()
    {
        List<Object> published = new ArrayList<>();
        List<BuildRequest> consumed = new ArrayList<>();
        SpringEventsRequestHandler handler = new SpringEventsRequestHandler(published::add);
        handler.setConsumer(consumed::add);
        BuildRequest request = BuildRequest.builder().serviceName("svc").build();

        handler.handle(request);

        assertThat(published).containsExactly(request);
        assertThat(consumed).isEmpty();
    }
}
