package com.nao.discordbridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny, dependency-free JSON parser/writer - just enough for the Discord REST payloads this plugin
 * touches. We don't bundle gson/jackson: the server's own gson is relocated and we want a zero-dep jar.
 *
 * {@link #parse(String)} returns a graph of Map&lt;String,Object&gt; / List&lt;Object&gt; / String /
 * Double / Boolean / null. Discord snowflake ids are returned by the API as JSON strings, so we never
 * lose precision parsing numbers as Double.
 */
final class Json {

	private final String s;
	private int i;

	private Json(String text) {
		this.s = text;
	}

	/** Parse a whole JSON document. Returns Map / List / String / Double / Boolean / null. */
	static Object parse(String text) {
		Json p = new Json(text);
		p.ws();
		Object v = p.value();
		p.ws();
		return v;
	}

	private Object value() {
		char c = s.charAt(i);
		switch (c) {
			case '{': return object();
			case '[': return array();
			case '"': return string();
			case 't': i += 4; return Boolean.TRUE;   // true
			case 'f': i += 5; return Boolean.FALSE;  // false
			case 'n': i += 4; return null;           // null
			default:  return number();
		}
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		i++; // consume '{'
		ws();
		if (s.charAt(i) == '}') { i++; return m; }
		while (true) {
			ws();
			String key = string();
			ws();
			i++; // consume ':'
			ws();
			m.put(key, value());
			ws();
			char c = s.charAt(i++);
			if (c == '}') break;
			// otherwise c == ',' -> next entry
		}
		return m;
	}

	private List<Object> array() {
		List<Object> a = new ArrayList<Object>();
		i++; // consume '['
		ws();
		if (s.charAt(i) == ']') { i++; return a; }
		while (true) {
			ws();
			a.add(value());
			ws();
			char c = s.charAt(i++);
			if (c == ']') break;
			// otherwise c == ',' -> next element
		}
		return a;
	}

	private String string() {
		StringBuilder b = new StringBuilder();
		i++; // consume opening '"'
		while (true) {
			char c = s.charAt(i++);
			if (c == '"') break;
			if (c == '\\') {
				char e = s.charAt(i++);
				switch (e) {
					case '"':  b.append('"');  break;
					case '\\': b.append('\\'); break;
					case '/':  b.append('/');  break;
					case 'b':  b.append('\b'); break;
					case 'f':  b.append('\f'); break;
					case 'n':  b.append('\n'); break;
					case 'r':  b.append('\r'); break;
					case 't':  b.append('\t'); break;
					case 'u':
						b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
						i += 4;
						break;
					default:   b.append(e);
				}
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	private Object number() {
		int start = i;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
				i++;
			} else {
				break;
			}
		}
		return Double.parseDouble(s.substring(start, i));
	}

	private void ws() {
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
			else break;
		}
	}

	/** Escape a Java string into the body of a JSON string literal (no surrounding quotes). */
	static String esc(String v) {
		StringBuilder b = new StringBuilder(v.length() + 16);
		for (int k = 0; k < v.length(); k++) {
			char c = v.charAt(k);
			switch (c) {
				case '"':  b.append("\\\""); break;
				case '\\': b.append("\\\\"); break;
				case '\n': b.append("\\n");  break;
				case '\r': b.append("\\r");  break;
				case '\t': b.append("\\t");  break;
				case '\b': b.append("\\b");  break;
				case '\f': b.append("\\f");  break;
				default:
					if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
					else b.append(c);
			}
		}
		return b.toString();
	}
}
