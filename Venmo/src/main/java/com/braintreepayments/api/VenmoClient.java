package com.braintreepayments.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;

/**
 * Used to create and tokenize Venmo accounts. For more information see the <a
 * href="https://developer.paypal.com/braintree/docs/guides/venmo/overview">documentation</a>
 */
public class VenmoClient {

    static final String VENMO_PACKAGE_NAME = "com.venmo";
    private final BraintreeClient braintreeClient;
    private final VenmoApi venmoApi;
    private final VenmoSharedPrefsWriter sharedPrefsWriter;
    private final DeviceInspector deviceInspector;

    private VenmoClient(BraintreeClient braintreeClient, ApiClient apiClient) {
        this(braintreeClient, new VenmoApi(braintreeClient, apiClient),
                new VenmoSharedPrefsWriter(), new DeviceInspector());
    }

    /**
     * Create a new instance of {@link VenmoClient} using a {@link BraintreeClient}.
     *
     * @param braintreeClient a {@link BraintreeClient}
     */
    public VenmoClient(@NonNull BraintreeClient braintreeClient) {
        this(braintreeClient, new ApiClient(braintreeClient));
    }

    @VisibleForTesting
    VenmoClient(BraintreeClient braintreeClient, VenmoApi venmoApi,
                VenmoSharedPrefsWriter sharedPrefsWriter, DeviceInspector deviceInspector) {
        this.braintreeClient = braintreeClient;
        this.sharedPrefsWriter = sharedPrefsWriter;
        this.deviceInspector = deviceInspector;
        this.venmoApi = venmoApi;
    }

