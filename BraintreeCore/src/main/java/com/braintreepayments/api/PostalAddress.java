package com.braintreepayments.api;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Java object representing a postal address
 */
public class PostalAddress implements Parcelable {

    private String recipientName;
    private String phoneNumber;
    private String streetAddress;
    private String extendedAddress;
    private String locality;
    private String region;
    private String postalCode;
    private String sortingCode;
    private String countryCodeAlpha2;

    public PostalAddress() {
    }

    public void setRecipientName(@Nullable String name) {
        recipientName = name;
    }

    public void setPhoneNumber(@Nullable String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setStreetAddress(@Nullable String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public void setExtendedAddress(@Nullable String extendedAddress) {
        this.extendedAddress = extendedAddress;
    }

    public void setLocality(@Nullable String locality) {
        this.locality = locality;
    }

    public void setRegion(@Nullable String region) {
        this.region = region;
    }

    public void setPostalCode(@Nullable String postalCode) {
        this.postalCode = postalCode;
    }

    public void setSortingCode(@Nullable String sortingCode) {
        this.sortingCode = sortingCode;
    }

    public void setCountryCodeAlpha2(@Nullable String countryCodeAlpha2) {
        this.countryCodeAlpha2 = countryCodeAlpha2;
    }

    @Nullable
    public String getRecipientName() {
        return recipientName;
    }

    @Nullable
    public String getPhoneNumber() {
        return phoneNumber;
    }

    @Nullable
    public String getStreetAddress() {
        return streetAddress;
    }

    @Nullable
    public String getExtendedAddress() {
        return extendedAddress;
    }

    @Nullable
    public String getLocality() {
        return locality;
    }

    @Nullable
    public String getRegion() {
        return region;
    }

    @Nullable
    public String getPostalCode() {
        return postalCode;
    }

    @Nullable
    public String getSortingCode() {
        return sortingCode;
    }

    @Nullable
    public String getCountryCodeAlpha2() {
        return countryCodeAlpha2;
    }

    /**
     * A {@link PostalAddress} is considered empty if it does not have a country code.
     *
     * @return {@code true} if the country code is {@code null} or empty, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return TextUtils.isEmpty(countryCodeAlpha2);
    }

    @Override
    public String toString() {
        return String.format("%s\n%s\n%s\n%s, %s\n%s %s", recipientName, streetAddress,
                extendedAddress, locality, region, postalCode, countryCodeAlpha2);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(streetAddress);
        dest.writeString(extendedAddress);
        dest.writeString(locality);
        dest.writeString(region);
        dest.writeString(postalCode);
        dest.writeString(countryCodeAlpha2);
        dest.writeString(recipientName);
        dest.writeString(phoneNumber);
        dest.writeString(sortingCode);
    }

    private PostalAddress(Parcel in) {
        streetAddress = in.readString();
        extendedAddress = in.readString();
        locality = in.readString();
        region = in.readString();
        postalCode = in.readString();
        countryCodeAlpha2 = in.readString();
        recipientName = in.readString();
        phoneNumber = in.readString();
        sortingCode = in.readString();
    }

    public static final Creator<PostalAddress> CREATOR = new Creator<PostalAddress>() {
        public PostalAddress createFromParcel(Parcel source) {
            return new PostalAddress(source);
        }

        public PostalAddress[] newArray(int size) {
            return new PostalAddress[size];
        }
    };
}
