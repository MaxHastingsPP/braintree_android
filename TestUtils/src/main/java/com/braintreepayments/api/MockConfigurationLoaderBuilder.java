package com.braintreepayments.api;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MockConfigurationLoaderBuilder {

    private Configuration configuration;
    private Exception configurationError;

    public MockConfigurationLoaderBuilder configuration(Configuration configuration) {
        this.configuration = configuration;
        return this;
    }

    public MockConfigurationLoaderBuilder configurationError(Exception configurationError) {
        this.configurationError = configurationError;
        return this;
    }

    public ConfigurationLoader build() {
        ConfigurationLoader configurationLoader = mock(ConfigurationLoader.class);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                ConfigurationLoaderCallback callback = (ConfigurationLoaderCallback) invocation.getArguments()[1];
                if (configuration != null) {
                    callback.onResult(configuration, null);
                } else if (configurationError != null) {
                    callback.onResult(null, configurationError);
                }
                return null;
            }
        }).when(configurationLoader).loadConfiguration(any(Authorization.class), any(ConfigurationLoaderCallback.class));

        return configurationLoader;
    }

}
