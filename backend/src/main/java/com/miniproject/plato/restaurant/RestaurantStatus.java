package com.miniproject.plato.restaurant;

// =========================================================================
// RestaurantStatus — Lifecycle states of a restaurant on the platform.
// -------------------------------------------------------------------------
// ACTIVE:    Visible, accepting orders. Normal state.
// INACTIVE:  Owner turned it off (e.g. closed for the day). Customers
//            cannot scan QR or place orders. Owner can still manage it.
// SUSPENDED: Blocked by Super Admin (policy violation, payment issue).
//            Neither owner nor customers can use it until re-activated.
// =========================================================================
public enum RestaurantStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
