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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.api.sync.Json;

/**
 * Serialises an {@link Encounter} with its observations folded in, and applies one back.
 * <p>
 * Observations are never synced on their own. An obs only means something next to the encounter
 * that recorded it and the patient it is about, and a CHW's device writes the two together; keeping
 * them in one payload means a vitals form arrives complete or not at all, rather than as a bare
 * blood-pressure reading with nowhere to hang.
 * <p>
 * Complex obs (images, documents) are not carried. A field device has neither the bandwidth nor the
 * storage for them, and a truncated image is worse than none; the codec omits them and leaves the
 * value on the server.
 */
public final class EncounterCodec {
	
	private EncounterCodec() {
	}
	
	// ---------------------------------------------------------------- serialize
	
	public static Map<String, Object> toMap(Encounter encounter) {
		Map<String, Object> map = Json.map();
		map.put("uuid", encounter.getUuid());
		map.put("version", Json.version(lastChanged(encounter)));
		map.put("voided", Boolean.valueOf(encounter.getVoided()));
		map.put("patient", encounter.getPatient() == null ? null : encounter.getPatient().getUuid());
		map.put("encounterType", encounter.getEncounterType() == null ? null : encounter.getEncounterType().getUuid());
		map.put("location", encounter.getLocation() == null ? null : encounter.getLocation().getUuid());
		map.put("form", encounter.getForm() == null ? null : encounter.getForm().getUuid());
		map.put("visit", encounter.getVisit() == null ? null : encounter.getVisit().getUuid());
		map.put("encounterDatetime", Json.formatDateTime(encounter.getEncounterDatetime()));
		
		List<Map<String, Object>> providers = new ArrayList<Map<String, Object>>();
		for (EncounterProvider provider : encounter.getActiveEncounterProviders()) {
			if (provider.getProvider() == null) {
				continue;
			}
			Map<String, Object> row = Json.map();
			row.put("provider", provider.getProvider().getUuid());
			row.put("role", provider.getEncounterRole() == null ? null : provider.getEncounterRole().getUuid());
			providers.add(row);
		}
		map.put("providers", providers);
		
		List<Map<String, Object>> obs = new ArrayList<Map<String, Object>>();
		for (Obs observation : encounter.getObsAtTopLevel(false)) {
			obs.add(obsToMap(observation));
		}
		map.put("obs", obs);
		return map;
	}
	
	private static Map<String, Object> obsToMap(Obs obs) {
		Map<String, Object> map = Json.map();
		map.put("uuid", obs.getUuid());
		map.put("concept", obs.getConcept() == null ? null : obs.getConcept().getUuid());
		map.put("obsDatetime", Json.formatDateTime(obs.getObsDatetime()));
		map.put("person", obs.getPerson() == null ? null : obs.getPerson().getUuid());
		map.put("location", obs.getLocation() == null ? null : obs.getLocation().getUuid());
		map.put("comment", obs.getComment());
		map.put("accessionNumber", obs.getAccessionNumber());
		map.put("valueNumeric", obs.getValueNumeric());
		map.put("valueText", obs.getValueText());
		map.put("valueCoded", obs.getValueCoded() == null ? null : obs.getValueCoded().getUuid());
		map.put("valueDatetime", Json.formatDateTime(obs.getValueDatetime()));
		map.put("valueBoolean", obs.getValueBoolean());
		map.put("valueDrug", obs.getValueDrug() == null ? null : obs.getValueDrug().getUuid());
		map.put("voided", Boolean.valueOf(obs.getVoided()));
		if (obs.getGroupMembers(false) != null && !obs.getGroupMembers(false).isEmpty()) {
			List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
			for (Obs member : obs.getGroupMembers(false)) {
				members.add(obsToMap(member));
			}
			map.put("groupMembers", members);
		}
		return map;
	}
	
	/**
	 * @return the encounter's sync version
	 */
	public static Long version(Encounter encounter) {
		return Json.version(lastChanged(encounter));
	}
	
	private static Date lastChanged(Encounter encounter) {
		Date last = encounter.getDateChanged() != null ? encounter.getDateChanged() : encounter.getDateCreated();
		for (Obs obs : encounter.getObsAtTopLevel(true)) {
			last = later(last, obs.getDateChanged());
			last = later(last, obs.getDateCreated());
		}
		return last;
	}
	
	private static Date later(Date a, Date b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.after(b) ? a : b;
	}
	
	// ---------------------------------------------------------------- apply
	
