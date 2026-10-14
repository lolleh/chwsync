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

/**
 * The record types that travel over the sync feed.
 * <p>
 * The wire name is deliberately lowercase and stable: mobile clients key their local tables off it,
 * so it must not change even if the Java enum constant does.
 * <p>
 * {@link #isCoreSchema()} marks the types that live in OpenMRS core and are captured from core
 * events; the others are backed by this module's own tables.
 */
public enum SyncEntityType {
	
	/** A patient with its names, addresses, identifiers and attributes folded into one payload. */
	PATIENT("patient", true),
	
	/** A visit, the container a CHW opens when they see someone at their home. */
	VISIT("visit", true),
	
	/** An encounter with its observations folded in. */
	ENCOUNTER("encounter", true),
	
	/** A household, a module-owned grouping of patients. */
	HOUSEHOLD("household", false),
	
	/** A scheduled follow-up, module-owned so no dependency on the appointments module is needed. */
	APPOINTMENT("appointment", false),
	
	/** A referral out to a facility, module-owned. */
	REFERRAL("referral", false);
	
	private final String wireName;
	
	private final boolean coreSchema;
	
	SyncEntityType(String wireName, boolean coreSchema) {
		this.wireName = wireName;
		this.coreSchema = coreSchema;
	}
	
	/**
	 * @return the lowercase name used on the wire and as the directory part of a sync topic
	 */
	public String getWireName() {
		return wireName;
	}
	
	/**
	 * @return true when the record is stored in OpenMRS core tables and captured from core events
	 */
	public boolean isCoreSchema() {
		return coreSchema;
	}
	
	/**
	 * Resolves a wire name back to its enum, ignoring case.
	 *
	 * @param wireName the lowercase name as it appears on the wire
	 * @return the matching type, or null if there is none
	 */
	public static SyncEntityType fromWireName(String wireName) {
		if (wireName == null) {
			return null;
		}
		for (SyncEntityType type : values()) {
			if (type.wireName.equalsIgnoreCase(wireName.trim())) {
				return type;
			}
		}
		return null;
	}
}
