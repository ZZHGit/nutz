package org.nutz.json.compile;

import java.io.IOException;
import java.io.Reader;

import org.nutz.json.JsonException;
import org.nutz.json.JsonCompile;
import org.nutz.json.JsonItem;
import org.nutz.json.item.ArrayJsonItem;
import org.nutz.json.item.PairJsonItem;
import org.nutz.json.item.ObjectJsonItem;
import org.nutz.json.item.SingleJsonItem;
import org.nutz.json.item.StringJsonItem;

/**
 * 字符串顺序预编译
 * 
 * @author juqkai(juqkai@gmail.com)
 *
 */
public class StringCompile implements JsonCompile{
	
	private int cursor;
	private Reader reader;
	private int col;
	private int row;
	
	private static final int END = -1;
	
	public JsonItem Compile(Reader reader) {
		this.reader = reader;
		try {
			nextChar();
			skipCommentsAndBlank();
			if(cursor == 'v'){
				/*
				 * Meet the var ioc ={ maybe, try to find the '{' and break
				 */
				while (END != nextChar())
					if ('{' == cursor)
						break;
			}
			return compileLocation();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private JsonItem compileLocation() throws IOException{
		skipCommentsAndBlank();
		JsonItem ji = null;
		switch(cursor){
		case '{':
		case '[':
			ji = compileArray();
			break;
		case '"':
		case '\'':
		default:
			ji = compileString();
			break;
		}
		skipCommentsAndBlank();
		return ji;
	}
	
	private JsonItem compileString() throws IOException{
		
		if(cursor != '\'' && cursor != '"'){
			StringBuilder sb = new StringBuilder();
			while(cursor != END && cursor != ':' && cursor != ',' && cursor != ']' && cursor != '}'){
				sb.append((char)cursor);
				nextChar();
				skipCommentsAndBlank();
			}
			SingleJsonItem sji = new SingleJsonItem();
			sji.setValue(sb.toString());
			return sji;
		}
		StringJsonItem sji = new StringJsonItem();
		sji.setValue(readString().toString());
		return sji;
	}
	/**
	 * 编译数组,将所有'[]','{}'包裹的字符串理解成数组
	 */
	private JsonItem compileArray() throws IOException{
		boolean isObj = cursor == '{' ? true: false;
		nextChar();
		ArrayJsonItem aji = isObj ? new ObjectJsonItem() : new ArrayJsonItem();
		while(cursor != END && cursor != '}' && cursor != ']'){
			if(cursor == ','){
				nextChar();
				continue;
			}
			JsonItem name = compileLocation();
			
			if(cursor == ':'){
				aji.addItem(compilePair(name));
				continue;
			}
			//保存单值对象
			aji.addItem(name);
		}
		nextChar();
		return aji;
	}

	/**
	 * 保存键值对对象
	 */
	private JsonItem compilePair(JsonItem name) throws IOException{
		PairJsonItem obj = new PairJsonItem();
		obj.setKey(name);
		nextChar();
		obj.setValue(compileLocation());
		return obj;
	}
	
	private StringBuilder readString() throws IOException {
		StringBuilder sb = new StringBuilder();
		int expEnd = cursor;
		nextChar();
		while (cursor != END && cursor != expEnd) {
			if (cursor == '\\') {
				nextChar();
				switch (cursor) {
				case 'n':
					cursor = 10;
					break;
				case 'r':
					cursor = 13;
					break;
				case 't':
					cursor = 9;
					break;
				case 'u':
					char[] hex = new char[4];
					for (int i = 0; i < 4; i++)
						hex[i] = (char) nextChar();
					cursor = Integer.valueOf(new String(hex), 16);
					break;
				case 'b':
					throw makeError("don't support \\b");
				case 'f':
					throw makeError("don't support \\f");
				}
			}
			sb.append((char) cursor);
			nextChar();
		}
		if (cursor == END)
			throw makeError("Unclose string");
		nextChar();
		return sb;
	}

	
	private int nextChar() throws IOException {
		if (-1 == cursor)
			return -1;
		try {
			cursor = reader.read();
			if (cursor == '\n') {
				row++;
				col = 0;
			} else
				col++;
		}
		catch (Exception e) {
			cursor = -1;
		}
		return cursor;
	}

	private void skipCommentsAndBlank() throws IOException {
		skipBlank();
		while (cursor != END && cursor == '/') {
			nextChar();
			if (cursor == '/') { // inline comment
				skipInlineComment();
				nextChar();
			} else if (cursor == '*') { // block comment
				skipBlockComment();
				nextChar();
			} else {
				throw makeError("Error comment syntax!");
			}
			skipBlank();
		}
	}
	private void skipInlineComment() throws IOException {
		while (nextChar() != END && cursor != '\n') {}
	}
	
	private void skipBlank() throws IOException {
		while (cursor >= 0 && cursor <= 32)
			nextChar();
	}

	private void skipBlockComment() throws IOException {
		nextChar();
		while (cursor != END) {
			if (cursor == '*') {
				if (nextChar() == '/')
					break;
			} else
				nextChar();
		}
	}
	private JsonException makeError(String message) {
		return new JsonException(row, col, (char) cursor, message);
	}
}