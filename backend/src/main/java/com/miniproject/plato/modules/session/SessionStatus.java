package com.miniproject.plato.modules.session;

public enum SessionStatus {
    ACTIVE,     // Session is live; guest can browse, manage cart, place orders
    CLOSED,     // Bill paid; dining visit complete
    EXPIRED
}
