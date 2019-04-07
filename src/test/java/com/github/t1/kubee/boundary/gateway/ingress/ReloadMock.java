package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.boundary.gateway.ingress.IngressReloader.Reload;

public class ReloadMock implements Reload {
    static void reset() {
        calls = 0;
        error = null;
    }

    static int calls = 0;
    static String error = null;

    @Override public String reload() {
        calls++;
        return error;
    }
}
