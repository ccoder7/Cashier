package com.getkeepsafe.cashier;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;

import com.getkeepsafe.cashier.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

/**
 * The global entry point for all billing related functionality.
 * <p>
 * There should only be one instance of this class for each Activity that hosts a billing flow
 */
public class Cashier {
    private static HashMap<String, VendorFactory> vendorFactories = new HashMap<>(1);

    private final Activity activity;
    private final Vendor vendor;

    /**
     * Registers a vendor factory for use
     * @param vendorId The vendor's unique ID
     * @param factory The {@link VendorFactory} that will create instances of the {@link Vendor}
     */
    public static void putVendorFactory(String vendorId, VendorFactory factory) {
        if (TextUtils.isEmpty(vendorId)) {
            throw new IllegalArgumentException("Invalid vendor id, null or empty");
        }

        vendorFactories.put(vendorId, factory);
    }

    /**
     * @param vendorId The vendor's unique ID
     * @return the {@link VendorFactory} instance that creates the {@link Vendor} with the given ID
     */
    public static VendorFactory getVendorFactory(String vendorId) {
        if (TextUtils.isEmpty(vendorId)) {
            throw new IllegalArgumentException("Invalid vendor id, null or empty");
        }

        final VendorFactory factory = vendorFactories.get(vendorId);
        if (factory == null) {
            throw new VendorMissingException(vendorId);
        }

        return factory;
    }

    /** Returns a Cashier instance depending on the app installer **/
    public static Builder forInstaller(Activity context) {
        final String installer = context
                .getPackageManager()
                .getInstallerPackageName(context.getPackageName());
        return new Builder(context).forVendor(getVendorFactory(installer).create());
    }

    /** Returns a Cashier instance that sold the given {@link Purchase} **/
    public static Builder forPurchase(Activity context, Purchase purchase) {
        return forProduct(context, purchase.product());
    }

    /** Returns a Cashier instance that sells the given {@link Product} **/
    public static Builder forProduct(Activity context, Product product) {
        return new Builder(context).forVendor(getVendorFactory(product.vendorId()).create());
    }

    /** Returns a product from the given JSON supplied by the vendor it belongs to **/
    public static Product productFromVendor(String json) throws JSONException {
        return productFromVendor(new JSONObject(json));
    }

    /** Returns a product from the given JSON supplied by the vendor it belongs to **/
    public static Product productFromVendor(JSONObject json) throws JSONException {
        final String vendorId = json.getString(Product.KEY_VENDOR_ID);
        final Vendor vendor = getVendorFactory(vendorId).create();
        return vendor.getProductFrom(json);
    }

    /** Returns a purchase from the given JSON supplied by the vendor it belongs to **/
    public static Purchase purchaseFromVendor(String json) throws JSONException {
        return purchaseFromVendor(new JSONObject(json));
    }

    /** Returns a purchase from the given JSON supplied by the vendor it belongs to **/
    public static Purchase purchaseFromVendor(JSONObject json) throws JSONException {
        final String vendorId = json.getString(Product.KEY_VENDOR_ID);
        final Vendor vendor = getVendorFactory(vendorId).create();
        return vendor.getPurchaseFrom(json);
    }

    private Cashier(Activity activity, Vendor vendor) {
        if (activity == null || vendor == null) {
            throw new IllegalArgumentException("Context or vendor is null");
        }
        this.activity = activity;
        this.vendor = vendor;
    }

    /**
     * Initiates a purchase flow
     * @param product The {@link Product} you wish to buy
     * @param listener The {@link PurchaseListener} to handle the result
     */
    public void purchase(Product product, PurchaseListener listener) {
        purchase(product, null, listener);
    }

    /**
     * Initiates a purchase flow
     * @param product The {@link Product} you wish to buy
     * @param developerPayload Your custom payload to pass along to the {@link Vendor}
     * @param listener The {@link PurchaseListener} to handle the result
     */
    public void purchase(final Product product,
                         final String developerPayload,
                         final PurchaseListener listener) {
        vendor.initialize(activity, new Vendor.InitializationListener() {
            @Override
            public void initialized() {
                if (!vendor.available() || !vendor.canPurchase(product)) {
                    listener.failure(product, new Vendor.Error(VendorConstants.PURCHASE_UNAVAILABLE, -1));
                    return;
                }

                final String payload = developerPayload == null ? "" : developerPayload;
                vendor.purchase(activity, product, payload, listener);
            }

            @Override
            public void unavailable() {
                listener.failure(product,
                        new Vendor.Error(VendorConstants.PURCHASE_UNAVAILABLE, -1));
            }
        });
    }

    /**
     * Consumes the given purchase
     * @param purchase The {@link Purchase} to consume. Must not be a subscription
     * @param listener The {@link ConsumeListener} to handle the result
     */
    public void consume(final Purchase purchase, final ConsumeListener listener) {
        if (purchase.product().isSubscription()) {
            throw new IllegalArgumentException("Cannot consume a subscription type!");
        }
        vendor.initialize(this.activity, new Vendor.InitializationListener() {
            @Override
            public void initialized() {
                vendor.consume(activity, purchase, listener);
            }

            @Override
            public void unavailable() {
                listener.failure(purchase,
                        new Vendor.Error(VendorConstants.CONSUME_UNAVAILABLE, -1));
            }
        });
    }

    /**
     * Returns a list of purchased items from the vendor
     * @param listener {@link InventoryListener} to handle the result
     */
    public void getInventory(final InventoryListener listener) {
        getInventory(null, null, listener);
    }

    /**
     * Returns a list of purchased items and specified products from the vendor
     * @param itemSkus A list of {@link Product} skus to query the vendor for
     * @param subSkus A list of subscription {@link Product} skus to query the vendor for
     * @param listener {@link InventoryListener} to handle the result
     */
    public void getInventory(final List<String> itemSkus,
                             final List<String> subSkus,
                             final InventoryListener listener) {
        vendor.initialize(activity, new Vendor.InitializationListener() {
            @Override
            public void initialized() {
                vendor.getInventory(activity, itemSkus, subSkus, listener);
            }

            @Override
            public void unavailable() {
                listener.failure(new Vendor.Error(VendorConstants.INVENTORY_QUERY_UNAVAILABLE, -1));
            }
        });
    }

    /** Returns the vendor ID that this Cashier belongs to **/
    public String vendorId() {
        return vendor.id();
    }

    /** Runs any cleanup functions the {@link Vendor} may need **/
    public void dispose() {
        vendor.dispose(activity);
    }

    /**
     * Handles results from separate activities. Use of this function depends on
     * the {@link Vendor}
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return vendor.onActivityResult(requestCode, resultCode, data);
    }

    public static class Builder {
        private final Activity activity;
        private Vendor vendor;
        private Logger logger;

        public Builder(Activity activity) {
            this.activity = activity;
        }

        public Builder forVendor(Vendor vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder withLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Cashier build() {
            if (logger != null) {
                vendor.setLogger(logger);
            }

            return new Cashier(activity, vendor);
        }
    }
}
