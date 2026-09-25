"""Small deterministic Java NBT codec used by structure generation/validation.

Only the standard tag types needed by our templates are written. No third-party
NBT dependency is required; gzip headers deliberately omit time and filenames.
"""
import gzip
import io
import struct

class Byte(int): pass
class Long(int): pass

def kind(value):
    if isinstance(value, Byte): return 1
    if isinstance(value, Long): return 4
    if isinstance(value, int): return 3
    if isinstance(value, str): return 8
    if isinstance(value, list): return 9
    if isinstance(value, dict): return 10
    raise TypeError(type(value))

def string(value):
    data=value.encode('utf-8')
    return struct.pack('>H',len(data))+data

def payload(value):
    tag=kind(value)
    if tag==1: return struct.pack('>b',value)
    if tag==3: return struct.pack('>i',value)
    if tag==4: return struct.pack('>q',value)
    if tag==8: return string(value)
    if tag==9:
        element=kind(value[0]) if value else 10
        assert all(kind(v)==element for v in value)
        return bytes([element])+struct.pack('>i',len(value))+b''.join(payload(v) for v in value)
    return b''.join(bytes([kind(v)])+string(k)+payload(v) for k,v in value.items())+b'\0'

def write(path, root):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(gzip.compress(b'\x0a\x00\x00'+payload(root),mtime=0))

def read(path):
    stream=io.BytesIO(gzip.decompress(path.read_bytes()))
    def number(fmt): return struct.unpack(fmt,stream.read(struct.calcsize(fmt)))[0]
    def text(): return stream.read(number('>H')).decode('utf-8')
    def value(tag):
        if tag==1: return number('>b')
        if tag==3: return number('>i')
        if tag==4: return number('>q')
        if tag==8: return text()
        if tag==9:
            element=number('>B'); count=number('>i')
            return [value(element) for _ in range(count)]
        if tag==10:
            result={}
            while (element:=number('>B')):
                key=text(); result[key]=value(element)
            return result
        raise ValueError(tag)
    assert number('>B')==10
    text()
    return value(10)
