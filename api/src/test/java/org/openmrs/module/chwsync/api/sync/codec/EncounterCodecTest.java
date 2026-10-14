/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.sync.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.module.chwsync.api.sync.Json;

/**
 * Observations travel folded into their encounter, so the contract that matters is that a top-level
 * obs survives the trip with its value and concept intact.
 */
public class EncounterCodecTest {
	
	@Test
	public void toMapShouldFoldTopLevelObsIntoTheEncounter() {
		Patient patient = new Patient();
		patient.setUuid("patient-uuid");
		
		Encounter encounter = new Encounter();
		encounter.setUuid("encounter-uuid");
		encounter.setPatient(patient);
		encounter.setEncounterDatetime(new Date(1_700_000_000_000L));
		
		Concept weight = new Concept();
		weight.setUuid("weight-concept-uuid");
		
		Obs obs = new Obs();
		obs.setUuid("obs-uuid");
		obs.setPerson(patient);
		obs.setConcept(weight);
		obs.setValueNumeric(36.7d);
		obs.setObsDatetime(new Date(1_700_000_000_000L));
		encounter.addObs(obs);
		
		Map<String, Object> map = EncounterCodec.toMap(encounter);
		
		assertEquals("encounter-uuid", map.get("uuid"));
		assertEquals("patient-uuid", map.get("patient"));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> obsRows = (List<Map<String, Object>>) map.get("obs");
		assertEquals(1, obsRows.size());
		assertEquals("weight-concept-uuid", obsRows.get(0).get("concept"));
		assertEquals(Double.valueOf(36.7d), obsRows.get(0).get("valueNumeric"));
		assertEquals(Json.formatDateTime(new Date(1_700_000_000_000L)), obsRows.get(0).get("obsDatetime"));
	}
	
	@Test
	public void toMapShouldOmitVoidedObsAtTheTopLevel() {
		Patient patient = new Patient();
		patient.setUuid("patient-uuid");
		Encounter encounter = new Encounter();
		encounter.setUuid("encounter-uuid");
		encounter.setPatient(patient);
		
		Obs obs = new Obs();
		obs.setConcept(new Concept());
		obs.setValueNumeric(1.0d);
		obs.setVoided(true);
		encounter.addObs(obs);
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> obsRows = (List<Map<String, Object>>) EncounterCodec.toMap(encounter).get("obs");
		assertTrue(obsRows.isEmpty());
	}
}
