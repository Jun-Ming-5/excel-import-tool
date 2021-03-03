package com.jun.ming.excel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jun.ming.excel.ExcelContent.ExcelHeader;
import com.jun.ming.excel.annotation.Excel;

public class EntityLine {
	private final int index;
	private final String entityField;
	private final String fieldName;

	private EntityLine(int index, String entityField, String fieldName) {
		this.index = index;
		this.entityField = entityField;
		this.fieldName = fieldName;
	}

	public int getIndex() {
		return index;
	}

	public String getEntityField() {
		return entityField;
	}

	public String getFieldName() {
		return fieldName;
	}

	public static List<EntityLine> toEntityLines(Class<?> c) {
		Field[] fields = c.getDeclaredFields();
		int i = 1;
		List<EntityLine> lines = new ArrayList<>();
		for (Field field : fields) {
			Excel annotation = field.getAnnotation(Excel.class);
			EntityLine oneLine = new EntityLine(i, field.getName(), annotation.name());
			i++;
			lines.add(oneLine);
		}
		return lines;
	}

	public static Map<String, String> requireEntityFields(Class<?> c) {
		Map<String, String> map = new HashMap<>();
		Field[] fields = c.getDeclaredFields();
		for (Field field : fields) {
			Excel annotation = field.getAnnotation(Excel.class);
			if (annotation.required()) {
				map.put(field.getName(), annotation.name());
			}
		}
		return map;
	}

	public static Map<String, Integer> getFieldSerialMap(Class<?> c, List<ExcelHeader> headers)
			throws ExcelException {
		Map<String, Integer> map = new HashMap<>();
		Field[] fields = c.getDeclaredFields();
		List<String> fieldNameList = new ArrayList<>();
		List<String> excelNameList = new ArrayList<>();
		for (Field field : fields) {
			Excel annotation = field.getAnnotation(Excel.class);
			fieldNameList.add(annotation.name());
		}
		headers.stream().forEach(e -> excelNameList.add(e.getName()));
		List<String> tmp = new ArrayList<>();
		tmp.addAll(fieldNameList);
		tmp.removeAll(excelNameList);
		if (!tmp.isEmpty()) {
			Map<String,String> emap = new HashMap<>();
			emap.put("templateHeaders", String.join(",", fieldNameList));
			emap.put("excelHeaders", String.join(",", excelNameList));
			emap.put("missing", String.join(",", tmp));
			throw new ExcelException(emap, "excel headers should contain all template headers");
		}
		for (Field field : fields) {
			Excel annotation = field.getAnnotation(Excel.class);
			for (ExcelHeader header : headers) {
				if (annotation.name().equals(header.getName())) {
					map.put(field.getName(), header.getIndex());
				}
			}
		}
		return map;
	}

	public static List<String> getExcelName(Class<?> c) {
		List<String> list = new ArrayList<>();
		Field[] fields = c.getDeclaredFields();
		for (Field field : fields) {
			Excel annotation = field.getAnnotation(Excel.class);
			list.add(annotation.name());
		}
		return list;
	}

	public static Map<String, Integer> getFieldMap(Class<?> c) {
		Map<String, Integer> map = new HashMap<>();
		Field[] fields = c.getDeclaredFields();
		int i = 0;
		for (Field field : fields) {
			Excel annotation = field.getAnnotation(Excel.class);
			if (annotation != null) {
				map.put(field.getName(), i++);
			}
		}
		return map;
	}
}
