package com.miniproject.plato.modules.employee;

public enum EmployeeRole {
    MANAGER,   // full access to restaurant settings and reports
    CHEF,      // sees kitchen orders, updates order item status
    WAITER,    // takes and manages orders at the table
    CASHIER    // processes payments and closes sessions
}
