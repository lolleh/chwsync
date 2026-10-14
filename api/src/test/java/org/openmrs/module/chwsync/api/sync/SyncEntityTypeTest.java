/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The wire names are a contract with clients that already shipped, so these tests exist to make a
 * rename fail loudly instead of breaking phones in the field.
 */
public class SyncEntityTypeTest {
	
	@Test
	public void wireNamesShouldBeTheDocumentedLowercaseTokens() {
		assertEquals("patient", SyncEntityType.PATIENT.getWireName());
		assertEquals("visit", SyncEntityType.VISIT.getWireName());
		assertEquals("encounter", SyncEntityType.ENCOUNTER.getWireName());
		assertEquals("household", SyncEntityType.HOUSEHOLD.getWireName());
		assertEquals("appointment", SyncEntityType.APPOINTMENT.getWireName());
		assertEquals("referral", SyncEntityType.REFERRAL.getWireName());
	}
	
	@Test
	public void fromWireNameShouldIgnoreCaseAndWhitespace() {
		assertEquals(SyncEntityType.PATIENT, SyncEntityType.fromWireName(" Patient "));
		assertEquals(SyncEntityType.ENCOUNTER, SyncEntityType.fromWireName("ENCOUNTER"));
		assertNull(SyncEntityType.fromWireName("unknown"));
		assertNull(SyncEntityType.fromWireName(null));
	}
	
	@Test
	public void coreSchemaFlagsShouldMatchWhereTheRecordLives() {
		assertTrue(SyncEntityType.PATIENT.isCoreSchema());
		assertTrue(SyncEntityType.VISIT.isCoreSchema());
		assertTrue(SyncEntityType.ENCOUNTER.isCoreSchema());
		assertFalse(SyncEntityType.HOUSEHOLD.isCoreSchema());
		assertFalse(SyncEntityType.APPOINTMENT.isCoreSchema());
		assertFalse(SyncEntityType.REFERRAL.isCoreSchema());
	}
}
