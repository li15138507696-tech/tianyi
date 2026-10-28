"""鎶婄敤鎴锋彁渚涚殑铚樿洓鍥鹃檷閲囨牱鎴?16x16 閫忔槑璐村浘銆?
杈撳叆鏄?1254x1254 鐨勭櫧搴曞儚绱犵敾锛涜緭鍑哄繀椤绘槸灏忓昂瀵?RGBA锛?  - 杩戠櫧鍍忕礌杞负鍏ㄩ€忔槑锛堝師鍥炬病鏈?alpha 閫氶亾锛?  - 鐢?BOX锛堝尯鍩熷钩鍧囷級闄嶉噰鏍凤紝閬垮厤 LANCZOS 鎶婂儚绱犺竟缂樼硦鎴愪竴鍥?  - 涓嶅仛浠讳綍閲嶇粯锛氬舰鐘躲€佷激鐥曚綅缃叏閮ㄤ繚鐣欑敤鎴峰師鍥?"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/tianyi/textures/item"
OUT.mkdir(parents=True, exist_ok=True)

JOBS = [
    # (婧愬浘, 杈撳嚭鍚?  绗竴寮犲甫璧ょ孩鏂滅棔 鈫?鍙楁崯锛涚浜屽紶骞插噣 鈫?瀹屽ソ
    (r"C:\Users\ADMIN\.dsh\attachments\v1\objects\75"
     r"\7594850937fd38b0d602758a0673e1cc2c7a4c89be8590e955c498eba0da0a5b",
     "suminggu_damaged.png"),
    (r"C:\Users\ADMIN\.dsh\attachments\v1\objects\6c"
     r"\6cb0cd61d662db99e5318e69675c4cb939fab84821e6dff369709d676d0d3dc7",
     "suminggu.png"),
]

WHITE = 200  # 鍒拌繖涓寒搴︿互涓婅涓鸿儗鏅?

def convert(src: str, dst: Path) -> None:
    im = Image.open(src).convert("RGB")
    small = im.resize((16, 16), Image.BOX)
    out = Image.new("RGBA", (16, 16))
    px = small.load()
    dst_px = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b = px[x, y]
            a = 0 if (r >= WHITE and g >= WHITE and b >= WHITE) else 255
            dst_px[x, y] = (r, g, b, a)
    out.save(dst, "PNG")
    # 鍥炶鏍￠獙锛岄伩鍏?鍐欎簡浣嗘病鍐欐垚"
    chk = Image.open(dst)
    assert chk.size == (16, 16), f"{dst.name} 灏哄涓嶅: {chk.size}"
    assert chk.mode == "RGBA", f"{dst.name} 涓嶆槸 RGBA: {chk.mode}"
    opaque = sum(1 for _, _, _, a in chk.getdata() if a)
    print(f"OK {dst.name}: 16x16 RGBA, {dst.stat().st_size} B, 涓嶉€忔槑鍍忕礌 {opaque}/256")


for src, name in JOBS:
    convert(src, OUT / name)
print("璐村浘宸茬敓鎴愶細", OUT)

