package com.nao.questcycle.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON parser/emitter. No external dependency.
 *
 * Returns: Map<String,Object>, List<Object>, String, Long, Double, Boolean, null.
 * Supports: standard JSON values, escapes (newline, tab, backslash, quote, unicode), comments NOT supported.
 * Failure mode: throws JsonException with position info.
 */
public final class Json {
	private Json() {
	}

	// ---- Parse ----------------------------------------------------------

	public static Object parse(String src) {
		Parser p = new Parser(src);
		p.skipWs();
		Object v = p.readValue();
		p.skipWs();
		if (p.pos != src.length()) {
			throw new JsonException("Trailing garbage at position " + p.pos);
		}
		return v;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(String src) {
		Object v = parse(src);
		if (!(v instanceof Map)) {
			throw new JsonException("Expected JSON object at root, got " + (v == null ? "null" : v.getClass().getSimpleName()));
		}
		return (Map<String, Object>) v;
	}

	private static final class Parser {
		private final String src;
		private int pos = 0;

		Parser(String src) {
			this.src = src;
		}

		void skipWs() {
			while (pos < src.length()) {
				char c = src.charAt(pos);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
					pos++;
				} else {
					break;
				}
			}
		}

		Object readValue() {
			skipWs();
			if (pos >= src.length()) {
				throw new JsonException("Unexpected EOF at position " + pos);
			}
			char c = src.charAt(pos);
			switch (c) {
				case '{': return readObject();
				case '[': return readArray();
				case '"': return readString();
				case 't': case 'f': return readBool();
				case 'n': return readNull();
				default:
					if (c == '-' || (c >= '0' && c <= '9')) {
						return readNumber();
					}
					throw new JsonException("Unexpected char '" + c + "' at " + pos);
			}
		}

		Map<String, Object> readObject() {
			expect('{');
			Map<String, Object> out = new LinkedHashMap<String, Object>();
			skipWs();
			if (peek() == '}') {
				pos++;
				return out;
			}
			while (true) {
				skipWs();
				String key = readString();
				skipWs();
				expect(':');
				Object val = readValue();
				out.put(key, val);
				skipWs();
				char c = peek();
				if (c == ',') {
					pos++;
					continue;
				}
				if (c == '}') {
					pos++;
					return out;
				}
				throw new JsonException("Expected , or } at " + pos);
			}
		}

		List<Object> readArray() {
			expect('[');
			List<Object> out = new ArrayList<Object>();
			skipWs();
			if (peek() == ']') {
				pos++;
				return out;
			}
			while (true) {
				out.add(readValue());
				skipWs();
				char c = peek();
				if (c == ',') {
					pos++;
					continue;
				}
				if (c == ']') {
					pos++;
					return out;
				}
				throw new JsonException("Expected , or ] at " + pos);
			}
		}

		String readString() {
			expect('"');
			StringBuilder sb = new StringBuilder();
			while (pos < src.length()) {
				char c = src.charAt(pos++);
				if (c == '"') return sb.toString();
				if (c == '\\') {
					if (pos >= src.length()) throw new JsonException("Unterminated escape at " + pos);
					char e = src.charAt(pos++);
					switch (e) {
						case '"': sb.append('"'); break;
						case '\\': sb.append('\\'); break;
						case '/': sb.append('/'); break;
						case 'n': sb.append('\n'); break;
						case 't': sb.append('\t'); break;
						case 'r': sb.append('\r'); break;
						case 'b': sb.append('\b'); break;
						case 'f': sb.append('\f'); break;
						case 'u':
							if (pos + 4 > src.length()) throw new JsonException("Bad \\u at " + pos);
							sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
							pos += 4;
							break;
						default: throw new JsonException("Bad escape \\" + e + " at " + pos);
					}
				} else {
					sb.append(c);
				}
			}
			throw new JsonException("Unterminated string");
		}

		Object readNumber() {
			int start = pos;
			if (peek() == '-') pos++;
			while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
			boolean isFloat = false;
			if (pos < src.length() && src.charAt(pos) == '.') {
				isFloat = true;
				pos++;
				while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
			}
			if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
				isFloat = true;
				pos++;
				if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
				while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
			}
			String s = src.substring(start, pos);
			if (isFloat) return Double.valueOf(s);
			return Long.valueOf(s);
		}

