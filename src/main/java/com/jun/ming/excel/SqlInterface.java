package com.jun.ming.excel;

import java.util.List;

public interface SqlInterface {

	String createBatchInsertSql(String tableName, List<String> insertFieldNameList);

	String createBatchInsertAndUpdateSql(String tableName, String uniques, List<String> insertFieldNameList,
			List<String> updateFieldNameList);
}
