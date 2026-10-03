"""从 APK 里读出真实的 versionCode / versionName。

## 为什么要写这个（DFW-117 2026-10-02）
用户上传 1.0.1 后手机收不到更新。根因：后台的「现在 +1」算的是**上一条记录的
versionCode + 1**，它根本不知道用户手机上装的是哪一版 —— 上一条是 10027，
填成 10028，而用户装的是 10034 → 10028 < 10034 → 软件判定"这更新比我还旧"。

正解：**上传的那个 APK 本身就是新版本**，它的 versionCode 才是真值。
所以直接读 APK 里的二进制 AndroidManifest.xml。

## 为什么不用 aapt / androguard
服务器上没有 aapt（那是 Android SDK 的东西），也不该为了一个字段装几百 MB 的依赖。
二进制 AXML 的格式是稳定且公开的，这里只解析到「属性」这一层，够用。
"""
import struct, zipfile

def _len(buf, off):
    """**UTF-8 串**的变长长度编码：首字节最高位置 1 表示占两字节。

    注意：UTF-16 串**不用**这个 —— 它固定是 2 字节小端 u16（见 _strings）。
    我一开始两种都用这个函数，结果 UTF-16 串整体错位，
    解出来是「琀栀攀洀攀」这种乱码（把 `05 00` 当成了变长长度）。
    """
    v = buf[off]; off += 1
    if v & 0x80:
        v = ((v & 0x7F) << 8) | buf[off]; off += 1
    return v, off


def _strings(buf, off):
    """解析字符串池，返回 (字符串列表, 池结束偏移)。"""
    # 字段顺序：stringCount, styleCount, flags, stringsStart, stylesStart
    # （原来这里变量名错位了 —— cnt 拿到的是 styleCount(=0)，导致字符串列表恒为空）
    cnt, _style_cnt, flags, start, _styles_start = struct.unpack_from('<IIIII', buf, off + 8)
    utf8 = bool(flags & 0x100)
    offs = [struct.unpack_from('<I', buf, off + 28 + 4 * i)[0] for i in range(cnt)]
    base = off + start
    out = []
    for o in offs:
        p = base + o
        if utf8:
            _n, p = _len(buf, p)         # UTF-16 字符数（UTF-8 串才有，用不上）
            nb, p = _len(buf, p)         # 字节数
            out.append(buf[p:p + nb].decode('utf-8', 'replace'))
        else:
            # UTF-16：长度是固定 2 字节小端 u16，后面跟 n*2 字节 + 2 字节结尾的 0
            n = struct.unpack_from('<H', buf, p)[0]
            p += 2
            out.append(buf[p:p + n * 2].decode('utf-16-le', 'replace'))
    # 池长度在头部第 2 个字段
    size = struct.unpack_from('<I', buf, off + 4)[0]
    return out, off + size


def _attr_value(buf, off, strings):
    """读一个属性值。只关心字符串与整数两类，其余返回 None。"""
    size, res0, typ = struct.unpack_from('<HBB', buf, off)
    data = struct.unpack_from('<I', buf, off + 4)[0]
    if typ == 0x03:                       # 字符串
        return strings[data] if data < len(strings) else None
    if typ == 0x10:                       # 十进制整数
        return data if data < 0x80000000 else data - 0x100000000
    if typ == 0x11:                       # 十六进制整数
        return data
    return None


def read_version(apk_path):
    """返回 (versionCode, versionName)；读不出来就是 (None, None)。

    **失败必须是 None 而不是抛异常** —— 它是增强，不能让上传因为读不出元数据而失败。
    """
    try:
        with zipfile.ZipFile(apk_path) as z:
            buf = z.read('AndroidManifest.xml')
        # 跳过头 8 字节，然后依次是字符串池、资源表、命名空间、标签
        off = 8
        strings, off = _strings(buf, off)
        # 资源表（可能没有）
        while off + 8 <= len(buf):
            typ, _hdr, size = struct.unpack_from('<HHI', buf, off)
            if typ == 0x0180:             # RES_XML_RESOURCE_MAP
                off += size
                continue
            break
        code = name = None
        while off + 8 <= len(buf):
            typ, _hdr, size = struct.unpack_from('<HHI', buf, off)
            if size == 0:
                break
            if typ == 0x0102:             # START_TAG
                _ns, _nm, attr_start, attr_size, attr_count = struct.unpack_from('<IIHHH', buf, off + 16)
                for i in range(attr_count):
                    # attributeStart 是相对 ResXMLTree_attrExt 的偏移；
                    # attrExt 从 off + headerSize 开始（ResXMLTree_node 头是 16 字节：
                    # type+headerSize+size+lineNumber+comment）
                    a = off + 16 + attr_start + i * attr_size
                    ns_i, name_i, _raw = struct.unpack_from('<III', buf, a)
                    attr = strings[name_i] if name_i < len(strings) else ''
                    if attr == 'versionCode':
                        code = _attr_value(buf, a + 12, strings)
                    elif attr == 'versionName':
                        name = _attr_value(buf, a + 12, strings)
                if code is not None or name is not None:
                    break
            off += size
        return code, name
    except Exception:
        return None, None


if __name__ == '__main__':
    import sys
    print(read_version(sys.argv[1]))
