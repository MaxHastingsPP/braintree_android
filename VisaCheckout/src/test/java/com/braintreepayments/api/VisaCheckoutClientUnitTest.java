package com.braintreepayments.api;

import com.visa.checkout.Environment;
import com.visa.checkout.Profile.CardBrand;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class VisaCheckoutClientUnitTest {

    private Configuration mConfigurationWithVisaCheckout;
    private VisaCheckoutPaymentSummary visaCheckoutPaymentSummary;
    private ClassHelper classHelper;

    @Before
    public void setup() throws Exception {
        configurationWithVisaCheckout = Configuration.fromJson(Fixtures.CONFIGURATION_WITH_VISA_CHECKOUT);
        classHelper = mock(ClassHelper.class);
        when(classHelper.isClassAvailable("com.visa.checkout.VisaCheckoutSdk")).thenReturn(true);
        visaCheckoutPaymentSummary = new VisaCheckoutPaymentSummary("stubbedCallId", "stubbedEncKey", "stubbedEncPaymentData");
    }

    @Test
    public void createProfileBuilder_whenNotEnabled_throwsConfigurationException() {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();

        Configuration configuration = TestConfigurationBuilder.basicConfig();
        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configuration)
                .build();

        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutCreateProfileBuilderCallback listener = mock(VisaCheckoutCreateProfileBuilderCallback.class);
        sut.createProfileBuilder(listener);

        ArgumentCaptor<ConfigurationException> captor = ArgumentCaptor.forClass(ConfigurationException.class);
        verify(listener, times(1)).onResult((VisaCheckoutProfile) isNull(), captor.capture());

        ConfigurationException configurationException = captor.getValue();
        assertEquals("Visa Checkout is not enabled.", configurationException.getMessage());
    }

    @Test
    public void createProfileBuilder_whenVisaCheckoutSdkNotFound_throwsConfigurationException() throws JSONException {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();

        String configString = new TestConfigurationBuilder()
                .environment("production")
                .visaCheckout(new TestConfigurationBuilder.TestVisaCheckoutConfigurationBuilder()
                        .apikey("gwApiKey")
                        .supportedCardTypes(CardBrand.VISA, CardBrand.MASTERCARD)
                        .externalClientId("gwExternalClientId"))
                .build();
        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(Configuration.fromJson(configString))
                .build();

        ClassHelper classHelper = mock(ClassHelper.class);
        when(classHelper.isClassAvailable("com.visa.checkout.VisaCheckoutSdk")).thenReturn(false);
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutCreateProfileBuilderCallback listener = mock(VisaCheckoutCreateProfileBuilderCallback.class);
        sut.createProfileBuilder(listener);

        ArgumentCaptor<ConfigurationException> captor = ArgumentCaptor.forClass(ConfigurationException.class);
        verify(listener, times(1)).onResult((VisaCheckoutProfile) isNull(), captor.capture());

        ConfigurationException configurationException = captor.getValue();
        assertEquals("Visa Checkout is not enabled.", configurationException.getMessage());
    }

    @Test
    public void createProfileBuilder_passesConfigDetailsToVisaCheckoutProfile() throws Exception {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();
        String configString = new TestConfigurationBuilder()
                .environment("production")
                .visaCheckout(new TestConfigurationBuilder.TestVisaCheckoutConfigurationBuilder()
                        .apikey("gwApiKey")
                        .supportedCardTypes(CardBrand.VISA, CardBrand.MASTERCARD)
                        .externalClientId("gwExternalClientId"))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(Configuration.fromJson(configString))
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutCreateProfileBuilderCallback callback = mock(VisaCheckoutCreateProfileBuilderCallback.class);
        sut.createProfileBuilder(callback);

        ArgumentCaptor<VisaCheckoutProfile> captor = ArgumentCaptor.forClass(VisaCheckoutProfile.class);
        verify(callback).onResult(captor.capture(), (Exception) isNull());

        VisaCheckoutProfile profile = captor.getValue();
        assertEquals(Environment.PRODUCTION, profile.getEnvironment());
        assertEquals("gwApiKey", profile.getMerchantApiKey());
        assertEquals("gwExternalClientId", profile.getExternalClientId());
        assertEquals(CardBrand.VISA, profile.getAcceptedCardBrands().get(0));
        assertEquals(CardBrand.MASTERCARD, profile.getAcceptedCardBrands().get(1));
    }

    @Test
    public void createProfileBuilder_whenProduction_setsVisaCheckoutProfileEnvironmentProduction() throws Exception {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();
        String configString = new TestConfigurationBuilder()
                .environment("production")
                .visaCheckout(new TestConfigurationBuilder.TestVisaCheckoutConfigurationBuilder()
                        .apikey("gwApiKey")
                        .supportedCardTypes(CardBrand.VISA, CardBrand.MASTERCARD)
                        .externalClientId("gwExternalClientId"))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(Configuration.fromJson(configString))
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutCreateProfileBuilderCallback callback = mock(VisaCheckoutCreateProfileBuilderCallback.class);
        sut.createProfileBuilder(callback);

        ArgumentCaptor<VisaCheckoutProfile> captor = ArgumentCaptor.forClass(VisaCheckoutProfile.class);
        verify(callback).onResult(captor.capture(), (Exception) isNull());

        VisaCheckoutProfile profile = captor.getValue();
        assertEquals(Environment.PRODUCTION, profile.getEnvironment());
    }

    @Test
    public void createProfileBuilder_whenNotProduction_setsVisaCheckoutProfileEnvironmentSandbox() throws Exception {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();
        String configString = new TestConfigurationBuilder()
                .environment("environment")
                .visaCheckout(new TestConfigurationBuilder.TestVisaCheckoutConfigurationBuilder()
                        .apikey("gwApiKey")
                        .supportedCardTypes("VISA", "MASTERCARD")
                        .externalClientId("gwExternalClientId"))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(Configuration.fromJson(configString))
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutCreateProfileBuilderCallback callback = mock(VisaCheckoutCreateProfileBuilderCallback.class);
        sut.createProfileBuilder(callback);

        ArgumentCaptor<VisaCheckoutProfile> captor = ArgumentCaptor.forClass(VisaCheckoutProfile.class);
        verify(callback).onResult(captor.capture(), (Exception) isNull());

        VisaCheckoutProfile profile = captor.getValue();
        assertEquals(Environment.SANDBOX, profile.getEnvironment());
    }

    @Test
    public void tokenize_whenSuccessful_postsVisaPaymentMethodNonce() throws JSONException {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder()
                .tokenizeRESTSuccess(new JSONObject(Fixtures.PAYMENT_METHODS_VISA_CHECKOUT_RESPONSE))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configurationWithVisaCheckout)
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutTokenizeCallback listener = mock(VisaCheckoutTokenizeCallback.class);
        sut.tokenize(visaCheckoutPaymentSummary, listener);

        verify(listener).onResult(visaCheckoutNonce, null);
    }

    @Test
    public void tokenize_whenSuccessful_sendsAnalyticEvent() throws JSONException {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder()
                .tokenizeRESTSuccess(new JSONObject(Fixtures.PAYMENT_METHODS_VISA_CHECKOUT_RESPONSE))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configurationWithVisaCheckout)
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutTokenizeCallback listener = mock(VisaCheckoutTokenizeCallback.class);
        sut.tokenize(visaCheckoutPaymentSummary, listener);

        verify(braintreeClient).sendAnalyticsEvent("visacheckout.tokenize.succeeded");
    }

    @Test
    public void tokenize_whenFailure_postsException() {
        Exception tokenizeError = new Exception("Mock Failure");
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder()
                .tokenizeRESTError(tokenizeError)
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configurationWithVisaCheckout)
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutTokenizeCallback listener = mock(VisaCheckoutTokenizeCallback.class);
        sut.tokenize(visaCheckoutPaymentSummary, listener);

        verify(listener).onResult(null, tokenizeError);
    }

    @Test
    public void tokenize_whenFailure_sendsAnalyticEvent() {
        Exception tokenizeError = new Exception("Mock Failure");
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder()
                .tokenizeRESTError(tokenizeError)
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configurationWithVisaCheckout)
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient, classHelper);

        VisaCheckoutTokenizeCallback listener = mock(VisaCheckoutTokenizeCallback.class);
        sut.tokenize(visaCheckoutPaymentSummary, listener);

        verify(braintreeClient).sendAnalyticsEvent("visacheckout.tokenize.failed");
    }
}
