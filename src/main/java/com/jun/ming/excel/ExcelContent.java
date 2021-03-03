package com.jun.ming.excel;

import java.util.List;

public class ExcelContent {
	private String fileName;
	private List<ExcelHeader> headers;
	private List<List<WrapCell>> contents;

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public List<ExcelHeader> getHeaders() {
		return headers;
	}

	public void setHeaders(List<ExcelHeader> headers) {
		this.headers = headers;
	}

	public List<List<WrapCell>> getContents() {
		return contents;
	}

	public void setContents(List<List<WrapCell>> contents) {
		this.contents = contents;
	}

	public static class ExcelHeader {
		private final int index;
		private final String name;

		private ExcelHeader(int index, String name) {
			this.index = index;
			this.name = name;
		}

		public static ExcelHeader valueof(int index, String name) {
			return new ExcelHeader(index, name);
		}

		public int getIndex() {
			return index;
		}

		public String getName() {
			return name;
		}
	}
	

    public static class WrapCell {
    	private final int virtualLine;
        private final boolean real;
        private final String cellValue;

        private WrapCell(boolean real, int virtualLine,String cellValule) {
            this.real = real;
            this.virtualLine = virtualLine;
            this.cellValue = cellValule;
        }

        public static WrapCell realCell(int virtualLine,String cellValue) {
            return new WrapCell(true, virtualLine,cellValue);
        }

        public static WrapCell emptyCell(){
            return new WrapCell(true, 0,null);
        }

        public static WrapCell fakeCell(int virtualLine,String cellValue) {
            return new WrapCell(false, virtualLine,cellValue);
        }

        public boolean isReal() {
            return real;
        }

        public String getCellValue() {
            return cellValue;
        }
        
        public int getVirtualLine() {
			return virtualLine;
		}

		public boolean isEmpty() {
        	return (cellValue == null || "".equals(cellValue));
        }
    }
}
