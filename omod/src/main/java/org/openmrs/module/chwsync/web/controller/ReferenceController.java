/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptSearchResult;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.VisitType;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reference data a client needs to build a valid record without guessing.
 * <p>
 * OpenMRS will not accept an identifier, a visit or an encounter without a location and a type, and
 * those are all server-assigned uuids. A device cannot invent them, so it fetches the small set
 * once, caches it, and can then work entirely offline. Only the coded concepts a CHW might record
 * are fetched lazily, by search, because the full dictionary is far too large to ship to a phone.
 */
@RestController
@RequestMapping(ChwSyncConstants.REST_NAMESPACE + "/reference")
public class ReferenceController extends BaseSyncController {
	
	/** Locations and the identifier, visit and encounter types a form can offer. */
	@RequestMapping(value = "", method = RequestMethod.GET)
	public Map<String, Object> reference(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization) {
		return asDevice(authorization, new DeviceWork<Map<String, Object>>() {
			
			@Override
			public Map<String, Object> run() {
				Map<String, Object> result = new LinkedHashMap<String, Object>();
				
				List<Map<String, Object>> locations = new ArrayList<Map<String, Object>>();
				for (Location location : Context.getLocationService().getAllLocations(false)) {
					locations.add(named(location.getUuid(), location.getName()));
				}
				result.put("locations", locations);
				
				List<Map<String, Object>> identifierTypes = new ArrayList<Map<String, Object>>();
				for (PatientIdentifierType type : Context.getPatientService().getAllPatientIdentifierTypes(false)) {
					Map<String, Object> row = named(type.getUuid(), type.getName());
					row.put("format", type.getFormat());
					row.put("required", type.getRequired());
					identifierTypes.add(row);
				}
				result.put("identifierTypes", identifierTypes);
				
				List<Map<String, Object>> visitTypes = new ArrayList<Map<String, Object>>();
				for (VisitType type : Context.getVisitService().getAllVisitTypes(false)) {
					visitTypes.add(named(type.getUuid(), type.getName()));
				}
				result.put("visitTypes", visitTypes);
				
				List<Map<String, Object>> encounterTypes = new ArrayList<Map<String, Object>>();
				for (EncounterType type : Context.getEncounterService().getAllEncounterTypes(false)) {
					encounterTypes.add(named(type.getUuid(), type.getName()));
				}
				result.put("encounterTypes", encounterTypes);
				
				result.put("provider", currentProvider());
				return result;
			}
		});
	}
	
	/**
	 * Searches the concept dictionary, for the coded answers a vitals or diagnosis form needs. The
	 * query is required: without one this would return the catalog.
	 */
	@RequestMapping(value = "/concepts", method = RequestMethod.GET)
	public List<Map<String, Object>> concepts(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization,
	        @RequestParam("q") final String query) {
		if (StringUtils.isBlank(query) || query.trim().length() < 2) {
			throw new IllegalArgumentException("Enter at least two characters to search concepts.");
		}
		return asDevice(authorization, new DeviceWork<List<Map<String, Object>>>() {
			
			@Override
			public List<Map<String, Object>> run() {
				List<ConceptSearchResult> matches = Context.getConceptService().getConcepts(query.trim(),
				    Context.getLocale(), false);
				List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
				int limit = 25;
				for (ConceptSearchResult match : matches) {
					Concept concept = match.getConcept();
					if (concept == null || concept.getRetired()) {
						continue;
					}
					Map<String, Object> row = named(concept.getUuid(), concept.getName(Context.getLocale()).getName());
					if (concept.getDatatype() != null) {
						row.put("datatype", concept.getDatatype().getHl7Abbreviation());
					}
					if (concept.getConceptClass() != null) {
						row.put("conceptClass", concept.getConceptClass().getName());
					}
					result.add(row);
					if (result.size() >= limit) {
						break;
					}
				}
				return result;
			}
		});
	}
	
	private static Map<String, Object> named(String uuid, String name) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("uuid", uuid);
		row.put("name", name);
		return row;
	}
	
	private static Map<String, Object> currentProvider() {
		User user = Context.getAuthenticatedUser();
		if (user == null || user.getPerson() == null) {
			return null;
		}
		for (Provider provider : Context.getProviderService().getProvidersByPerson(user.getPerson(), false)) {
			if (provider != null && !provider.getRetired()) {
				return named(provider.getUuid(), provider.getName());
			}
		}
		return null;
	}
}