		Boolean readBool() {
			if (src.regionMatches(pos, "true", 0, 4)) { pos += 4; return Boolean.TRUE; }
			if (src.regionMatches(pos, "false", 0, 5)) { pos += 5; return Boolean.FALSE; }
			throw new JsonException("Bad bool at " + pos);
		}

		Object readNull() {
			if (src.regionMatches(pos, "null", 0, 4)) { pos += 4; return null; }
			throw new JsonException("Bad null at " + pos);
		}

		void expect(char c) {
			if (pos >= src.length() || src.charAt(pos) != c) {
				throw new JsonException("Expected '" + c + "' at " + pos);
			}
			pos++;
		}

		char peek() {
			if (pos >= src.length()) throw new JsonException("Unexpected EOF");
			return src.charAt(pos);
		}
	}

	// ---- Emit -----------------------------------------------------------

	public static String emit(Object v) {
		StringBuilder sb = new StringBuilder();
		emitInto(sb, v, 0);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static void emitInto(StringBuilder sb, Object v, int indent) {
		if (v == null) { sb.append("null"); return; }
		if (v instanceof Boolean) { sb.append(((Boolean) v).booleanValue() ? "true" : "false"); return; }
		if (v instanceof Number) {
			if (v instanceof Double || v instanceof Float) {
				sb.append(v.toString());
			} else {
				sb.append(((Number) v).longValue());
			}
			return;
		}
		if (v instanceof String) { emitString(sb, (String) v); return; }
		if (v instanceof Map) {
			Map<String, Object> m = (Map<String, Object>) v;
			if (m.isEmpty()) { sb.append("{}"); return; }
			sb.append("{\n");
			int i = 0;
			for (Map.Entry<String, Object> e : m.entrySet()) {
				pad(sb, indent + 1);
				emitString(sb, e.getKey());
				sb.append(": ");
				emitInto(sb, e.getValue(), indent + 1);
				if (++i < m.size()) sb.append(",");
				sb.append("\n");
			}
			pad(sb, indent);
			sb.append("}");
			return;
		}
		if (v instanceof List) {
			List<Object> l = (List<Object>) v;
			if (l.isEmpty()) { sb.append("[]"); return; }
			sb.append("[\n");
			for (int i = 0; i < l.size(); i++) {
				pad(sb, indent + 1);
				emitInto(sb, l.get(i), indent + 1);
				if (i < l.size() - 1) sb.append(",");
				sb.append("\n");
			}
			pad(sb, indent);
			sb.append("]");
			return;
		}
		throw new JsonException("Cannot emit " + v.getClass().getName());
	}

	private static void emitString(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"':  sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", Integer.valueOf((int) c)));
					} else {
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}

	private static void pad(StringBuilder sb, int n) {
		for (int i = 0; i < n; i++) sb.append("  ");
	}

	// ---- Typed helpers (kept tiny on purpose) ---------------------------

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asMap(Object v) {
		if (v == null) return null;
		if (v instanceof Map) return (Map<String, Object>) v;
		throw new JsonException("Expected object, got " + v.getClass().getSimpleName());
	}

	@SuppressWarnings("unchecked")
	public static List<Object> asList(Object v) {
		if (v == null) return null;
		if (v instanceof List) return (List<Object>) v;
		throw new JsonException("Expected array, got " + v.getClass().getSimpleName());
	}

	public static String asString(Object v, String dflt) {
		if (v == null) return dflt;
		if (v instanceof String) return (String) v;
		return v.toString();
	}

	public static int asInt(Object v, int dflt) {
		if (v == null) return dflt;
		if (v instanceof Number) return ((Number) v).intValue();
		if (v instanceof String) {
			try { return Integer.parseInt((String) v); } catch (NumberFormatException nfe) { return dflt; }
		}
		return dflt;
	}

	public static boolean asBool(Object v, boolean dflt) {
		if (v == null) return dflt;
		if (v instanceof Boolean) return ((Boolean) v).booleanValue();
		return dflt;
	}

	public static final class JsonException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public JsonException(String msg) { super(msg); }
	}
}
