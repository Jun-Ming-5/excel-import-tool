package com.jun.ming.excel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import com.jun.ming.excel.annotation.FieldType;

public class JdbcBatch {

	private JdbcBatch() {

	}

	public static <T> int batchPageInsert(JdbcTemplate jdbcTemplate, Class<T> c, Collection<T> dataList, int pageSize,
			SqlInterface sqlInterface) throws Exception {
		List<T> pageList = new ArrayList<>();
		int total = 0;
		for (T t : dataList) {
			pageList.add(t);
			if (pageList.size() == pageSize) {
				total += batchInsert(jdbcTemplate, c, pageList, sqlInterface);
				pageList.clear();
			}
		}
		if (!pageList.isEmpty()) {
			total += batchInsert(jdbcTemplate, c, pageList, sqlInterface);
		}
		return total;
	}

	private static List<Class<?>> getAllClass(Class<?> c) {
		Stack<Class<?>> stack = new Stack<>();
		Class<?> tmp = c;
		while (tmp != Object.class) {
			stack.add(tmp);
			tmp = tmp.getSuperclass();
		}
		List<Class<?>> classList = new ArrayList<>();
		while (!stack.isEmpty()) {
			classList.add(stack.pop());
		}
		return classList;
	}

	public static <T> int batchInsert(JdbcTemplate jdbcTemplate, Class<T> c, Collection<T> dataList,
			SqlInterface sqlInterface) throws Exception {
		List<Class<?>> allClass = getAllClass(c);
		List<Field> validFields = new ArrayList<>();
		for (Class<?> cc : allClass) {
			List<Field> validField = getValidFields(cc, validFields);
			validFields.addAll(validField);
		}
		Table annotation = c.getAnnotation(javax.persistence.Table.class);
		String table = annotation.name();
		List<FieldValueMap> fieldValues = convertArray(validFields, c, dataList);
		List<String> insertFieldNameList = new ArrayList<>();
		List<String> updateFieldNameList = new ArrayList<>();
		Set<String> uniqueFieldNameList = new HashSet<>();
		for (FieldValueMap f : fieldValues) {
			insertFieldNameList.add(f.fieldName);
			if (3 == f.keyType) {
				updateFieldNameList.add(f.fieldName);
			}
			if (2 == f.keyType) {
				uniqueFieldNameList.add(f.fieldName);
			}
		}

		String sql = createSql(table, uniqueFieldNameList, insertFieldNameList, updateFieldNameList, sqlInterface);
		return jdbcTemplate.execute(new ConnectionCallback<Integer>() {
			@Override
			public Integer doInConnection(Connection connection) throws SQLException, DataAccessException {
				try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
					int fieldCounts = fieldValues.size();
					for (int i = 0; i < fieldCounts; i++) {
						FieldValueMap fvm = fieldValues.get(i);
						prepareStatement.setArray(i + 1, connection.createArrayOf(fvm.jdbcType.getName(), fvm.values));
					}
					return prepareStatement.executeUpdate();
				}
			}
		});
	}

	public static List<Field> getValidFields(Class<?> c, List<Field> existFields) {
		Field[] fields = c.getDeclaredFields();
		List<Field> validFields = new ArrayList<>();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			Embedded emb = field.getAnnotation(javax.persistence.Embedded.class);
			if (emb != null) {
				validFields.add(field);
				continue;
			}
			Column column = field.getAnnotation(javax.persistence.Column.class);
			Id id = field.getAnnotation(javax.persistence.Id.class);
			JoinColumn jColumn = field.getAnnotation(javax.persistence.JoinColumn.class);
			if (column == null && id == null && jColumn == null) {
				continue;
			}
			Field over = null;
			for (Field f : existFields) {
				if (field.getName().equals(f.getName())) {
					// 子类与父类有相同的字段
					over = f;
					break;
				}
			}
			if (over != null) {
				existFields.remove(over);
			}
			validFields.add(field);
		}
		return validFields;
	}

	private static List<FieldValueMap> convertArray(List<Field> allFields, Class<?> c, Collection<?> dataList)
			throws Exception {
		List<FieldValueMap> fieldValues = new ArrayList<>();
		Table table = c.getAnnotation(javax.persistence.Table.class);
		UniqueConstraint[] uniqueConstraints = table.uniqueConstraints();
		List<String> allUniqueFields = new ArrayList<>();
		for (UniqueConstraint uc : uniqueConstraints) {
			String[] columnNames = uc.columnNames();
			allUniqueFields.addAll(Arrays.asList(columnNames));
		}
		for (Field field : allFields) {
			List<Object> oneFieldValues = new ArrayList<>();
			for (Object o : dataList) {
				field.setAccessible(true);
				Object v = field.get(o);
				oneFieldValues.add(v);
			}

			Column column = field.getAnnotation(javax.persistence.Column.class);
			if (column != null) {
				FieldValueMap oneFieldValue = convertOneField(field, oneFieldValues);
				oneFieldValue.fieldName = column.name();
				fieldValues.add(oneFieldValue);
				Id idAnnotation = field.getAnnotation(javax.persistence.Id.class);
				if (idAnnotation != null) {
					oneFieldValue.keyType = 1;
				} else if (column.unique() || allUniqueFields.contains(column.name())) {
					oneFieldValue.keyType = 2;
				} else {
					oneFieldValue.keyType = 3;
				}
				continue;
			}

			JoinColumn foreignKey = field.getAnnotation(javax.persistence.JoinColumn.class);
			if (foreignKey != null) {
				Class<?> foreignClass = field.getType();
				Field[] foreignClassFields = foreignClass.getDeclaredFields();
				for (Field f : foreignClassFields) {
					Id idAnnotation = f.getAnnotation(javax.persistence.Id.class);
					if (idAnnotation != null) {
						List<Object> idFieldValues = new ArrayList<>();
						for (Object o : oneFieldValues) {
							if (null == o) {
								idFieldValues.add(null);
							} else {
								f.setAccessible(true);
								Object v = f.get(o);
								idFieldValues.add(v);
							}
						}
						FieldValueMap oneFieldValue = convertOneField(f, idFieldValues);
						oneFieldValue.fieldName = foreignKey.name();
						if(foreignKey.unique() || allUniqueFields.contains(foreignKey.name())) {
							oneFieldValue.keyType = 2;
						}else {
							oneFieldValue.keyType = 3;
						}
						fieldValues.add(oneFieldValue);
						break;
					}
				}
				continue;
			}
			Embedded embed = field.getAnnotation(javax.persistence.Embedded.class);
			if (embed != null) {
				Class<?> embClass = field.getType();
				List<Field> embFields = getValidFields(embClass, Collections.emptyList());
				for (Field ef : embFields) {
					List<Object> embFieldValues = new ArrayList<>();
					for(Object o : oneFieldValues) {
						if(null == o) {
							embFieldValues.add(null);
						}else {
							ef.setAccessible(true);
							Object v = ef.get(o);
							embFieldValues.add(v);
						}
					}
					FieldValueMap oneFieldValue = convertOneField(ef, embFieldValues);
					oneFieldValue.fieldName = field.getName() + "_" + ef.getName();
					fieldValues.add(oneFieldValue);
				}
				continue;
			}
		}
		return fieldValues;
	}

	public static FieldValueMap convertOneField(Field field, Collection<?> dataList) throws Exception {
		FieldValueMap fvm = new FieldValueMap();
		Type type = field.getGenericType();
		FieldType fieldType = field.getAnnotation(com.jun.ming.excel.annotation.FieldType.class);
		fvm.jdbcType = fieldType.value();
		int dataSize = dataList.size();
		fvm.values = new Object[dataSize];
		int i = 0;
		for (Object o : dataList) {
			if(null == o) {
				fvm.values[i++] = null;
				continue;
			}
			if (((Class<?>) type).isEnum()) {
				FieldType enumField = field.getAnnotation(FieldType.class);
				if (enumField.value() == JDBCType.VARCHAR) {
					fvm.values[i++] = ((Enum<?>) o).name();
				}
				if (enumField.value() == JDBCType.INTEGER) {
					fvm.values[i++] = ((Enum<?>) o).ordinal();
				}
			} else {
				fvm.values[i++] = o;
			}
		}
		return fvm;
	}

	public static String createSql(String tableName, Set<String> uniques, List<String> insertFieldNameList,
			List<String> updateFieldNameList, SqlInterface sqlInterface) {
		if (CollectionUtils.isEmpty(updateFieldNameList)) {
			return sqlInterface.createBatchInsertSql(tableName, insertFieldNameList);
		} else {
			return sqlInterface.createBatchInsertAndUpdateSql(tableName, String.join(",", uniques), insertFieldNameList,
					updateFieldNameList);
		}
	}

	public static class FieldValueMap {
		// 1:主键；2:唯一约束；3:普通字段
		private int keyType;
		private String fieldName;
		private Object[] values;
		private JDBCType jdbcType;
	}

}
