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
 * What happened to a record, as far as the sync feed is concerned.
 * <p>
 * Core OpenMRS never physically deletes clinical rows - it voids them - so {@link #DELETE} means
 * "the record is no longer active", not "the row is gone". A client that receives a delete for a
 * record it has never seen should simply ignore it, which is what makes a delete idempotent.
 */
public enum ChangeOperation {
	
	CREATE("CREATE"),
	
	UPDATE("UPDATE"),
	
	DELETE("DELETE");
	
	private final String wireName;
	
	ChangeOperation(String wireName) {
		this.wireName = wireName;
	}
	
	public String getWireName() {
		return wireName;
	}
	
	public static ChangeOperation fromWireName(String wireName) {
		if (wireName == null) {
			return null;
		}
		for (ChangeOperation op : values()) {
			if (op.wireName.equalsIgnoreCase(wireName.trim())) {
				return op;
			}
		}
		return null;
	}
	
	/**
	 * Maps a core event action name to a sync operation. RETIRE/UNRETIRE are not clinical events and
	 * return null so the listener can ignore them.
	 *
	 * @param actionName the value of the event message's {@code action} property
	 * @return the sync operation, or null when the action is not one the feed carries
	 */
	public static ChangeOperation fromEventAction(String actionName) {
		if (actionName == null) {
			return null;
		}
		String action = actionName.trim().toUpperCase();
		if ("CREATED".equals(action)) {
			return CREATE;
		}
		if ("UPDATED".equals(action) || "UNVOIDED".equals(action)) {
			return UPDATE;
		}
		if ("VOIDED".equals(action) || "DELETED".equals(action) || "PURGED".equals(action)) {
			return DELETE;
		}
		return null;
	}
}
