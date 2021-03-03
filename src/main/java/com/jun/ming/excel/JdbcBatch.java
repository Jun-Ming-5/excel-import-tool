package com.jun.ming.excel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import com.jun.ming.excel.annotation.FieldType;

public class JdbcBatch {
	
	private JdbcBatch() {
		
	}
	
	public static <T> int batchPageInsert(JdbcTemplate jdbcTemplate, Class<T> c, Collection<T> dataList,int pageSize,SqlInterface sqlInterface) throws Exception{
		List<T> pageList = new ArrayList<>();
		int total = 0;
		for(T t : dataList) {
			pageList.add(t);
			if(pageList.size() == pageSize) {
				total+=batchInsert(jdbcTemplate, c, pageList,sqlInterface);	
				pageList.clear();
			}
		}
		if(!pageList.isEmpty()) {
			total+=batchInsert(jdbcTemplate, c, pageList,sqlInterface);
		}
		return total;
	}
	public static <T> int batchInsert(JdbcTemplate jdbcTemplate, Class<T> c, Collection<T> dataList,SqlInterface sqlInterface) throws Exception {
		List<Class<?>> allClass = getAllClass(c);
		List<Field> validFields = new ArrayList<>();
		for (Class<?> cc : allClass) {
			List<Field> validField = getValidFields(cc, validFields);
			validFields.addAll(validField);
		}
		Table annotation = c.getAnnotation(javax.persistence.Table.class);
		String table = annotation.name();
		Set<String> uniqueFields = getUniqueFields(c, validFields);
		String uniques = String.join(",", uniqueFields);
		Map<Integer, Object[]> elementArray = convertArray(validFields, dataList);
		List<String> insertFieldNameList = getFieldNames(c, validFields, true);
		List<String> updateFieldNameList = getFieldNames(c, validFields, false);
		Map<Integer, JDBCType> fieldTypeMap = createFieldType(validFields);
		String sql = createSql(table, uniques, insertFieldNameList, updateFieldNameList,sqlInterface);
		return jdbcTemplate.execute(new ConnectionCallback<Integer>() {
			@Override
			public Integer doInConnection(Connection connection) throws SQLException, DataAccessException {
				try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
					int fieldCounts = validFields.size();
					for (int i = 1; i <= fieldCounts; i++) {
						prepareStatement.setArray(i,
								connection.createArrayOf(fieldTypeMap.get(i).getName(), elementArray.get(i)));
					}
					return prepareStatement.executeUpdate();
				}
			}
		});
	}

	public static List<Class<?>> getAllClass(Class<?> c) {
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

	public static List<Field> getValidFields(Class<?> c, List<Field> existFields) {
		Field[] fields = c.getDeclaredFields();
		List<Field> validFields = new ArrayList<>();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers())) {
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
	public static Set<String> getUniqueFields(Class<?> c, List<Field> fields) {
		Table table = c.getAnnotation(javax.persistence.Table.class);
		UniqueConstraint[] uniqueConstraints = table.uniqueConstraints();
		Set<String> uniques = new HashSet<>();
		for (UniqueConstraint uc : uniqueConstraints) {
			if (!StringUtils.isEmpty(uc.name())) {
				uniques.add(uc.name());
			}
			for (String columnName : uc.columnNames()) {
				if (!StringUtils.isEmpty(columnName)) {
					uniques.add(columnName);
				}
			}
		}
		for (Field field : fields) {
			Column annotation = field.getAnnotation(javax.persistence.Column.class);
			if (annotation != null && annotation.unique()) {
				if (!StringUtils.isEmpty(annotation.name())) {
					uniques.add(annotation.name());
				}
			}
		}
		return uniques;
	}

	public static Map<Integer, Object[]> convertArray(List<Field> fields, Collection<?> tableList) throws Exception {
		if (CollectionUtils.isEmpty(tableList)) {
			return Collections.emptyMap();
		}
		int fieldIndex = 1;
		int tableSize = tableList.size();
		Map<Integer, Object[]> map = new HashMap<>();
		Map<Integer, Field> mainFieldMap = new HashMap<>();
		Map<Integer, Field> foreignFieldMap = new HashMap<>();
		Map<Field, Field> mainForeignMap = new HashMap<>();

		for (Field field : fields) {
			Type type = null;
			Field foreignField = getForeignKey(field);
			if (foreignField != null) {
				foreignFieldMap.put(fieldIndex, field);
				mainForeignMap.put(field, foreignField);
				type = foreignField.getGenericType();
			} else {
				mainFieldMap.put(fieldIndex, field);
				type = field.getGenericType();
			}
			if (type == String.class) {
				map.put(fieldIndex, new String[tableSize]);
			} else if (type == Boolean.class || type == boolean.class) {
				map.put(fieldIndex, new Boolean[tableSize]);
			} else if (type == Integer.class || type == int.class) {
				map.put(fieldIndex, new Integer[tableSize]);
			} else if (type == BigDecimal.class) {
				map.put(fieldIndex, new BigDecimal[tableSize]);
			} else if (type == LocalDate.class) {
				map.put(fieldIndex, new LocalDate[tableSize]);
			} else if (type == LocalDateTime.class) {
				map.put(fieldIndex, new LocalDateTime[tableSize]);
			} else if (type == Long.class || type == long.class) {
				map.put(fieldIndex, new Long[tableSize]);
			} else if (((Class<?>) type).isEnum()) {
				FieldType fieldType = field.getAnnotation(FieldType.class);
				if (fieldType.value() == JDBCType.VARCHAR) {
					map.put(fieldIndex, new String[tableSize]);
				} else if (fieldType.value() == JDBCType.INTEGER) {
					map.put(fieldIndex, new Integer[tableSize]);
				} else {
					throw new RuntimeException("unknown type:" + type + " field:" + field + " enum field type error");
				}
			} else {
				throw new RuntimeException("unknown type:" + type + " field:" + field);
			}
			fieldIndex++;
		}

		int j = 0;
		for (Object o : tableList) {
			for (Map.Entry<Integer, Field> entry : mainFieldMap.entrySet()) {
				if (o == null) {
					continue;
				}
				int key = entry.getKey();
				Field field = entry.getValue();
				field.setAccessible(true);
				Type type = field.getGenericType();
				if (((Class<?>) type).isEnum()) {
					FieldType fieldType = field.getAnnotation(FieldType.class);
					if (fieldType.value() == JDBCType.VARCHAR) {
						map.get(key)[j] = ((Enum<?>) field.get(o)).name();
					} else {
						map.get(key)[j] = ((Enum<?>) field.get(o)).ordinal();
					}
				} else {
					map.get(key)[j] = field.get(o);
				}
			}
			for (Map.Entry<Integer, Field> entry : foreignFieldMap.entrySet()) {
				if (o == null) {
					continue;
				}
				int key = entry.getKey();
				Field field = entry.getValue();
				field.setAccessible(true);
				Object object = field.get(o);
				if (object != null) {
					Field foreignField = mainForeignMap.get(field);
					foreignField.setAccessible(true);
					Object v = foreignField.get(object);
					map.get(key)[j] = v;
				}
			}
			j++;
		}
		return map;
	}

	public static Field getForeignKey(Field field) {
		JoinColumn jionColumn = field.getAnnotation(javax.persistence.JoinColumn.class);
		if (jionColumn == null) {
			return null;
		}
		Class<?> type = field.getType();
		Field[] declaredFields = type.getDeclaredFields();
		for (Field f : declaredFields) {
			Id annotation = f.getAnnotation(javax.persistence.Id.class);
			if (annotation != null) {
				return f;
			}
		}
		return null;
	}
	public static List<String> getFieldNames(Class<?> c, List<Field> fields, boolean insert) {
		List<String> names = new ArrayList<>(fields.size());
        if (insert) {
            for (Field field : fields) {
                Column annotation = field.getAnnotation(javax.persistence.Column.class);
                JoinColumn jColumn = field.getAnnotation(javax.persistence.JoinColumn.class);
                if (annotation != null) {
                    names.add(annotation.name());
                }
                if (jColumn != null) {
                    names.add(jColumn.name());
                }
            }
        } else {
            Table table = c.getAnnotation(javax.persistence.Table.class);
            UniqueConstraint[] uniqueConstraints = table.uniqueConstraints();
            List<String> uniques = new ArrayList<>();
            for (UniqueConstraint uc : uniqueConstraints) {
                if (!StringUtils.isEmpty(uc.name())) {
                    uniques.add(uc.name());
                }
                for (String columnName : uc.columnNames()) {
                    if (!StringUtils.isEmpty(columnName)) {
                        uniques.add(columnName);
                    }
                }
            }

            for (Field field : fields) {
                Column annotation = field.getAnnotation(javax.persistence.Column.class);
                JoinColumn jColumn = field.getAnnotation(javax.persistence.JoinColumn.class);
                Id id = field.getAnnotation(javax.persistence.Id.class);
                if (id != null) {
                    continue;
                }
                if (annotation != null) {
                    names.add(annotation.name());
                }
                if (jColumn != null) {
                    names.add(jColumn.name());
                }
                if (annotation != null && annotation.unique()) {
                    uniques.add(annotation.name());
                }
            }
            names.removeAll(uniques);
        }
        return names;
	}
	public static Map<Integer, JDBCType> createFieldType(List<Field> fields) {
		List<Field> concreteFields = new ArrayList<>(fields.size());
		for (Field field : fields) {
			Field foreignField = getForeignKey(field);
			if (foreignField != null) {
				concreteFields.add(foreignField);
			} else {
				concreteFields.add(field);
			}
		}
		Map<Integer, JDBCType> map = new HashMap<>(fields.size());
		int i = 1;
		for (Field field : concreteFields) {
			FieldType annotation = field.getAnnotation(FieldType.class);
			if (annotation == null) {
				throw new RuntimeException("FieldType doesn't exist on field: " + field);
			}
			map.put(i, annotation.value());
			i++;
		}
		return map;
	}

	public static String createSql(String tableName, String unique, List<String> insertFieldNameList,
			List<String> updateFieldNameList,SqlInterface sqlInterface) {
		if (CollectionUtils.isEmpty(updateFieldNameList)) {
			return sqlInterface.createBatchInsertSql(tableName, insertFieldNameList);
		} else {
			return sqlInterface.createBatchInsertAndUpdateSql(tableName, unique, insertFieldNameList, updateFieldNameList);
		}
	}
}
