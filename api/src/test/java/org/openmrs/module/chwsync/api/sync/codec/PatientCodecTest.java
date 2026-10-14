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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;

/**
 * Serialization runs on every pull, is pure, and needs no database, so it is worth pinning its
 * shape here: a client that has already shipped parses these field names and nothing else.
 */
public class PatientCodecTest {
	
	@Test
	public void toMapShouldFoldPersonNameAddressIdentifierAndAttributeIntoOnePayload() {
		Patient patient = new Patient();
		patient.setUuid("patient-uuid");
		patient.setGender("F");
		patient.setBirthdateEstimated(Boolean.TRUE);
		patient.setDateCreated(new java.util.Date(1_700_000_000_000L));
		
		PersonName name = new PersonName();
		name.setUuid("name-uuid");
		name.setGivenName("Grace");
		name.setFamilyName("Nakato");
		name.setPreferred(true);
		patient.addName(name);
		
		PersonAddress address = new PersonAddress();
		address.setUuid("address-uuid");
		address.setAddress1("Kisoro Road");
		address.setCityVillage("Kisoro");
		patient.addAddress(address);
		
		PatientIdentifierType identifierType = new PatientIdentifierType();
		identifierType.setUuid("identifier-type-uuid");
		PatientIdentifier identifier = new PatientIdentifier();
		identifier.setUuid("identifier-uuid");
		identifier.setIdentifier("CHW-0001");
		identifier.setIdentifierType(identifierType);
		identifier.setPreferred(true);
		patient.addIdentifier(identifier);
		
		PersonAttributeType attributeType = new PersonAttributeType();
		attributeType.setUuid("attribute-type-uuid");
		PersonAttribute attribute = new PersonAttribute(attributeType, "+256700000000");
		patient.addAttribute(attribute);
		
		Map<String, Object> map = PatientCodec.toMap(patient);
		
		assertEquals("patient-uuid", map.get("uuid"));
		assertEquals("F", map.get("gender"));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> names = (List<Map<String, Object>>) map.get("names");
		assertEquals(1, names.size());
		assertEquals("Grace", names.get(0).get("givenName"));
		assertEquals("Nakato", names.get(0).get("familyName"));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> identifiers = (List<Map<String, Object>>) map.get("identifiers");
		assertEquals(1, identifiers.size());
		assertEquals("CHW-0001", identifiers.get(0).get("identifier"));
		assertEquals("identifier-type-uuid", identifiers.get(0).get("identifierType"));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> addresses = (List<Map<String, Object>>) map.get("addresses");
		assertEquals(1, addresses.size());
		assertEquals("Kisoro", addresses.get(0).get("cityVillage"));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> attributes = (List<Map<String, Object>>) map.get("attributes");
		assertTrue(attributes.stream().anyMatch((row) -> "+256700000000".equals(row.get("value"))));
		
		// Version is always present (never null) even for a patient with no audit dates.
		assertNotNull(map.get("version"));
	}
	
	@Test
	public void toMapShouldOmitNothingRatherThanFailOnBarePatient() {
		Patient patient = new Patient();
		patient.setUuid("bare-uuid");
		Map<String, Object> map = PatientCodec.toMap(patient);
		assertEquals("bare-uuid", map.get("uuid"));
		assertFalse(((List<?>) map.get("names")).iterator().hasNext());
	}
}
