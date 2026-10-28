"""把 `_pack/_beast_stats.json` 生成 `encounter/BeastStats.java`。

数据来自 `gen_beast_stats.py`（对本体 jar 字节码的抽取），
生成结果由 `audit_bestiary.py` 复核 —— 手改这张表会被审计打回。
"""
import json
import os

ROOT = r'D:\daima\mcmod\天意'
SRC = os.path.join(ROOT, '_pack', '_beast_stats.json')
OUT = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'guzhenren', 'tianyi', 'encounter',
                   'BeastStats.java')

HEADER = '''package net.guzhenren.tianyi.encounter;

import java.util.List;

/**
 * 蛊兽原生属性表（<b>由脚本生成，勿手改</b>）。
 *
 * <h2>数据从哪来</h2>
 * 逐个读本体 jar 里实体类的 {@code registerAttributes()} 字节码，取
 * {@code Attributes.MAX_HEALTH} / {@code ARMOR} / {@code ATTACK_DAMAGE} 的常量。
 * 生成器：{@code _pack/gen_beast_stats.py}；复核：{@code _pack/audit_bestiary.py}。
 *
 * <h2>这些数值的含义</h2>
 * 是 {@code createAttributes()} 的<b>初始值</b>，<b>不含</b>本体词条、转数威压与状态效果 ——
 * 实机数值会更高，所以面板/劝告里写的是"原生"数值。
 */
public final class BeastStats {
    /** 一只兽的原生属性。 */
    public record Row(String id, String name, int tier, String dao,
                      double hp, double armor, double atk) {}

    private static final List<Row> ROWS = List.of(
'''

FOOTER = '''    );

    private BeastStats() {
    }

    /** 全部行（按阶、id 排序）。 */
    public static List<Row> all() {
        return ROWS;
    }

    public static int size() {
        return ROWS.size();
    }
}
'''


def main():
    rows = json.load(open(SRC, encoding='utf-8'))
    rows.sort(key=lambda r: (r['tier'], r['id']))
    body = []
    for r in rows:
        body.append(
            f'            new Row("{r["id"]}", "{r["name"]}", {r["tier"]}, "{r["dao"]}", '
            f'{r["hp"]:g}, {r["armor"]:g}, {r["atk"]:g})')
    text = HEADER + ',\n'.join(body) + '\n' + FOOTER
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('生成', OUT, len(rows), '行')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
