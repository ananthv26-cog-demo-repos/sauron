package com.freenow.sauron.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConstantsTest
{
    @Test
    public void queueNameIsStable()
    {
        assertThat(Constants.SAURON_QUEUE_NAME).isEqualTo("asyncSauronPipelineRequest");
    }


    @Test
    public void cannotBeInstantiated() throws Exception
    {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
