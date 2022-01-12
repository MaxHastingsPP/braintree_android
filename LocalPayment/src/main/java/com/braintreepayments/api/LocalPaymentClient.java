package com.braintreepayments.api;

import android.net.Uri;

import androidx.activity.result.ActivityResultRegistry;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Used to integrate with local payments.
 */
public class LocalPaymentClient {

    private static final String LOCAL_PAYMENT_CANCEL = "local-payment-cancel";
    private static final String LOCAL_PAYMENT_SUCCESS = "local-payment-success";

    private final BraintreeClient braintreeClient;
    private final PayPalDataCollector payPalDataCollector;

    private LocalPaymentListener localPaymentListener;
    private final LocalPaymentBrowserSwitchObserver localPaymentBrowserSwitchObserver;

    public LocalPaymentClient(Fragment fragment, @NonNull BraintreeClient braintreeClient) {
        this(fragment, braintreeClient, new PayPalDataCollector());
    }

    @VisibleForTesting
    LocalPaymentClient(Fragment fragment, @NonNull BraintreeClient braintreeClient, @NonNull PayPalDataCollector payPalDataCollector) {
        this.braintreeClient = braintreeClient;
        this.payPalDataCollector = payPalDataCollector;

        this.localPaymentBrowserSwitchObserver =
            new LocalPaymentBrowserSwitchObserver(this);
        fragment.getLifecycle().addObserver(localPaymentBrowserSwitchObserver);
    }

    /**
     * Prepares the payment flow for a specific type of local payment.
     *
     * @param request  {@link LocalPaymentRequest} with the payment details.
     * @param callback {@link LocalPaymentStartCallback}
     */
    public void startPayment(@NonNull final LocalPaymentRequest request, @NonNull final LocalPaymentStartCallback callback) {
        Exception exception = null;

        //noinspection ConstantConditions
        if (callback == null) {
            throw new RuntimeException("A LocalPaymentCallback is required.");
        }

        //noinspection ConstantConditions
        if (request == null) {
            exception = new BraintreeException("A LocalPaymentRequest is required.");
        } else if (request.getPaymentType() == null || request.getAmount() == null) {
            exception = new BraintreeException(
                    "LocalPaymentRequest is invalid, paymentType and amount are required.");
        }

        if (exception != null) {
            callback.onResult(null, exception);
        } else {
            braintreeClient.getConfiguration(new ConfigurationCallback() {
                @Override
                public void onResult(@Nullable Configuration configuration, @Nullable Exception error) {
                    if (configuration != null) {
                        if (!configuration.isPayPalEnabled()) {
                            callback.onResult(null, new ConfigurationException("Local payments are not enabled for this merchant."));
                            return;
                        }

                        String returnUrl = braintreeClient.getReturnUrlScheme() + "://" + LOCAL_PAYMENT_SUCCESS;
                        String cancel = braintreeClient.getReturnUrlScheme() + "://" + LOCAL_PAYMENT_CANCEL;

                        sendAnalyticsEvent(request.getPaymentType(), "local-payment.start-payment.selected");

                        String url = "/v1/local_payments/create";
                        braintreeClient.sendPOST(url, request.build(returnUrl, cancel), new HttpResponseCallback() {

                            @Override
                            public void onResult(String responseBody, Exception httpError) {
                                if (responseBody != null) {
                                    try {
                                        JSONObject responseJson = new JSONObject(responseBody);
                                        String redirectUrl = responseJson.getJSONObject("paymentResource").getString("redirectUrl");
                                        String paymentToken = responseJson.getJSONObject("paymentResource").getString("paymentToken");

                                        sendAnalyticsEvent(request.getPaymentType(), "local-payment.create.succeeded");
                                        LocalPaymentResult transaction = new LocalPaymentResult(request, redirectUrl, paymentToken);
                                        callback.onResult(transaction, null);
                                    } catch (JSONException e) {
                                        sendAnalyticsEvent(request.getPaymentType(), "local-payment.webswitch.initiate.failed");
                                        callback.onResult(null, e);
                                    }
                                } else {
                                    sendAnalyticsEvent(request.getPaymentType(), "local-payment.webswitch.initiate.failed");
                                    callback.onResult(null, httpError);
                                }
                            }
                        });

                    } else {
                        callback.onResult(null, error);
                    }
                }
            });
        }
    }

    /**
     * Initiates the browser switch for a payment flow by opening a browser where the customer can authenticate with their bank.
     *
     * @param activity           Android FragmentActivity
     * @param localPaymentResult {@link LocalPaymentRequest} which has already been sent to {@link #startPayment(LocalPaymentRequest, LocalPaymentStartCallback)}
     *                           and now has an approvalUrl and paymentId.
     */
    public void approvePayment(@NonNull FragmentActivity activity, @NonNull LocalPaymentResult localPaymentResult) throws JSONException, BrowserSwitchException {
        //noinspection ConstantConditions
        if (activity == null) {
            throw new RuntimeException("A FragmentActivity is required.");
        }

        //noinspection ConstantConditions
        if (localPaymentResult == null) {
            throw new RuntimeException("A LocalPaymentTransaction is required.");
        }

        BrowserSwitchOptions browserSwitchOptions = new BrowserSwitchOptions()
                .requestCode(BraintreeRequestCodes.LOCAL_PAYMENT)
                .returnUrlScheme(braintreeClient.getReturnUrlScheme())
                .url(Uri.parse(localPaymentResult.getApprovalUrl()));

        String paymentType = localPaymentResult.getRequest().getPaymentType();

        browserSwitchOptions.metadata(new JSONObject()
                .put("merchant-account-id", localPaymentResult.getRequest().getMerchantAccountId())
                .put("payment-type", localPaymentResult.getRequest().getPaymentType()));

        braintreeClient.startBrowserSwitch(activity, browserSwitchOptions);
        sendAnalyticsEvent(paymentType, "local-payment.webswitch.initiate.succeeded");
    }

