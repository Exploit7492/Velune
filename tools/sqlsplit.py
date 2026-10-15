"""Split a SQL file into statements, respecting quotes, $$ bodies and comments."""


def split_sql(sql):
    out = []
    buf = []
    i = 0
    n = len(sql)
    insq = indollar = inline = inblock = False
    while i < n:
        ch = sql[i]
        nx = sql[i + 1] if i + 1 < n else ''
        if inline:
            if ch == '\n':
                inline = False
                buf.append(ch)
            i += 1
            continue
        if inblock:
            if ch == '*' and nx == '/':
                inblock = False
                i += 2
                continue
            i += 1
            continue
        if insq:
            buf.append(ch)
            if ch == "'" and nx == "'":
                buf.append(nx)
                i += 2
                continue
            if ch == "'":
                insq = False
            i += 1
            continue
        if indollar:
            if sql.startswith('$$', i):
                indollar = False
                buf.append('$$')
                i += 2
                continue
            buf.append(ch)
            i += 1
            continue
        if ch == '-' and nx == '-':
            inline = True
            i += 2
            continue
        if ch == '/' and nx == '*':
            inblock = True
            i += 2
            continue
        if ch == "'":
            insq = True
            buf.append(ch)
            i += 1
            continue
        if sql.startswith('$$', i):
            indollar = True
            buf.append('$$')
            i += 2
            continue
        if ch == ';':
            out.append(''.join(buf))
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    if ''.join(buf).strip():
        out.append(''.join(buf))
    return [s for s in out if s.strip()]
