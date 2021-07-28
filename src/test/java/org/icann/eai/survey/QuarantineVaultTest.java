package org.icann.eai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuarantineVaultTest {

    @Test
    public void test() throws Exception {
        QuarantineVault vault = new QuarantineVault();
        vault.setPeriod(100);
        vault.setTimeout(1000);
        vault.setExpire(3000);
        vault.setCidr4(24);
        vault.setCidr6(48);
        vault.start();

        assertTrue(vault.lock("10.0.0.1"));
        assertFalse(vault.lock("10.0.0.1"));
        assertFalse(vault.lock("10.0.0.2"));
        assertFalse(vault.lock("10.0.0.20"));
        assertTrue(vault.lock("10.0.1.1"));
        assertTrue(vault.lock("10.0.2.10"));
        assertFalse(vault.lock("10.0.1.1"));
        assertFalse(vault.lock("10.0.1.2"));
        assertFalse(vault.lock("10.0.2.10"));
        assertFalse(vault.lock("10.0.2.20"));

        assertTrue(vault.lock("2001:db8::1"));
        assertFalse(vault.lock("2001:db8::1"));
        assertFalse(vault.lock("2001:db8::2"));
        assertFalse(vault.lock("2001:db8:0:FF::1"));
        assertTrue(vault.lock("2001:db8:1::1"));
        assertFalse(vault.lock("2001:db8:1::2"));
        assertTrue(vault.lock("2001:db8:2::A"));
        assertFalse(vault.lock("2001:db8:2::B"));

        vault.unlock("10.0.0.1");
        assertFalse(vault.lock("10.0.0.1"));
        Thread.sleep(1100);
        assertTrue(vault.lock("10.0.0.1"));

        Thread.sleep(2100);
        assertTrue(vault.lock("10.0.1.1"));
        assertTrue(vault.lock("30.0.2.1"));
    }
}