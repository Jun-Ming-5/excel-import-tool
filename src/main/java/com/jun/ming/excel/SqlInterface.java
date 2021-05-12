package com.jun.ming.excel;

import java.util.List;
import java.util.Set;

public interface SqlInterface {

	String createBatchInsertSql(String tableName, List<String> insertFieldNameList);

	String createBatchInsertAndUpdateSql(String tableName, Set<String> uniques, List<String> insertFieldNameList,
										 List<String> updateFieldNameList);
}
