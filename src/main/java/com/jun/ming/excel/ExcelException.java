package com.jun.ming.excel;

public class ExcelException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Object obj;

    public ExcelException(Object obj, String msg) {
        super(msg);
        this.obj = obj;
    }

    public ExcelException(String msg) {
        this(null, msg);
    }

    public Object getObj() {
        return obj;
    }
}
