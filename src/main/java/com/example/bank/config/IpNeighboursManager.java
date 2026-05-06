package com.example.bank.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class IpNeighboursManager {

    // Initialized to null by default
    private final AtomicReference<String> ipPrevious = new AtomicReference<>(null);
    private final AtomicReference<String> ipNext = new AtomicReference<>(null);

    // Get the current value (can return null)
    public String getPrevIP() {
        return ipPrevious.get();
    }

    // Change the value at runtime
    public void setPrevIP(String newValue) {
        this.ipPrevious.set(newValue);
    }
    public String getNextIP() {
        return ipNext.get();
    }

    // Change the value at runtime
    public void setNextIP(String newValue) {
        this.ipNext.set(newValue);
    }

}