package com.jun.ming.excel;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PostgresSql implements SqlInterface {
	
	@Override
	public String createBatchInsertAndUpdateSql(String tableName, String unique, List<String> insertFieldNameList,
			List<String> updateFieldNameList) {
		String insertSql = createBatchInsertSql(tableName,insertFieldNameList);
		if(CollectionUtils.isNotEmpty(updateFieldNameList)) {
			String conflictSql = " ON CONFLICT ( $unique ) DO UPDATE SET $param3";
			conflictSql = conflictSql.replace("$unique",unique);
			List<String> updateParam = new ArrayList<>(updateFieldNameList.size());
			for (String fieldName : updateFieldNameList) {
				String s = fieldName + "=excluded." + fieldName;
				updateParam.add(s);
			}
			String params3 = String.join(",", updateParam);
			conflictSql = conflictSql.replace("$param3", params3);
			insertSql = insertSql+conflictSql;
		}
		return insertSql;
	}

	@Override
	public String createBatchInsertSql(String tableName, List<String> insertFieldNameList) {
		String sql = "INSERT INTO $table_name ($param1) VALUES($param2)";
		sql = sql.replace("$table_name", tableName);
		String params1 = String.join(",", insertFieldNameList);
		sql = sql.replace("$param1", params1);
		List<String> unnestList = Collections.nCopies(insertFieldNameList.size(), "unnest(?)");
		String params2 = String.join(",", unnestList);
		sql = sql.replace("$param2", params2);
		return sql;
	}
}
