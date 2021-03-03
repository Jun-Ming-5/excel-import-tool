package com.jun.ming.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PostgresSql implements SqlInterface {
	
	@Override
	public String createBatchInsertAndUpdateSql(String tableName, String unique, List<String> insertFieldNameList,
			List<String> updateFieldNameList) {
		String sql = "INSERT INTO $table_name ($param1) VALUES($param2) ON CONFLICT ( $unique ) DO UPDATE SET $param3";
		sql = sql.replace("$table_name", tableName);
		sql = sql.replace("$unique", unique);
		String params1 = String.join(",", insertFieldNameList);
		sql = sql.replace("$param1", params1);

		List<String> unnestList = Collections.nCopies(insertFieldNameList.size(), "unnest(?)");
		String params2 = String.join(",", unnestList);
		sql = sql.replace("$param2", params2);
		List<String> updateParam = new ArrayList<>(updateFieldNameList.size());
		for (String fieldName : updateFieldNameList) {
			String s = fieldName + "=excluded." + fieldName;
			updateParam.add(s);
		}
		String params3 = String.join(",", updateParam);
		sql = sql.replace("$param3", params3);
		return sql;
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
