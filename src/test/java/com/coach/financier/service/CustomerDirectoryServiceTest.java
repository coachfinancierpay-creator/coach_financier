package com.coach.financier.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerDirectoryServiceTest {

    @Test
    void normalizesLegacyCustomerIdsAndAssignsAStableAgency() {
        CustomerDirectoryService service = new CustomerDirectoryService();

        CustomerDirectoryService.CustomerProfile profile = service.profile("DEMO001");

        assertEquals("QJA6874", profile.customerId());
        assertEquals("03101", profile.agency().code());
        assertEquals("Agence de Paris", profile.agency().name());
        assertTrue(profile.agency().code().matches("[0-9]{5}"));
    }
}