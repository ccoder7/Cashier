package com.getkeepsafe.cashier.googleplay;

import android.support.annotation.NonNull;

import com.getkeepsafe.cashier.Product;
import com.getkeepsafe.cashier.utilities.Check;

import org.json.JSONException;
import org.json.JSONObject;

public class GooglePlayProduct extends Product implements GooglePlayConstants.ProductConstants {
    public static GooglePlayProduct of(@NonNull final String skuDetailJson,
                                       final boolean isSubscription) throws JSONException {
        final JSONObject json = new JSONObject(Check.notNull(skuDetailJson, "SKU JSON"));
        final String sku = json.getString(SKU);
        final String price = json.getString(PRICE);
        final String currency = json.getString(CURRENCY);
        final String name = json.getString(NAME);
        final String description = json.getString(DESCRIPTION);
        // TODO: Can this be a long instead?
        final String microsPrice = json.getString(PRICE_MICRO);

        return new GooglePlayProduct(
                sku, price, currency, name, description, isSubscription, Long.parseLong(microsPrice));
    }

    public GooglePlayProduct(
            @NonNull final String sku,
            @NonNull final String price,
            @NonNull final String currency,
            @NonNull final String name,
            @NonNull final String description,
            final boolean isSubscription,
            final long microsPrice) {
        super(sku, price, currency, name, description, isSubscription, microsPrice);
    }
}