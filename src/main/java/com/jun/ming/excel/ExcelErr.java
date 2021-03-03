package com.jun.ming.excel;

import lombok.Data;

@Data
public class ExcelErr {
    private String columnName;
    private int lines;
    private Object cellValue;
    private String errMsg;

    public static ExcelErr err(String columnName, int lines, Object cellValue, String errMsg) {
    	ExcelErr err = new ExcelErr();
    	err.columnName = columnName;
    	err.lines = lines;
    	err.cellValue = cellValue;
    	err.errMsg = errMsg;
    	return err;
    }
}
