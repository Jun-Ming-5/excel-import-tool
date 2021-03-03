package com.jun.ming.excel;

import static org.apache.poi.ss.usermodel.CellType.BLANK;
import static org.apache.poi.ss.usermodel.CellType.BOOLEAN;
import static org.apache.poi.ss.usermodel.CellType.ERROR;
import static org.apache.poi.ss.usermodel.CellType.FORMULA;
import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import com.jun.ming.excel.ExcelContent.ExcelHeader;
import com.jun.ming.excel.ExcelContent.WrapCell;
import com.opencsv.CSVReader;

public class ExcelCsvReader {

	private static final String EXCEL2003 = "xls";
	private static final String EXCEL2007 = "xlsx";
	private final static String CSV = "csv";

	private ExcelCsvReader() {

	}

	public static ExcelContent readExcel(MultipartFile file) throws Exception {
		String fileName = file.getOriginalFilename();
		if (!fileName.endsWith(EXCEL2003) && !fileName.endsWith(EXCEL2007)) {
			throw new RuntimeException("only support excel 2003 and 2007");
		}
		try (InputStream is = file.getInputStream();
				Workbook workbook = fileName.endsWith(EXCEL2003) ? new HSSFWorkbook(is) : new XSSFWorkbook(is)) {
			Sheet sheet = workbook.getSheetAt(0);
			int firstRowNum = sheet.getFirstRowNum();
			int lastRowNum = sheet.getLastRowNum();
			if (firstRowNum == lastRowNum) {
				throw new RuntimeException("file is empty,fileName:" + fileName);
			}
			ExcelContent excelContent = new ExcelContent();
			Row firstRow = sheet.getRow(firstRowNum);
			short headerFirstCellNum = firstRow.getFirstCellNum();
			short headerLastCellNum = firstRow.getLastCellNum();

			List<ExcelHeader> headers = new ArrayList<>();
			for (int i = headerFirstCellNum; i < headerLastCellNum; i++) {
				String cellName = firstRow.getCell(i).getStringCellValue();
				headers.add(ExcelHeader.valueof(i, cellName));
			}
			Map<Cordinate, Cordinate> toFirstRowCellMap = new ConcurrentHashMap<>();
			sheet.getMergedRegions().parallelStream().forEach(range -> {
				for (int i = range.getFirstRow(); i <= range.getLastRow(); i++) {
					for (int j = range.getFirstColumn(); j <= range.getLastColumn(); j++) {
						toFirstRowCellMap.put(Cordinate.of(i, j), Cordinate.of(range.getFirstRow(), j));
					}
				}
			});
			List<List<WrapCell>> contents = new ArrayList<>();
			for (int i = firstRowNum + 1; i <= lastRowNum; i++) {
				Row row = sheet.getRow(i);
				if (Objects.isNull(row)) {
					break;
				}
				List<WrapCell> wrapCellList = new ArrayList<>();
				for (int j = headerFirstCellNum; j < headerLastCellNum; j++) {
					Cell cell = row.getCell(j);
					Cordinate cordinate = toFirstRowCellMap.get(Cordinate.of(i, j));
					if (cordinate != null) {
						Cell firstLinecell = sheet.getRow(cordinate.row).getCell(j);
						if (cell != null && firstLinecell.getAddress().compareTo(cell.getAddress()) == 0) {
							wrapCellList.add(WrapCell.realCell(i, getCellValue(cell)));
						} else {
							wrapCellList.add(WrapCell.fakeCell(cordinate.row, getCellValue(firstLinecell)));
						}
					} else {
						wrapCellList.add(WrapCell.realCell(i, getCellValue(cell)));
					}
				}
				boolean lineEmpty = true;
				for (WrapCell wCell : wrapCellList) {
					lineEmpty = lineEmpty & wCell.isEmpty();
				}
				if (lineEmpty) {
					break;
				}
				contents.add(wrapCellList);
			}
			excelContent.setFileName(fileName);
			excelContent.setHeaders(headers);
			excelContent.setContents(contents);
			return excelContent;
		}
	}

	public static ExcelContent readCsv(MultipartFile file) throws Exception {
		String fileName = file.getOriginalFilename();
		if (!fileName.endsWith(CSV)) {
			throw new RuntimeException("only support CSV");
		}
		CSVReader csvReader = null;
		try {
			csvReader = new CSVReader(new InputStreamReader(file.getInputStream(), "UTF-8"));
			ExcelContent excelContent = new ExcelContent();
			String[] headers = csvReader.readNext();
			List<ExcelHeader> excelHeaderList = new ArrayList<>();
			for (int i = 0; i < headers.length; i++) {
				String head = headers[i];
				if (head.startsWith("\uFEFF")) {
					head = head.replace("\uFEFF", "");
				} else if (head.endsWith("\uFEFF")) {
					head = head.replace("\uFEFF", "");
				}
				ExcelHeader valueof = ExcelHeader.valueof(i, head.trim());
				excelHeaderList.add(valueof);
			}
			if (excelHeaderList.isEmpty()) {
				csvReader = new CSVReader(new InputStreamReader(file.getInputStream(), "GBK"));
				headers = csvReader.readNext();
				for (int i = 0; i < headers.length; i++) {
					excelHeaderList.add(ExcelHeader.valueof(i, headers[i]));
				}
			}

			String[] lines = null;
			List<List<WrapCell>> contents = new ArrayList<>();
			int line = 1;
			while ((lines = csvReader.readNext()) != null) {
				List<WrapCell> cellList = new ArrayList<>();
				boolean cellEmpty = true;
				for (String cell : lines) {
					cellEmpty = cellEmpty && StringUtils.isEmpty(cell);
					cellList.add(WrapCell.realCell(line++, cell));
				}
				for (int i = cellList.size(); i < headers.length; i++) {
					cellList.add(WrapCell.emptyCell());
				}

				contents.add(cellList);
			}
			excelContent.setFileName(fileName);
			excelContent.setHeaders(excelHeaderList);
			excelContent.setContents(contents);
			return excelContent;
		} catch (Exception e) {
			csvReader.close();
			return null;
		}
	}

	private static String getCellValue(Cell cell) {
		if (cell == null) {
			return "";
		}
		if (cell.getCellType() == NUMERIC) {
			if (DateUtil.isCellDateFormatted(cell)) {
				return HSSFDateUtil.getJavaDate(cell.getNumericCellValue()).toString();
			} else {
				return new BigDecimal(cell.getNumericCellValue()).toString();
			}
		} else if (cell.getCellType() == STRING) {
			return StringUtils.trimToEmpty(cell.getStringCellValue());
		} else if (cell.getCellType() == FORMULA) {
			return StringUtils.trimToEmpty(cell.getCellFormula());
		} else if (cell.getCellType() == BLANK) {
			return "";
		} else if (cell.getCellType() == BOOLEAN) {
			return String.valueOf(cell.getBooleanCellValue());
		} else if (cell.getCellType() == ERROR) {
			return "ERROR";
		} else {
			return cell.toString().trim();
		}
	}

	private static class Cordinate {
		private int row;
		private int column;

		private Cordinate(int row, int column) {
			this.row = row;
			this.column = column;
		}

		public static Cordinate of(int row, int column) {
			return new Cordinate(row, column);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + column;
			result = prime * result + row;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Cordinate other = (Cordinate) obj;
			if (column != other.column) {
				return false;
			}
			if (row != other.row) {
				return false;
			}
			return true;
		}
	}
}