	public static void apply(Encounter encounter, Map<String, Object> payload, User user) {
		String uuid = Json.string(payload, "uuid");
		if (encounter.getUuid() == null && uuid != null) {
			encounter.setUuid(uuid);
		}
		Patient patient = patient(Json.string(payload, "patient"));
		if (patient != null) {
			encounter.setPatient(patient);
		}
		EncounterType type = encounterType(Json.string(payload, "encounterType"));
		if (type != null) {
			encounter.setEncounterType(type);
		}
		Location location = location(Json.string(payload, "location"));
		if (location != null) {
			encounter.setLocation(location);
		}
		Form form = form(Json.string(payload, "form"));
		if (form != null) {
			encounter.setForm(form);
		}
		Visit visit = visit(Json.string(payload, "visit"));
		if (visit != null) {
			encounter.setVisit(visit);
		}
		Date datetime = Json.parseDate(Json.string(payload, "encounterDatetime"));
		if (datetime != null) {
			encounter.setEncounterDatetime(datetime);
		}
		
		for (Map<String, Object> row : Json.asMapList(payload.get("providers"))) {
			Provider provider = provider(Json.string(row, "provider"));
			if (provider == null) {
				continue;
			}
			EncounterRole role = encounterRole(Json.string(row, "role"));
			if (role == null) {
				continue;
			}
			encounter.addProvider(role, provider);
		}
		
		for (Map<String, Object> row : Json.asMapList(payload.get("obs"))) {
			Obs obs = applyObs(row, encounter, patient, user);
			if (obs != null) {
				encounter.addObs(obs);
			}
		}
	}
	
	private static Obs applyObs(Map<String, Object> payload, Encounter encounter, Patient patient, User user) {
		Concept concept = concept(Json.string(payload, "concept"));
		if (concept == null) {
			return null;
		}
		Obs obs = new Obs();
		String uuid = Json.string(payload, "uuid");
		if (uuid != null) {
			obs.setUuid(uuid);
		}
		obs.setConcept(concept);
		obs.setPerson(patient != null ? patient : encounter.getPatient());
		obs.setEncounter(encounter);
		Date when = Json.parseDate(Json.string(payload, "obsDatetime"));
		obs.setObsDatetime(when != null ? when : encounter.getEncounterDatetime());
		Person person = person(Json.string(payload, "person"));
		if (person != null) {
			obs.setPerson(person);
		}
		Location location = location(Json.string(payload, "location"));
		if (location != null) {
			obs.setLocation(location);
		}
		obs.setComment(Json.string(payload, "comment"));
		obs.setAccessionNumber(Json.string(payload, "accessionNumber"));
		Double numeric = Json.number(payload, "valueNumeric");
		if (numeric != null) {
			obs.setValueNumeric(numeric);
		}
		String text = Json.string(payload, "valueText");
		if (text != null) {
			obs.setValueText(text);
		}
		Concept coded = concept(Json.string(payload, "valueCoded"));
		if (coded != null) {
			obs.setValueCoded(coded);
		}
		Date valueDatetime = Json.parseDate(Json.string(payload, "valueDatetime"));
		if (valueDatetime != null) {
			obs.setValueDatetime(valueDatetime);
		}
		Boolean valueBoolean = Json.bool(payload, "valueBoolean");
		if (valueBoolean != null) {
			obs.setValueBoolean(valueBoolean);
		}
		Drug drug = drug(Json.string(payload, "valueDrug"));
		if (drug != null) {
			obs.setValueDrug(drug);
		}
		
		List<Map<String, Object>> members = Json.asMapList(payload.get("groupMembers"));
		if (!members.isEmpty()) {
			obs.setGroupMembers(new HashSet<Obs>());
			for (Map<String, Object> member : members) {
				Obs memberObs = applyObs(member, encounter, patient, user);
				if (memberObs != null) {
					memberObs.setObsGroup(obs);
					obs.addGroupMember(memberObs);
				}
			}
		}
		return obs;
	}
	
	private static Concept concept(String uuid) {
		return uuid == null ? null : Context.getConceptService().getConceptByUuid(uuid);
	}
	
	private static Drug drug(String uuid) {
		return uuid == null ? null : Context.getConceptService().getDrugByUuid(uuid);
	}
	
	private static Location location(String uuid) {
		return uuid == null ? null : Context.getLocationService().getLocationByUuid(uuid);
	}
	
	private static Patient patient(String uuid) {
		return uuid == null ? null : Context.getPatientService().getPatientByUuid(uuid);
	}
	
	private static Person person(String uuid) {
		return uuid == null ? null : Context.getPersonService().getPersonByUuid(uuid);
	}
	
	private static EncounterType encounterType(String uuid) {
		return uuid == null ? null : Context.getEncounterService().getEncounterTypeByUuid(uuid);
	}
	
	private static EncounterRole encounterRole(String uuid) {
		return uuid == null ? null : Context.getEncounterService().getEncounterRoleByUuid(uuid);
	}
	
	private static Form form(String uuid) {
		return uuid == null ? null : Context.getFormService().getFormByUuid(uuid);
	}
	
	private static Visit visit(String uuid) {
		return uuid == null ? null : Context.getVisitService().getVisitByUuid(uuid);
	}
	
	private static Provider provider(String uuid) {
		return uuid == null ? null : Context.getProviderService().getProviderByUuid(uuid);
	}
}
