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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Small, dependency-free helpers for moving dates and lists in and out of the sync payloads.
 * <p>
 * The wire trims every timestamp to the second. Sub-second precision would make the per-record
 * {@code version} - the value a client compares to decide whether its copy is stale - differ
 * between two reads of a row that never changed, and would give no benefit for clinical scheduling.
 */
public final class Json {
	
	private static final String DATE_FORMAT = "yyyy-MM-dd";
	
	private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
	
	private Json() {
		// static utility class
	}
	
	public static String formatDate(Date date) {
		if (date == null) {
			return null;
		}
		SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
		format.setTimeZone(TimeZone.getDefault());
		return format.format(date);
	}
	
	public static Date parseDate(String text) {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		String value = text.trim();
		try {
			if (value.length() == DATE_FORMAT.length()) {
				return new SimpleDateFormat(DATE_FORMAT).parse(value);
			}
			return new SimpleDateFormat(DATETIME_FORMAT).parse(truncateToSeconds(value));
		}
		catch (ParseException e) {
			throw new IllegalArgumentException("Unrecognised date: " + text, e);
		}
	}
	
	public static String formatDateTime(Date date) {
		if (date == null) {
			return null;
		}
		SimpleDateFormat format = new SimpleDateFormat(DATETIME_FORMAT);
		format.setTimeZone(TimeZone.getDefault());
		return format.format(date);
	}
	
	/**
	 * The version a client compares against: the instant a record last changed, to the second, as epoch
	 * milliseconds. Two snapshots of an unchanged row always produce the same value.
	 */
	public static Long version(Date lastChanged) {
		if (lastChanged == null) {
			return null;
		}
		return Long.valueOf((lastChanged.getTime() / 1000L) * 1000L);
	}
	
	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> asMapList(Object value) {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		if (value instanceof List) {
			for (Object item : (List<Object>) value) {
				if (item instanceof Map) {
					result.add((Map<String, Object>) item);
				}
			}
		}
		return result;
	}
	
	public static Map<String, Object> map() {
		return new LinkedHashMap<String, Object>();
	}
	
	public static String string(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		return value == null ? null : value.toString();
	}
	
	public static Integer integer(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Integer.valueOf(((Number) value).intValue());
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : Integer.valueOf((int) Double.parseDouble(text));
	}
	
	public static Double number(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Double.valueOf(((Number) value).doubleValue());
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : Double.valueOf(text);
	}
	
	public static Boolean bool(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		return Boolean.valueOf(Boolean.parseBoolean(value.toString().trim()));
	}
	
	private static String truncateToSeconds(String value) {
		// Accept "2026-01-02T03:04:05" and also a trailing 'Z' or offset by dropping what follows the
		// seconds; the wire never carries sub-second precision, so anything after them is noise.
		if (value.length() > DATETIME_FORMAT.length()) {
			return value.substring(0, DATETIME_FORMAT.length());
		}
		return value;
	}
}
