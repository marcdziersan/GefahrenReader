import java.util.*;

/**
 * Kleiner, eigenständiger JSON-Parser (Objekt/Array/String/Number/Boolean/null).
 * Liefert Map<String,Object>, List<Object>, String, Double/Long, Boolean, null.
 * Keine externen Libraries erforderlich.
 */
class JSONMini {

    public static Object parse(String json) {
        return new Parser(json).parseValue();
    }

    /* --------- Inner Parser ---------- */
    private static class Parser {
        private final String s;
        private int i = 0;
        Parser(String src) { this.s = src; }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') { i += 4; return null; } // "null"
            return parseNumber();
        }

        Map<String,Object> parseObject() {
            Map<String,Object> m = new LinkedHashMap<>();
            expect('{'); skipWs();
            if (peek('}')) { i++; return m; }
            while (i < s.length()) {
                skipWs();
                String key = parseString();
                skipWs(); expect(':'); skipWs();
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                if (peek('}')) { i++; break; }
                expect(',');
            }
            return m;
        }

        List<Object> parseArray() {
            List<Object> a = new ArrayList<>();
            expect('['); skipWs();
            if (peek(']')) { i++; return a; }
            while (i < s.length()) {
                Object v = parseValue();
                a.add(v);
                skipWs();
                if (peek(']')) { i++; break; }
                expect(',');
            }
            return a;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i+3 < s.length()) {
                                String hex = s.substring(i, i+4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            }
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            return Boolean.FALSE;
        }

        Number parseNumber() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    i++;
                } else break;
            }
            String num = s.substring(start, i);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
                long l = Long.parseLong(num);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                return l;
            } catch (Exception e) {
                return 0;
            }
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }
        void expect(char ch) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != ch) {
                throw new RuntimeException("JSON: Zeichen '" + ch + "' erwartet bei Position " + i);
            }
            i++;
        }
        boolean peek(char ch) {
            skipWs();
            return i < s.length() && s.charAt(i) == ch;
        }
    }
}
