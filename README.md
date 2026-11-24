# EnchantX

原版附魔重做插件，支持 Purpur / Paper 26.3 和 Java 25。

附魔台、铁砧、重铸台、拆解都保留，产出的是自己的一套词条。84 条词条分在 9 个池里，每条五个档位，档位内还有数值区间。单个 jar，不依赖其他插件。

## 安装

1. 把 `EnchantX-0.2.1.jar` 放进 `plugins/`。
2. 启动后生成 `plugins/EnchantX/config.yml` 和 `affixes/` 下的 9 个池文件。
3. 进服 `/ex help` 看命令。想试手感可以 `/ex give weapon 3 3` 拿几本词条书。

权限：`enchantx.use` 默认所有人，`enchantx.admin` 默认 OP。

## 附魔台模式

在 `config.yml` 的 `settings.mode` 里切：

- `AFFIX`：清空原版附魔，只出词条。适合新服或纯 RPG 服。
- `HYBRID`：默认。原版附魔照常，按 `hybrid-chance` 概率再挂一条词条，数值乘以 `hybrid-scale`。
- `VANILLA`：不动附魔台，只用重铸、拆解和词条书。

## 玩法

- 附魔台：等级越高越容易出高档词条。
- 铁砧：词条书砸装备可以重掷数值；两件装备合并时同名词条取高的那档。
- 重铸台 `/ex reforge`：左键词条升档，右键锁定，重掷只洗没锁的槽位。
- 拆解 `/ex salvage`：清空词条，按档位返还碎片。碎片存在玩家身上，不占背包。
- 词条书来源：杀怪、箱子战利品、钓鱼、碎片购买、管理员发放。

## 词条池

| 池 | 数量 | 说明 |
| --- | --- | --- |
| weapon | 22 | 武器通用，含两条诅咒 |
| tool | 10 | 工具 |
| armor | 12 | 通用防具 |
| helmet | 5 | 头盔专属 |
| chest | 5 | 胸甲专属 |
| legs | 5 | 护腿专属 |
| boots | 5 | 靴子专属 |
| ranged | 11 | 弓弩 |
| universal | 9 | 任意装备 |

掷点时按装备部位汇总候选，部位专属池互不重叠。`conflicts` 里配了互斥，同类效果不会同时出现。

## 槽位

按材质决定可装词条数量：木、金、皮革 1 个；石、锁链、铁 2 个；钻石 3 个；下界合金 4 个。未知材质用 `slots.default`。改完 `/ex reload` 生效。

## 命令

```
/ex info                          查看手持装备的词条、槽位、碎片
/ex list [池] [页]                词条图鉴
/ex reforge                       打开重铸台
/ex reroll [次数]                 不开界面直接重掷
/ex salvage                       拆解手持装备
/ex buy <池|词条id|any> [档]       用碎片买词条书
/ex give <词条id|池> [档] [数量]    发词条书（管理员）
/ex fragments <add|set|take> <n>   改碎片（管理员）
/ex strip                         清空词条，不返还（管理员）
/ex reload                        重载配置和词条库（管理员）
```

别名 `/ex`、`/cx`。

## 配置

`config.yml` 里常改的：

```yaml
books:
  drop-chance: 0.06
  loot-chance: 0.10
  fishing-chance: 0.08
  buy-cost: 18
  buy-tier: 2
```

- `settings.global-scale`：所有词条数值的全局系数。
- `settings.hybrid-scale`：HYBRID 模式额外词条的打折系数。
- `costs.*`：重铸、升档、买书的价格。
- 暴击、减伤、吸血这些上限写在代码里，要改得动源码。

## 数据

词条存在物品的 PDC 里，单键 `enchantx:affix`，格式是紧凑字符串。卸掉插件后物品不会坏，只是词条不再生效。

属性类词条用 AttributeModifier，事件类走缓存，战斗时只读内存。lore 只摘自己加的那几行，不会动别的插件写的内容。

## 已知限制

- 砂轮不处理词条，拆解请用 `/ex salvage` 或重铸台。
- 连锁挖掘会逐块检查保护插件，比直接 break 慢一点，工具快坏时会提前停。
- 词条书砸装备的数值是重新 roll 的，可能越砸越差。不接受的话把 `costs.upgrade` 调低，或者只用重铸台升档。

## 构建

```powershell
& 'D:\scoop\apps\gradle\current\bin\gradle.bat' -p 'G:\插件\原版附魔扩展' build --console=plain `
  '-Dorg.gradle.java.installations.paths=D:\mo-jdk\jdk-25.0.5+8'
```

产物在 `build/libs/`。
