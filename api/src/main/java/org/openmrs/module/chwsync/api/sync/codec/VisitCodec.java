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

import java.util.Date;
import java.util.Map;

import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.api.sync.Json;

/**
 * Serialises a {@link Visit} and applies one back.
 * <p>
 * A visit is the container a CHW opens when they see someone at home, so it is the sync record that
 * gives an encounter somewhere to hang. It is intentionally narrow: who, where, which type, and the
 * times it started and stopped.
 */
public final class VisitCodec {
	
	private VisitCodec() {
	}
	
	/**
	 * @return the visit's sync version
	 */
	public static Long version(Visit visit) {
		Date changed = visit.getDateChanged() != null ? visit.getDateChanged() : visit.getDateCreated();
		return Json.version(changed);
	}
	
	public static Map<String, Object> toMap(Visit visit) {
		Map<String, Object> map = Json.map();
		map.put("uuid", visit.getUuid());
		map.put("version", version(visit));
		map.put("voided", Boolean.valueOf(visit.getVoided()));
		map.put("patient", visit.getPatient() == null ? null : visit.getPatient().getUuid());
		map.put("visitType", visit.getVisitType() == null ? null : visit.getVisitType().getUuid());
		map.put("location", visit.getLocation() == null ? null : visit.getLocation().getUuid());
		map.put("indication", visit.getIndication() == null ? null : visit.getIndication().getUuid());
		map.put("startDatetime", Json.formatDateTime(visit.getStartDatetime()));
		map.put("stopDatetime", Json.formatDateTime(visit.getStopDatetime()));
		return map;
	}
	
	public static void apply(Visit visit, Map<String, Object> payload, User user) {
		String uuid = Json.string(payload, "uuid");
		if (visit.getUuid() == null && uuid != null) {
			visit.setUuid(uuid);
		}
		String patientUuid = Json.string(payload, "patient");
		if (patientUuid != null) {
			Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
			if (patient != null) {
				visit.setPatient(patient);
			}
		}
		String visitTypeUuid = Json.string(payload, "visitType");
		if (visitTypeUuid != null) {
			VisitType type = Context.getVisitService().getVisitTypeByUuid(visitTypeUuid);
			if (type != null) {
				visit.setVisitType(type);
			}
		}
		Location location = location(Json.string(payload, "location"));
		if (location != null) {
			visit.setLocation(location);
		}
		Concept indication = concept(Json.string(payload, "indication"));
		if (indication != null) {
			visit.setIndication(indication);
		}
		Date start = Json.parseDate(Json.string(payload, "startDatetime"));
		if (start != null) {
			visit.setStartDatetime(start);
		}
		visit.setStopDatetime(Json.parseDate(Json.string(payload, "stopDatetime")));
	}
	
	private static Concept concept(String uuid) {
		return uuid == null ? null : Context.getConceptService().getConceptByUuid(uuid);
	}
	
	private static Location location(String uuid) {
		return uuid == null ? null : Context.getLocationService().getLocationByUuid(uuid);
	}
}
