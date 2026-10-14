/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * Shared plumbing for the CHW DAOs.
 * <p>
 * Every module table is touched through native SQL on the core session, never through a mapped
 * entity. A module that adds its own Hibernate mappings has to keep them in step with core's
 * {@code SessionFactory} across OpenMRS versions and has to survive rows written by an older copy
 * of itself; a module that speaks SQL only depends on the columns it created in its own Liquibase
 * changesets, and nothing else.
 * <p>
 * Two portability rules are enforced throughout: booleans are compared against {@code false}/
 * {@code true} (never {@code 0}/{@code 1}, which PostgreSQL rejects), and every value that reaches
 * SQL travels as a bound parameter.
 */
public abstract class AbstractChwSyncDao {
	
	private DbSessionFactory sessionFactory;
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	protected DbSessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	protected Session session() {
		return sessionFactory.getHibernateSessionFactory().getCurrentSession();
	}
	
	/**
	 * Maps one row of a native query result onto a domain object.
	 *
	 * @param <T> the produced type
	 */
	public interface RowMapper<T> {
		
		T mapRow(Object[] row);
	}
	
	protected Query<?> query(String sql, Map<String, Object> params) {
		Query<?> query = session().createNativeQuery(sql);
		if (params != null) {
			for (Map.Entry<String, Object> entry : params.entrySet()) {
				query.setParameter(entry.getKey(), entry.getValue());
			}
		}
		return query;
	}
	
	protected <T> List<T> list(String sql, Map<String, Object> params, RowMapper<T> mapper) {
		return list(sql, params, mapper, -1);
	}
	
	protected <T> List<T> list(String sql, Map<String, Object> params, RowMapper<T> mapper, int limit) {
		Query<?> query = query(sql, params);
		if (limit > 0) {
			query.setMaxResults(limit);
		}
		List<T> result = new ArrayList<T>();
		for (Object row : query.list()) {
			result.add(mapper.mapRow(toArray(row)));
		}
		return result;
	}
	
	protected <T> T unique(String sql, Map<String, Object> params, RowMapper<T> mapper) {
		List<?> rows = query(sql, params).setMaxResults(1).list();
		if (rows.isEmpty()) {
			return null;
		}
		return mapper.mapRow(toArray(rows.get(0)));
	}
	
	protected long count(String sql, Map<String, Object> params) {
		List<?> rows = query(sql, params).list();
		if (rows.isEmpty()) {
			return 0L;
		}
		return asLong(toArray(rows.get(0))[0]);
	}
	
	/**
	 * Runs an update/insert/delete and returns the number of affected rows.
	 */
	protected int execute(String sql, Map<String, Object> params) {
		return query(sql, params).executeUpdate();
	}
	
	protected static Object[] toArray(Object row) {
		if (row instanceof Object[]) {
			return (Object[]) row;
		}
		return new Object[] { row };
	}
	
	// ---------------------------------------------------------------- value coercion
	
	protected static String asString(Object value) {
		return value == null ? null : value.toString();
	}
	
	protected static Integer asInteger(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Integer.valueOf(((Number) value).intValue());
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : Integer.valueOf((int) Double.parseDouble(text));
	}
	
	protected static Long asLongObject(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Long.valueOf(((Number) value).longValue());
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : Long.valueOf((long) Double.parseDouble(text));
	}
	
	protected static long asLong(Object value) {
		Long result = asLongObject(value);
		return result == null ? 0L : result.longValue();
	}
	
	protected static Boolean asBoolean(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof Number) {
			return Boolean.valueOf(((Number) value).intValue() != 0);
		}
		return Boolean.valueOf(Boolean.parseBoolean(value.toString().trim()));
	}
	
	protected static boolean asPrimitiveBoolean(Object value) {
		Boolean result = asBoolean(value);
		return result != null && result.booleanValue();
	}
	
	/**
	 * Coerces a temporal column to {@link Date}. Drivers return {@code java.sql.Date},
	 * {@code Timestamp} or a string, so all three are handled.
	 */
	protected static Date asDate(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Date) {
			return new Date(((Date) value).getTime());
		}
		if (value instanceof Number) {
			return new Date(((Number) value).longValue());
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		if (text.length() == 10 && text.charAt(4) == '-') {
			return java.sql.Date.valueOf(text);
		}
		return java.sql.Timestamp.valueOf(text.replace('T', ' '));
	}
	
	protected static void closeQuietly(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (SQLException ignored) {
				// nothing useful to do while closing metadata
			}
		}
	}
}