    /**
     * @param browserSwitchResult a {@link BrowserSwitchResult} with a {@link BrowserSwitchStatus}
     */
    public void onBrowserSwitchResult(@NonNull BrowserSwitchResult browserSwitchResult) {
        //noinspection ConstantConditions
        if (browserSwitchResult == null) {
            localPaymentListener.onLocalPaymentError(new BraintreeException("BrowserSwitchResult cannot be null"));
            return;
        }
        JSONObject metadata = browserSwitchResult.getRequestMetadata();

        final String paymentType = Json.optString(metadata, "payment-type", null);
        String merchantAccountId = Json.optString(metadata, "merchant-account-id", null);

        int result = browserSwitchResult.getStatus();
        switch (result) {
            case BrowserSwitchStatus.CANCELED:
                sendAnalyticsEvent(paymentType, "local-payment.webswitch.canceled");
                localPaymentListener.onLocalPaymentError(new UserCanceledException("User canceled Local Payment."));
                return;
            case BrowserSwitchStatus.SUCCESS:
                Uri deepLinkUri = browserSwitchResult.getDeepLinkUrl();
                if (deepLinkUri == null) {
                    sendAnalyticsEvent(paymentType, "local-payment.webswitch-response.invalid");
                    Exception localPaymentError =
                            new BraintreeException("LocalPayment encountered an error, return URL is invalid.");
                    localPaymentListener.onLocalPaymentError(localPaymentError);
                    return;
                }

                String responseString = deepLinkUri.toString();
                if (responseString.toLowerCase().contains(LOCAL_PAYMENT_CANCEL.toLowerCase())) {
                    sendAnalyticsEvent(paymentType, "local-payment.webswitch.canceled");
                    localPaymentListener.onLocalPaymentError(new UserCanceledException("User canceled Local Payment."));
                    return;
                }
                JSONObject payload = new JSONObject();

                try {
                    payload.put("merchant_account_id", merchantAccountId);

                    JSONObject paypalAccount = new JSONObject()
                            .put("intent", "sale")
                            .put("response", new JSONObject().put("webURL", responseString))
                            .put("options", new JSONObject().put("validate", false))
                            .put("response_type", "web")
                            .put("correlation_id", payPalDataCollector.getClientMetadataId(braintreeClient.getApplicationContext()));
                    payload.put("paypal_account", paypalAccount);

                    JSONObject metaData = new JSONObject()
                            .put("source", "client")
                            .put("integration", braintreeClient.getIntegrationType())
                            .put("sessionId", braintreeClient.getSessionId());
                    payload.put("_meta", metaData);

                    String url = "/v1/payment_methods/paypal_accounts";
                    braintreeClient.sendPOST(url, payload.toString(), new HttpResponseCallback() {

                        @Override
                        public void onResult(String responseBody, Exception httpError) {
                            if (responseBody != null) {
                                try {
                                    LocalPaymentNonce result = LocalPaymentNonce.fromJSON(new JSONObject(responseBody));
                                    sendAnalyticsEvent(paymentType, "local-payment.tokenize.succeeded");
                                    localPaymentListener.onLocalPaymentSuccess(result);
                                } catch (JSONException jsonException) {
                                    sendAnalyticsEvent(paymentType, "local-payment.tokenize.failed");
                                    localPaymentListener.onLocalPaymentError(jsonException);
                                }
                            } else {
                                sendAnalyticsEvent(paymentType, "local-payment.tokenize.failed");
                                localPaymentListener.onLocalPaymentError(httpError);
                            }
                        }
                    });
                } catch (JSONException ignored) { /* do nothing */ }
        }
    }

    private void sendAnalyticsEvent(String paymentType, String eventSuffix) {
        String eventPrefix = (paymentType == null) ? "unknown" : paymentType;
        braintreeClient.sendAnalyticsEvent(String.format("%s.%s", eventPrefix, eventSuffix));
    }

    public void deliverBrowserSwitchResult(FragmentActivity activity) {
        BrowserSwitchResult browserSwitchResult =
                braintreeClient.deliverBrowserSwitchResult(activity);
        if (browserSwitchResult != null) {
            onBrowserSwitchResult(browserSwitchResult);
        }
    }

    public void setLocalPaymentListener(LocalPaymentListener localPaymentListener) {
        this.localPaymentListener = localPaymentListener;
    }
}
