package com.jun.ming.excel;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.jun.ming.excel.ExcelContent.WrapCell;
import com.jun.ming.excel.annotation.Excel;
import com.jun.ming.excel.annotation.FieldType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExcelHelper {

	private static final String EXCEL2003 = "xls";
	private static final String EXCEL2007 = "xlsx";
	private static final String CSV = "csv";
	private static final Logger logger = LoggerFactory.getLogger(ExcelHelper.class);

	private ExcelHelper() {
	}

	public static ExcelContent readExcel(MultipartFile file) throws Exception {
		String fileName = file.getOriginalFilename();
		if (fileName.endsWith(EXCEL2003) || fileName.endsWith(EXCEL2007)) {
			return ExcelCsvReader.readExcel(file);
		} else if (fileName.endsWith(CSV)) {
			return ExcelCsvReader.readCsv(file);
		} else {
			throw new ExcelException("only support excel 2003 2007 or csv");
		}
	}

	public static <T extends ExcelLine> List<T> getEntities(Class<T> excelClass, ExcelContent excelContent,
			List<ExcelErr> excelErrList) throws Exception {
		Map<String, Integer> map = EntityLine.getFieldSerialMap(excelClass, excelContent.getHeaders());
		return getEntities(excelClass, map, excelContent, excelErrList);
	}

	private static <T extends ExcelLine> List<T> getEntities(Class<T> c, Map<String, Integer> fieldMap,
			ExcelContent excelContent, List<ExcelErr> excelErrList) throws Exception {
		List<List<WrapCell>> contents = excelContent.getContents();
		int lines = 1;
		List<T> entityList = new ArrayList<>();
		List<List<CellUnique>> uniqueOneLines = new ArrayList<>();
		List<List<CellUnique>> uniqueTwoLines = new ArrayList<>();
		for (List<WrapCell> content : contents) {
			lines++;
			T t = c.newInstance();
			boolean ok = true;
			List<CellUnique> uniqueOneList = new ArrayList<>();
			List<CellUnique> uniqueTwoList = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : fieldMap.entrySet()) {
				int index = entry.getValue();
				String columName = excelContent.getHeaders().get(index).getName();
				String fieldName = entry.getKey();
				WrapCell wrapCell = content.get(index);
				int virtualLine = wrapCell.getVirtualLine();
				String excellValue = wrapCell.getCellValue();
				Field field = c.getDeclaredField(fieldName);
				Object v = null;
				try {
					v = getCellValue(excellValue, field, true);
				} catch (Exception e) {
					log.warn("cell-name:{} lines:{} error,exception:{}", columName, lines, e.getMessage());
				}
				if (required(field) && v == null) {
					excelErrList.add(ExcelErr.err(columName, lines, excellValue, "必须字段格式错误或为空!!!"));
					ok = false;
					break;
				}
				Excel annotation = field.getAnnotation(Excel.class);
				if (annotation != null && annotation.unique()) {
					uniqueOneList.add(CellUnique.one(columName, excellValue));
					uniqueTwoList.add(CellUnique.two(virtualLine, columName, excellValue));
				}
				field.setAccessible(true);
				field.set(t, v);
			}
			if (!ok) {
				continue;
			}
			if (!uniqueOneList.isEmpty()) {
				if(uniqueOneLines.contains(uniqueOneList)) {
					if(!uniqueTwoLines.contains(uniqueTwoList)) {
						List<String> keys = uniqueOneList.stream().map(e -> e.getCellName()).collect(Collectors.toList());
						List<String> values = uniqueOneList.stream().map(e -> e.getCellValue())
								.collect(Collectors.toList());
						excelErrList.add(ExcelErr.err(keys.toString(), lines, values, "唯一约束字段出现重复!!!"));
						continue;
					}
				}else {
					uniqueOneLines.add(uniqueOneList);
					uniqueTwoLines.add(uniqueTwoList);
				}
			}

			t.setLine(lines);
			entityList.add(t);
		}
		return entityList;
	}

	public static Object getCellValue(String cellValue, Field field, boolean debug) {
		if (StringUtils.isEmpty(cellValue)) {
			logger.debug("cell value is empty,field:{}", field);
			return null;
		}
		Type type = field.getGenericType();
		Excel annotation = field.getAnnotation(Excel.class);
		String importKey = annotation.kv();
		if (!StringUtils.isEmpty(importKey)) {
			JSONObject parseObject = JSONObject.parseObject(importKey);
			if (!parseObject.containsKey(cellValue)) {
				logger.debug("field importKey doesn't contain cell-field,field:{},importKey:{},cell-value:{}",
						field.getName(), importKey, cellValue);
				return null;
			} else {
				return parseObject.get(cellValue);
			}
		}
		if (type == String.class) {
			return cellValue;
		} else if (type == Integer.class || type == int.class) {
			return Integer.valueOf(cellValue);
		} else if(type == Long.class || type == long.class){
			return Long.valueOf(cellValue);
		}else if (type == BigDecimal.class) {
			BigDecimal value = new BigDecimal(cellValue);
			if (annotation.precision() == 0 && annotation.scale() == 0) {
				return value;
			}
			if (value.precision() > annotation.precision() || value.scale() > annotation.scale()) {
				logger.warn("cell-value:{} format err,precision:{} scale:{},field:{} only support", cellValue,
						annotation.precision(), annotation.scale(), field);
				return null;
			}
			return value;
		} else if (type == LocalDate.class) {
			try {
				if (Pattern.compile("/").matcher(cellValue).find()) {
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
					Date date = formatter.parse(cellValue);
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
					cellValue = sdf.format(date);
				}
				return LocalDate.parse(cellValue, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

			} catch (Exception e) {
				try {
					SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
					Date date = sdf.parse(cellValue);
					return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				} catch (ParseException e1) {
				}
			}
			return null;
		} else if (type == LocalDateTime.class) {
			try {
				if (Pattern.compile("/").matcher(cellValue).find()) {
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
					Date date = formatter.parse(cellValue);
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					cellValue = sdf.format(date);
				}
				return LocalDateTime.parse(cellValue, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			} catch (Exception e) {
				try {
					SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
					Date date = sdf.parse(cellValue);
					return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
				} catch (ParseException e1) {
				}
			}
			return null;
		} else {
			logger.debug("unknown type:{},field:{},cellValue:{}", type, field, cellValue);
			return null;
		}
	}

	public static boolean required(Field field) {
		Excel annotation = field.getAnnotation(Excel.class);
		if (annotation != null && annotation.required() == true) {
			return true;
		}
		return false;
	}

	public static <T> HSSFWorkbook createExcelHeader(Class<T> c, String fileName) {
		List<EntityLine> entityLines = EntityLine.toEntityLines(c);
		HSSFWorkbook workbook = new HSSFWorkbook();
		HSSFSheet sheet = workbook.createSheet(fileName);
		HSSFRow shopRow = sheet.createRow(0);
		shopRow.setHeightInPoints(18);
		for (int i = 0; i < entityLines.size(); i++) {
			HSSFCell cell = shopRow.createCell(i);
			cell.setCellValue(entityLines.get(i).getFieldName());
		}
		return workbook;
	}

	public static <T> HSSFWorkbook createExcelContent(Class<T> c, List<T> contents) throws Exception {
		HSSFWorkbook workBook = createExcelHeader(c, "sheet");
		List<EntityLine> entityLines = EntityLine.toEntityLines(c);
		for (int i = 0; i < contents.size(); i++) {
			HSSFRow row = workBook.getSheetAt(0).createRow(i + 1);
			for (int j = 0; j < entityLines.size(); j++) {
				HSSFCell cell = row.createCell(j);
				Field field = c.getDeclaredField(entityLines.get(j).getEntityField());
				field.setAccessible(true);
				setCellValue(cell, contents.get(i), field);
			}
		}
		return workBook;
	}

	public static void setCellValue(HSSFCell cell, Object o, Field field) throws Exception {
		FieldType annotation = field.getAnnotation(FieldType.class);
		JDBCType type = annotation.value();
		switch (type) {
		case VARCHAR:
			cell.setCellValue((String) field.get(o));
			break;
		default:
			break;
		}
	}

	public static int hashCode(Collection<?> fields) {
		final int prime = 31;
		int result = 1;
		for (Object o : fields) {
			result = prime * result + o.hashCode();
		}
		return result;
	}

	public static class CellUnique {
		private final int virtualLine;
		private final String cellName;
		private final String cellValue;

		private CellUnique(int virtualLine,String cellName, String cellValue) {
			this.virtualLine = virtualLine;
			this.cellName = cellName;
			this.cellValue = cellValue;
		}

		public static CellUnique one(String cellName, String cellValue) {
			return new CellUnique(0,cellName, cellValue);
		}
		
		public static CellUnique two(int virtualLine,String cellName, String cellValue) {
			return new CellUnique(virtualLine,cellName, cellValue);
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cellName == null) ? 0 : cellName.hashCode());
			result = prime * result + ((cellValue == null) ? 0 : cellValue.hashCode());
			result = prime * result + virtualLine;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CellUnique other = (CellUnique) obj;
			if (cellName == null) {
				if (other.cellName != null)
					return false;
			} else if (!cellName.equals(other.cellName))
				return false;
			if (cellValue == null) {
				if (other.cellValue != null)
					return false;
			} else if (!cellValue.equals(other.cellValue))
				return false;
			if (virtualLine != other.virtualLine)
				return false;
			return true;
		}

		public int getVirtualLine() {
			return virtualLine;
		}

		public String getCellName() {
			return cellName;
		}

		public String getCellValue() {
			return cellValue;
		}
	}
}
