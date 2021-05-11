package com.braintreepayments.api;

import com.visa.checkout.Profile.CardBrand;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class VisaCheckoutClientUnitTest {

    private Configuration mConfigurationWithVisaCheckout;
    private VisaCheckoutPaymentSummary visaCheckoutPaymentSummary;

    @Before
    public void setup() throws Exception {
        configurationWithVisaCheckout = Configuration.fromJson(Fixtures.CONFIGURATION_WITH_VISA_CHECKOUT);

        visaCheckoutPaymentSummary = new VisaCheckoutPaymentSummary("stubbedCallId", "stubbedEncKey", "stubbedEncPaymentData");
    }

    @Test
    public void createProfileBuilder_whenNotEnabled_throwsConfigurationException() {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();

        Configuration configuration = TestConfigurationBuilder.basicConfig();
        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configuration)
                .build();

        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

        VisaCheckoutCreateProfileBuilderCallback listener = mock(VisaCheckoutCreateProfileBuilderCallback.class);
        sut.createProfileBuilder(listener);

        ArgumentCaptor<ConfigurationException> captor = ArgumentCaptor.forClass(ConfigurationException.class);
        verify(listener, times(1)).onResult((VisaCheckoutProfile) isNull(), captor.capture());

        ConfigurationException configurationException = captor.getValue();
        assertEquals("Visa Checkout is not enabled.", configurationException.getMessage());
    }

    @Test
    public void createProfileBuilder_whenProduction_usesProductionConfig() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);

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
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

        sut.createProfileBuilder(new VisaCheckoutCreateProfileBuilderCallback() {
            @Override
            public void onResult(VisaCheckoutProfile profile, Exception error) {
                List<String> expectedCardBrands = Arrays.asList(CardBrand.VISA, CardBrand.MASTERCARD);
                assertNotNull(profile);
                assertEquals(expectedCardBrands.toArray(), profile.getProfile().getAcceptedCardBrands());
                lock.countDown();
            }
        });

        lock.await();
    }

    @Test
    public void createProfileBuilder_whenNotProduction_usesSandboxConfig() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);

        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder().build();
        String configString = new TestConfigurationBuilder()
                .environment("environment")
                .visaCheckout(new TestConfigurationBuilder.TestVisaCheckoutConfigurationBuilder()
                        .apikey("gwApiKey")
                        .supportedCardTypes(CardBrand.VISA, CardBrand.MASTERCARD)
                        .externalClientId("gwExternalClientId"))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(Configuration.fromJson(configString))
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

        sut.createProfileBuilder(new VisaCheckoutCreateProfileBuilderCallback() {
            @Override
            public void onResult(VisaCheckoutProfile profile, Exception error) {
                List<String> expectedCardBrands = Arrays.asList(CardBrand.VISA, CardBrand.MASTERCARD);
                assertNotNull(profile);
                assertEquals(expectedCardBrands.toArray(), profile.getProfile().getAcceptedCardBrands());
                lock.countDown();
            }
        });

        lock.await();
    }

    @Test
    public void tokenize_whenSuccessful_postsVisaPaymentMethodNonce() throws JSONException {
        TokenizationClient tokenizationClient = new MockTokenizationClientBuilder()
                .tokenizeRESTSuccess(new JSONObject(Fixtures.PAYMENT_METHODS_VISA_CHECKOUT_RESPONSE))
                .build();

        BraintreeClient braintreeClient = new MockBraintreeClientBuilder()
                .configuration(configurationWithVisaCheckout)
                .build();
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

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
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

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
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

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
        VisaCheckoutClient sut = new VisaCheckoutClient(braintreeClient, tokenizationClient);

        VisaCheckoutTokenizeCallback listener = mock(VisaCheckoutTokenizeCallback.class);
        sut.tokenize(visaCheckoutPaymentSummary, listener);

        verify(braintreeClient).sendAnalyticsEvent("visacheckout.tokenize.failed");
    }
}
