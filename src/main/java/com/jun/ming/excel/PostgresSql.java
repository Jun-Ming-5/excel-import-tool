package com.jun.ming.excel;

import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

public class PostgresSql implements SqlInterface {
	
	@Override
	public String createBatchInsertAndUpdateSql(String tableName, Set<String> unique, List<String> insertFieldNameList,
												List<String> updateFieldNameList) {
		String insertSql = createBatchInsertSql(tableName,insertFieldNameList);
		String conflictSql = " ON CONFLICT ( $unique ) DO UPDATE SET $params";
		conflictSql = conflictSql.replace("$unique",String.join(",",unique));
		String params = CollectionUtils.isNotEmpty(updateFieldNameList) ? getUpdateParams(updateFieldNameList) : getUpdateParams(unique);
		conflictSql = conflictSql.replace("$params", params);
		return insertSql+conflictSql;
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

	private String getUpdateParams(Collection<String> fields){
		List<String> updateParam = new ArrayList<>(fields.size());
		for (String fieldName : fields) {
			String s = fieldName + "=excluded." + fieldName;
			updateParam.add(s);
		}
		return String.join(",", updateParam);
	}
}
