package com.braintreepayments.demo.test;

import static com.braintreepayments.AutomatorAction.click;
import static com.braintreepayments.AutomatorAction.setText;
import static com.braintreepayments.AutomatorAssertion.text;
import static com.braintreepayments.DeviceAutomator.onDevice;
import static com.braintreepayments.UiObjectMatcher.withResourceId;
import static com.braintreepayments.UiObjectMatcher.withText;
import static com.braintreepayments.UiObjectMatcher.withTextContaining;
import static com.braintreepayments.UiObjectMatcher.withTextStartingWith;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.braintreepayments.demo.test.utilities.TestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4ClassRunner.class)
public class ThreeDSecureCardinalTest extends TestHelper {

    @Before
    public void setup() {
        super.setup();
        launchApp();
        onDevice(withText("Credit or Debit Cards")).waitForEnabled().perform(click());

        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
                .edit()
                .putBoolean("enable_three_d_secure", true)
                .putBoolean("amex_rewards_balance", false)
                .commit();
    }

    @Test(timeout = 40000)
    public void threeDSecure_authenticates() {
        enterCardNumber("4000000000001091");

        onDevice(withText("Purchase")).perform(click());

        enterPassword();

        getNonceDetails().check(text(containsString("Card Last Two: 91")));
        getNonceDetails().check(text(containsString("isLiabilityShifted: true")));
        getNonceDetails().check(text(containsString("isLiabilityShiftPossible: true")));

        onDevice(withText("Create a Transaction")).perform(click());
        onDevice(withTextStartingWith("created")).check(text(endsWith("authorized")));
    }

    @Test(timeout = 40000)
    public void testThreeDSecurePaymentFlowV2_acceptsPassword_failsToAuthenticateNonce_dueToCardinalError() {
        enterCardNumber("4000000000001125");

        onDevice(withText("Purchase")).perform(click());

        enterPassword();

        onDevice(withTextContaining("An error occurred")).waitForExists(30000);
    }

    @Test(timeout = 40000)
    public void testThreeDSecurePaymentFlowV2_returnsToApp_whenCancelTapped() {
        enterCardNumber("4000000000001091");

        onDevice(withText("Purchase")).perform(click());

        onDevice(withText("Close")).perform(click());

        onDevice(withTextContaining("User canceled 3DS")).waitForExists(30000);
    }

    @Test(timeout = 40000)
    public void testThreeDSecurePaymentFlowV2_lookupError() {
        enterCardNumber("4000000000001034");

        onDevice(withText("Purchase")).perform(click());

        onDevice(withTextContaining("Device Data: null")).waitForExists(30000);
    }

    @Test(timeout = 40000)
    public void testThreeDSecurePaymentFlowV2_timeout() {
        enterCardNumber("4000000000001075");

        onDevice(withText("Purchase")).perform(click());

        onDevice(withTextContaining("Device Data: null")).waitForExists(70000);
    }

    private void enterCardNumber(String cardNumber) {
        onDevice(withText("Card Number")).perform(setText(cardNumber));
        onDevice(withText("Expiration Date")).perform(setText(validExpirationText()));
        onDevice(withText("CVV")).perform(setText("123"));
        onDevice(withText("Postal Code")).perform(setText("12345"));
    }

    @Test(timeout = 40000)
    public void testThreeDSecurePaymentFlowV2_bypassedAuthentication() {
        enterCardNumber("4000000000001083");

        onDevice(withText("Purchase")).perform(click());

        getNonceDetails().check(text(containsString("isLiabilityShifted: false")));
        getNonceDetails().check(text(containsString("isLiabilityShiftPossible: false")));
        getNonceDetails().check(text(containsString("wasVerified: true")));
    }

    private void enterPassword() {
        String codeEditTextResId = "com.braintreepayments.demo:id/codeEditTextField";
        onDevice(withResourceId(codeEditTextResId)).waitForExists();
        onDevice(withResourceId(codeEditTextResId)).perform(click());
        onDevice().typeText("1234");

        onDevice().pressEnter();
        onDevice(withText("Submit")).perform(click());
    }
}
