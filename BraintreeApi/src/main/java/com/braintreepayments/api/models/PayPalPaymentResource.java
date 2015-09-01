package com.braintreepayments.api.models;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A PayPal payment resource
 */
public class PayPalPaymentResource {

    private static final String PAYMENT_RESOURCE_KEY = "paymentResource";
    private static final String REDIRECT_URL_KEY = "redirectUrl";
    private static final String AGREEMENT_SETUP_KEY = "agreementSetup";
    private static final String APPROVAL_URL_KEY = "approvalUrl";

    private String mRedirectUrl;

    public PayPalPaymentResource redirectUrl(String redirectUrl) {
        mRedirectUrl = redirectUrl;
        return this;
    }

    /**
     * The redirectUrl for the payment used by One Touch Core for authorization
     *
     * @return a redirect URL string containing an EC token
     */
    public String getRedirectUrl() {
        return mRedirectUrl;
    }

    /**
     * Create a PayPalPaymentResource from a jsonString. Checks for keys associated with
     * Single Payment and Billing Agreement flows.
     *
     * @param jsonString a valid JSON string representing the payment resource
     * @return a PayPal payment resource
     * @throws JSONException
     */
    public static PayPalPaymentResource fromJson(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);

        PayPalPaymentResource payPalPaymentResource = new PayPalPaymentResource();
        JSONObject redirectJson = json.optJSONObject(PAYMENT_RESOURCE_KEY);
        if(redirectJson != null) {
            payPalPaymentResource.redirectUrl(redirectJson.optString(REDIRECT_URL_KEY));
        } else {
            redirectJson = json.optJSONObject(AGREEMENT_SETUP_KEY);
            payPalPaymentResource.redirectUrl(redirectJson.optString(APPROVAL_URL_KEY));
        }
        return payPalPaymentResource;
    }
}