    /**
     * Launches an Android Intent pointing to the Venmo app on the Google Play Store
     *
     * @param activity used to open the Venmo's Google Play Store
     */
    public void showVenmoInGooglePlayStore(@NonNull FragmentActivity activity) {
        braintreeClient.sendAnalyticsEvent("android.pay-with-venmo.app-store.invoked");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(
                "https://play.google.com/store/apps/details?id=" + VENMO_PACKAGE_NAME));
        activity.startActivity(intent);
    }


    /**
     * Start the Pay With Venmo flow. This will return a {@link VenmoAuthChallenge} that will be 
     * used to authenticate the user by switching to the Venmo app in {@link 
     * VenmoLauncher#launch(VenmoAuthChallenge)} 
     * <p>
     * If the Venmo app is not available, {@link AppSwitchNotAvailableException} will be sent to
     * {@link VenmoAuthChallengeCallback#onVenmoAuthChallenge(VenmoAuthChallenge, Exception)} 
     *
     * @param activity Android FragmentActivity
     * @param request  {@link VenmoRequest}
     * @param callback {@link VenmoAuthChallengeCallback}
     */
    public void createPaymentAuthRequest(@NonNull final FragmentActivity activity,
                                         @NonNull final VenmoRequest request,
                                         @NonNull VenmoAuthChallengeCallback callback) {
        braintreeClient.sendAnalyticsEvent("pay-with-venmo.selected");
        braintreeClient.getConfiguration((configuration, error) -> {
            if (configuration == null) {
                callback.onVenmoAuthChallenge(null, error);
                braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.failed");
                return;
            }

            String exceptionMessage = null;
            if (!configuration.isVenmoEnabled()) {
                exceptionMessage = "Venmo is not enabled";
            } else if (!deviceInspector.isVenmoAppSwitchAvailable(activity)) {
                exceptionMessage = "Venmo is not installed";
            }

            if (exceptionMessage != null) {
                callback.onVenmoAuthChallenge(null,
                        new AppSwitchNotAvailableException(exceptionMessage));
                braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.failed");
                return;
            }

            // Merchants are not allowed to collect user addresses unless ECD (Enriched Customer
            // Data) is enabled on the BT Control Panel.
            if ((request.getCollectCustomerShippingAddress() ||
                    request.getCollectCustomerBillingAddress()) &&
                    !configuration.getVenmoEnrichedCustomerDataEnabled()) {
                callback.onVenmoAuthChallenge(null, new BraintreeException(
                        "Cannot collect customer data when ECD is disabled. Enable this feature " +
                                "in the Control Panel to collect this data."));
                braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.failed");
                return;
            }

            String venmoProfileId = request.getProfileId();
            if (TextUtils.isEmpty(venmoProfileId)) {
                venmoProfileId = configuration.getVenmoMerchantId();
            }

            final String finalVenmoProfileId = venmoProfileId;
            venmoApi.createPaymentContext(request, venmoProfileId,
                    (paymentContextId, exception) -> {
                        if (exception == null) {
                            braintreeClient.getAuthorization((authorization, authError) -> {
                                if (authorization != null) {
                                    createPaymentAuthRequest(activity, request, configuration,
                                            authorization, finalVenmoProfileId,
                                            paymentContextId, callback);
                                } else {
                                    callback.onVenmoAuthChallenge(null, authError);
                                }
                            });
                        } else {
                            callback.onVenmoAuthChallenge(null, exception);
                            braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.failed");
                        }
                    });
        });
    }

    private void createPaymentAuthRequest(
            final FragmentActivity activity,
            final VenmoRequest request,
            final Configuration configuration,
            Authorization authorization,
            final String venmoProfileId,
            @Nullable final String paymentContextId,
            VenmoAuthChallengeCallback callback
    ) {
        boolean isClientTokenAuth = (authorization instanceof ClientToken);
        boolean shouldVault = request.getShouldVault() && isClientTokenAuth;
        sharedPrefsWriter.persistVenmoVaultOption(activity, shouldVault);
        VenmoAuthChallenge authChallenge =
                new VenmoAuthChallenge(configuration, venmoProfileId, paymentContextId,
                        braintreeClient.getSessionId(), braintreeClient.getIntegrationType());
        callback.onVenmoAuthChallenge(authChallenge, null);
        braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.started");
    }

    /**
     * After successfully authenticating a Venmo user account via {@link 
     * VenmoClient#createPaymentAuthRequest(FragmentActivity, VenmoRequest, VenmoAuthChallengeCallback)},
     * this method should be invoked to tokenize the account to retrieve a
     * {@link VenmoAccountNonce}.
     * 
     * @param venmoAuthChallengeResult the result of {@link VenmoLauncher#launch(VenmoAuthChallenge)}
     * @param callback a {@link VenmoResultCallback} to receive a {@link VenmoAccountNonce} or 
     *                 error from Venmo tokenization
     */
    public void tokenize(@NonNull final VenmoAuthChallengeResult venmoAuthChallengeResult,
                         @NonNull VenmoResultCallback callback) {
        if (venmoAuthChallengeResult.getError() == null) {
            braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.success");

            braintreeClient.getAuthorization((authorization, authError) -> {
                if (authorization != null) {
                    final boolean isClientTokenAuth = (authorization instanceof ClientToken);

                    String paymentContextId = venmoAuthChallengeResult.getPaymentContextId();
                    if (paymentContextId != null) {
                        venmoApi.createNonceFromPaymentContext(paymentContextId,
                                (nonce, error) -> {
                                    if (nonce != null) {
                                        boolean shouldVault =
                                                sharedPrefsWriter.getVenmoVaultOption(
                                                        braintreeClient.getApplicationContext());
                                        if (shouldVault && isClientTokenAuth) {
                                            vaultVenmoAccountNonce(nonce.getString(),
                                                    (venmoAccountNonce, vaultError) -> {
                                                        if (venmoAccountNonce != null) {
                                                            callback.onResult(
                                                                    venmoAccountNonce,
                                                                    null);
                                                        } else if (vaultError != null) {
                                                            callback.onResult(null, vaultError);
                                                        }
                                                    });
                                        } else {
                                            braintreeClient.sendAnalyticsEvent(
                                                    "pay-with-venmo.app-switch.failure");
                                            callback.onResult(nonce, null);
                                        }
                                    } else {
                                        braintreeClient.sendAnalyticsEvent(
                                                "pay-with-venmo.app-switch.failure");
                                        callback.onResult(null, error);
                                    }
                                });
                    } else {
                        String nonce = venmoAuthChallengeResult.getVenmoAccountNonce();

                        boolean shouldVault = sharedPrefsWriter.getVenmoVaultOption(
                                braintreeClient.getApplicationContext());
                        if (shouldVault && isClientTokenAuth) {
                            vaultVenmoAccountNonce(nonce, (venmoAccountNonce, error) -> {
                                if (venmoAccountNonce != null) {
                                    callback.onResult(venmoAccountNonce, null);
                                } else if (error != null) {
                                    callback.onResult(null, error);
                                }
                            });
                        } else {
                            String venmoUsername = venmoAuthChallengeResult.getVenmoUsername();
                            VenmoAccountNonce venmoAccountNonce =
                                    new VenmoAccountNonce(nonce, venmoUsername, false);
                            callback.onResult(venmoAccountNonce, null);
                        }

                    }
                } else if (authError != null) {
                    callback.onResult(null, authError);
                }
            });

        } else if (venmoAuthChallengeResult.getError() != null) {
            if (venmoAuthChallengeResult.getError() instanceof UserCanceledException) {
                braintreeClient.sendAnalyticsEvent("pay-with-venmo.app-switch.canceled");
            }
            callback.onResult(null, venmoAuthChallengeResult.getError());
        }
    }

    private void vaultVenmoAccountNonce(String nonce, final VenmoResultCallback callback) {
        venmoApi.vaultVenmoAccountNonce(nonce, (venmoAccountNonce, error) -> {
            if (venmoAccountNonce != null) {
                braintreeClient.sendAnalyticsEvent("pay-with-venmo.vault.success");
            } else {
                braintreeClient.sendAnalyticsEvent("pay-with-venmo.vault.failed");
            }
            callback.onResult(venmoAccountNonce, error);
        });
    }

    /**
     * Check if Venmo app switch is available.
     *
     * @param context Application Context
     * @return true if the Venmo app is installed, false otherwise
     */
    public boolean isVenmoAppSwitchAvailable(@NonNull Context context) {
        return deviceInspector.isVenmoAppSwitchAvailable(context);
    }

    /**
     * Before starting the Venmo flow, use this method to check whether Venmo is supported and set
     * up on the device. When the callback is called with {@code true}, show the Venmo button. When
     * it is called with {@code false}, display other checkout options.
     *
     * @param context  Android Context
     * @param callback {@link VenmoIsReadyToPayCallback}
     */
    public void isReadyToPay(final Context context, final VenmoIsReadyToPayCallback callback) {
        braintreeClient.getConfiguration((configuration, configError) -> {
            if (configuration != null) {
                boolean result =
                        configuration.isVenmoEnabled() && isVenmoAppSwitchAvailable(context);
                callback.onResult(result, null);
            } else {
                callback.onResult(false, configError);
            }
        });
    }
}
