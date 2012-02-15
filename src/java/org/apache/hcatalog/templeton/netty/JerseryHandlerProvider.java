package org.apache.hcatalog.templeton.netty;

import com.sun.jersey.spi.container.ContainerProvider;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.container.ContainerException;

public class JerseyHandlerProvider
    implements ContainerProvider<JerseryHandler>
{
    public JerseryHandler createContainer(Class<JerseryHandler> type,
                                          ResourceConfig config,
                                          WebApplication application)
        throws ContainerException
    {
        if (type != JerseryHandler.class)
            return null;

        return new JerseryHandler(application, config);
    }
}
