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

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.junit.Test;

/**
 * The wire helpers are small but load-bearing: the same {@link Json#version(Date)} value decides
 * whether a client sees a row as changed, so a test that pins its rounding is what stops a future
 * "tidy up" from making every record look edited.
 */
public class JsonTest {
	
	@Test
	public void version_shouldTruncateToWholeSeconds() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(2026, Calendar.JANUARY, 2, 3, 4, 5);
		calendar.set(Calendar.MILLISECOND, 750);
		Long version = Json.version(calendar.getTime());
		assertEquals(Long.valueOf((calendar.getTimeInMillis() / 1000L) * 1000L), version);
		assertEquals(Long.valueOf(0L), Long.valueOf(version.longValue() % 1000L));
	}
	
	@Test
	public void version_shouldBeNullForNullDate() {
		assertNull(Json.version(null));
	}
	
	@Test
	public void version_shouldBeStableForTwoReadsOfTheSameRow() {
		Date moment = new Date(1_700_000_000_456L);
		assertEquals(Json.version(moment), Json.version(new Date(moment.getTime())));
	}
	
	@Test
	public void formatDateTimeShouldRoundTripThroughParseDate() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(2026, Calendar.MARCH, 14, 9, 26, 53);
		calendar.set(Calendar.MILLISECOND, 0);
		String text = Json.formatDateTime(calendar.getTime());
		assertEquals("2026-03-14T09:26:53", text);
		assertEquals(calendar.getTime(), Json.parseDate(text));
	}
	
	@Test
	public void parseDateShouldAcceptTrailingZoneOrFraction() {
		Date parsed = Json.parseDate("2026-03-14T09:26:53.999Z");
		assertEquals(Json.parseDate("2026-03-14T09:26:53"), parsed);
	}
	
	@Test
	public void mapAccessorsShouldCoerceStringlyValues() {
		Map<String, Object> map = Json.map();
		map.put("count", "42");
		map.put("amount", "3.5");
		map.put("flag", "true");
		map.put("name", "Grace");
		assertEquals(Integer.valueOf(42), Json.integer(map, "count"));
		assertEquals(Double.valueOf(3.5), Json.number(map, "amount"));
		assertEquals(Boolean.TRUE, Json.bool(map, "flag"));
		assertEquals("Grace", Json.string(map, "name"));
		assertNull(Json.integer(map, "missing"));
	}
}
