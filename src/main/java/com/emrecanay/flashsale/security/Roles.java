package com.emrecanay.flashsale.security;

/**
 * Realm roles issued by Keycloak. Accounts get {@link #CUSTOMER} when they sign up; the other roles
 * are assigned by an administrator in Keycloak.
 */
public final class Roles {

    /** Places, pays for and cancels their own orders. */
    public static final String CUSTOMER = "customer";

    /** Creates products and adds stock. */
    public static final String CATALOG_MANAGER = "catalog-manager";

    /** Reads metrics through the actuator. */
    public static final String OPERATOR = "operator";

    private Roles() {
    }
}
