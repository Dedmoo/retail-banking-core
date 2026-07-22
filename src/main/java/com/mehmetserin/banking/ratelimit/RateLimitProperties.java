package com.mehmetserin.banking.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "banking.ratelimit")
public class RateLimitProperties {

    @NestedConfigurationProperty
    private Bucket transfer = new Bucket();

    public Bucket getTransfer() {
        return transfer;
    }

    public void setTransfer(Bucket transfer) {
        this.transfer = transfer;
    }

    public static class Bucket {
        private int capacity = 10;
        private int refillMinutes = 1;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillMinutes() {
            return refillMinutes;
        }

        public void setRefillMinutes(int refillMinutes) {
            this.refillMinutes = refillMinutes;
        }
    }
}
