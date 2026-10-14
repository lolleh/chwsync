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
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The mapping from core event actions to sync operations is the point where a facility-side edit
 * either reaches the feed or quietly does not. UNVOIDED mapping to UPDATE and RETIRED mapping to
 * nothing are the two rules most likely to be got wrong.
 */
public class ChangeOperationTest {
	
	@Test
	public void fromEventActionShouldMapClinicalLifecycle() {
		assertEquals(ChangeOperation.CREATE, ChangeOperation.fromEventAction("CREATED"));
		assertEquals(ChangeOperation.UPDATE, ChangeOperation.fromEventAction("UPDATED"));
		assertEquals(ChangeOperation.UPDATE, ChangeOperation.fromEventAction("UNVOIDED"));
		assertEquals(ChangeOperation.DELETE, ChangeOperation.fromEventAction("VOIDED"));
		assertEquals(ChangeOperation.DELETE, ChangeOperation.fromEventAction("PURGED"));
	}
	
	@Test
	public void fromEventActionShouldIgnoreNonClinicalActions() {
		assertNull(ChangeOperation.fromEventAction("RETIRED"));
		assertNull(ChangeOperation.fromEventAction("UNRETIRED"));
		assertNull(ChangeOperation.fromEventAction("SOMETHING_ELSE"));
		assertNull(ChangeOperation.fromEventAction(null));
	}
	
	@Test
	public void fromEventActionShouldBeCaseInsensitive() {
		assertEquals(ChangeOperation.CREATE, ChangeOperation.fromEventAction("created"));
	}
	
	@Test
	public void fromWireNameShouldResolveClientTokens() {
		assertEquals(ChangeOperation.CREATE, ChangeOperation.fromWireName("Create"));
		assertEquals(ChangeOperation.DELETE, ChangeOperation.fromWireName("DELETE"));
		assertNull(ChangeOperation.fromWireName("nonsense"));
	}
}
