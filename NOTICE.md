# 第三方资源声明

本项目本体代码以 MIT 协议发布（见 `LICENSE`，版权归 yuan3271）。以下列出构建与运行
过程中使用到的第三方资源及其许可。

## 运行期依赖

| 组件 | 许可 | 用途 |
| --- | --- | --- |
| AndroidX / Jetpack Compose / Material 3 | Apache-2.0 | UI 框架、Material 3 Expressive 主题 |
| OkHttp | Apache-2.0 | 语音识别与文本修正的 HTTP 调用 |
| Kotlin / kotlinx.coroutines | Apache-2.0 | 语言与协程运行时 |

## 词典与数据资产

`app/src/main/assets/pinyin_words.txt` 与 `pinyin_chars.txt` 由 `tools/dictgen` 离线生成，
生成过程使用：

| 来源 | 许可 | 用途 |
| --- | --- | --- |
| [jieba](https://github.com/fxsjy/jieba) `dict.txt` | MIT | 词条与词频 |
| [THUOCL](https://github.com/thunlp/THUOCL) | MIT | 领域词表（IT、医学、法律、饮食、动物、诗词、历史名人、汽车、地名） |
| [hsk30](https://github.com/ivankra/hsk30) | MIT | **日常词汇表**：HSK 3.0 的 11,092 条按等级给保底分（1 级＝每天都会说） |
| [pinyin-data](https://github.com/mozillazg/pinyin-data) | MIT | 汉字读音及其常用度排序 |
| [pypinyin](https://github.com/mozillazg/python-pinyin) `phrases_dict.json` | MIT | **词条**读音：多音字词按词定音 |
| [phrase-pinyin-data](https://github.com/mozillazg/phrase-pinyin-data) | MIT | 41 万条**词级读音**（可选，`--phrase-readings large`，默认关闭） |
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | MIT | 口语常用词及其语料排名（用于让"怎么样""今天"这类词排在行业词前面） |
| [Unihan](https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip)（`kHanyuPinlu` / `kMandarin`） | Unicode License v3 | 每个**读音**的使用频率，决定一个字挂在哪些音节下 |
| [hugg95/university-data](https://github.com/hugg95/university-data) | MIT | 全国普通高等学校名单（2,631 条院校名，专名只用本音拼读） |
| [mumuy/data_location](https://github.com/mumuy/data_location) | MIT | 省 / 市 / 区县名（GB/T 2260 行政区划，3,433 条） |
| [chinese-poetry](https://github.com/chinese-poetry/chinese-poetry) | MIT | 唐诗三百首 / 宋词三百首 -> 搭配模型（`--poetry`，默认关闭） |
| [Tatoeba](https://tatoeba.org) 中文句子（`cmn` 导出快照，89102 句） | CC BY 2.0 FR（仅署名） | **日常词库第二层**：给"真的有人写进日常句子"的词保底分（**默认启用**，见下）；只统计词频，句子文本不入库 |
| `tools/dictgen/raw/corpus_*.txt` | **本项目原创** | 现代口语词频（只用它给"本来就常见"的词加权，不据此引入生僻词） |

**符号与表情**：`app/src/main/assets/emoji.txt` 由 `tools/dictgen/build_emoji.py` 从
[iamcal/emoji-data](https://github.com/iamcal/emoji-data)（MIT）生成，分组沿用 Unicode 自己的
emoji 分组（表情 / 人物 / 动物 / 食物 / 出行 / 活动 / 物品 / 符号 / 旗帜）；`Component`
（肤色、发色等修饰件）不下发——它们单独出现只会打出看不见的东西。全角 / 半角符号表是人工
整理的常用集合（见 `KeyboardLayouts.kt`），不来自任何第三方表。

**英文词表**：`EnglishSchoolWords.kt`（英文补全与中文模式下的英文词所用的**小初高到高考**词表）
由 `tools/wordgen/build_english_words.py` 从
[KyleBing/english-vocabulary](https://github.com/KyleBing/english-vocabulary)（BSD-3-Clause）生成：
小学取人教 PEP 三~六年级词头，初中 / 高中取中考 / 高考词汇表词头，只保留单个词、统一小写。
再分发时请一并保留该项目与作者的许可声明，许可证全文随源保留在
`tools/wordgen/LICENSE-english-vocabulary.txt`。

`EnglishEverydayWords.kt`（**日常英文词**，英文补全的第二层）由
`tools/wordgen/build_english_everyday.py` 从 [Tatoeba](https://tatoeba.org) 的**英文句子导出**
（`eng_sentences.tsv.bz2`，203.8 万句，CC BY 2.0 FR，仅署名）统计生成：分词数数、按出现次数
排序取前 4,000 个词。与中文那边的 Tatoeba 日常词层同一个口径——**只发布词与出现次数，句子
原文不入库、不随应用分发**。同一份 Tatoeba 数据在 `NOTICE` 的上文（中文句子）已经登记。

三条口径写在脚本里：出现次数少于 4 次的词不收；单字母与超过 16 个字母的串不收；**Tatoeba
里的人物名不收**（判据是"小写开头占比 < 5%"，`tom` 出现 41 万次里只有 9 次小写；`I'm`、
`Let's`、`Where's` 这类缩写与 `facebook`、`youtube`、`wifi` 这类现代生活词网开一面）。
这一条滤掉 200 多个词，几乎全是人名与地名（`sami`、`ziri`、`layla`、`fadil`…）。

> **实测**（`tools/wordgen/build_english_everyday.py` 自带指标，训练 / 留出按句子 id 切 9:1）：
> 留出集里的词次覆盖率 **71.2% → 87.6%**；最常用的 1,000 个日常词，取前 4 个字母当输入、
> 按补全的既有规则取前 48 条候选，词本身可达率 **67.1% → 100%**。词表合计 4,081 → 5,739 词，
> 中文模式下一次 46 字母缓冲的时延 0.4 → 0.5 ms（远低于 60 ms 的测试预算）。

> 成语表（chinese-xinhua 的 `idiom.json`、THUOCL 的成语表、5 万条成语表）**不进词表**。
> 词表要的是"人们天天打的词"，成语是另一件事；实测也确实是负收益（撤掉它们并把日常词汇表
> 接进来之后，手挑 31 句不变，见 `PLAN.md` 的 0.2.35 一节）。新华字典的词语表（`ci.json`，
> 26 万条）同样评估后不用：把它的键名修正、真正读进来之后，广谱回归台从 453 掉到 421，
> 并撞坏两条既有测试——词典收词不等于有人打。

> **日常词库第二层**（**默认启用**，`--no-tatoeba-floor` 关）：HSK 3.0 那一层只有 11,092 条，
> 很多天天在说的词根本不在里面（`先去`、`橙子`、`合出`、`去逛` 都查无此词），于是 `xianqu`
> 的首选是地名 `西安区`、`chengzi` 的首选是 `城子`。用 Tatoeba 的 8.9 万句日常话给"至少出现过
> 4 次"的词一道保底分（`TATOEBA_FLOOR_BASE = 650`）能把这一层从 4,283 个词扩到两万多个：
> `tools/tune/everyday.py` 的日常词可打性 **72.6% → 84.4%**。
>
> 代价是长句：广谱台全拼 453 → 412、九键二字词 415 → 458、首字母 82 → 78。底分调高（`--tatoeba-floor-base 800`）
> 会把日常词推到 85.2%、首字母推到 88（比基线还高），但全拼掉到 382。Tatoeba 是用户投稿、
> 没人校对，"口语 / 错写"的比重比规范语料高，所以 `干嘛`（21 次）压过了 `干吗`（5 次），两条
> 锁这个选择的用例跟着改成 `干嘛`——那条规则本来就把这个选择交给语料。全部实测数据留在
> `build_dict.py` 的 `TATOEBA_FLOOR_BASE` 一节。

生成的资产是上述数据的衍生作品，再分发时请一并保留本声明。词库文件本身不包含任何
上游项目的源代码。

> 逐字读音拼不出正确的词音：`音乐` 的 `乐` 是 `yuè`，`重庆` 的 `重` 是 `chóng`，
> 而 pinyin-data 给的是每个字的常用读音，生成时只能靠"逐字组合 + 降权"去逼近。pypinyin
> 的**词组**读音表把这件事讲清楚，所以多音字词改用它的词级读音（`pypinyin_phrases.txt`，
> 由 `tools/dictgen/prepare_pypinyin.py` 生成）。实测这修掉了 `音乐` 被读成 `yinle`、
> `重庆` 同时挂上 `zhongqing` 这类错误。

> 汉字读音来自 pinyin-data 的**逐字**读音，多字词的读音由逐字读音组合而来；个别字带有
> 罕用/古音（如 `盒` 的 `ān`），生成时为"全用本音读该音节的词"保留了优先权——当这类词
> 明显更常见时，靠异读拼出来的词会被丢弃（否则 `试剂盒` 会凭空得到一个 `shijian` 读音，
> 盖住 `时间`）。

> 曾评估过使用雾凇拼音（rime-ice）的词库，最终**未采用**：该项目为 GPL-3.0-only，
> 且其上游语料包含 CC BY-SA 与来源不明的数据，与本项目保持 MIT 的目标冲突。

> pinyin-data 按常用度排列一个字的读音，但没有频率：`乐` 因此只挂在 `le` 下（打 `yue` 出不来它），
> `谁` 只挂在 `shui` 下（`shei` 下一个字都没有），`得` 只挂在 `de` 下（`dei` 空着）。Unihan 的
> `kHanyuPinlu`（《现代汉语频率词典》的读音计数）与 `kMandarin`（习惯读音）补的就是这个数：
> 一个读音占到该字出现次数的 5% 以上、或是 kMandarin 认定的读音时，这个字会**多挂**一个音节
> （降权放置，原音节里的排位不动）。当前共 53 条次读音归位，`shei→谁`、`dei→得`、`hang→行`、
> `yue→乐`、`xie→血`、`de→地` 都在其中。`prepare_unihan.py` 负责抽取，Unihan 压缩包不随仓库分发。

> **自训练读音对应**（`tools/dictgen/train_readings.py` -> `clean/trained_readings.txt`，172 KiB，
> 入库、离线可复现；**默认启用**，`--no-trained-readings` 关）：没有词级读音的词原先靠"逐字读音的
> 笛卡尔积"猜（`一幢` 拼成 `yichuang`、`万佛峡` 拼成 `wanfuxia`、`长安` 拼成 `zhangan`），
> 这个开关改成用上面那几张 MIT 的**词→拼音对齐表**（pypinyin 47,102 ＋ phrase-pinyin-data
> 411,569 ＋ HSK 2.0 的 `numeric` 拼音）训练出的 `P(读音|字)` 与 `P(读音|字, 前字/后字)` 逐位
> 定音。产物只含统计量、不含任何上游原文；配一个加性平滑与首选读音先验，异读惩罚与"丢弃猜出来
> 的读音"那两条已有规则照旧生效。训练集与测试集不重叠的公平留出实验（41,158 个词）上，词级读音
> 准确率 94.9% → 97.3%（`python3 tools/tune/everyday.py --split-eval`）。

> 专名（院校、行政区划）来自上面两张 MIT 表。它们**不走语料频率**：`岳阳楼区`、`清华大学`
> 该被认识，但它在新闻语料里出现多少次跟"有没有人打它"没关系，所以单列成固定权重的来源
> （4,837 条进入词表；超过 8 字的院校名与词表同规则截断）。专名**只用本音**拼读：
> `中国人民大学` 的 `大` 不会因为 `大夫` 被拼成 dàixué，`内蒙古` 的 `内` 也不会读成 nà。

### 联想（语言模型）资产

`app/src/main/assets/pinyin_bigrams.txt` 由 `tools/dictgen/build_bigram.py` 离线生成，
它给出"上一个字/词之后最可能接什么"的搭配分数，有三个读者：整句解码、首字母简拼打分，
以及**打完一个词之后的联想条**。表里另有一行特殊左键 `^`，记的是"一句话以哪个单元开头"——
它是解码器唯一没有左邻的位置，补上它才有 马上 而不是 吗 开头（只对词生效，单字太容易是噪声）：

| 来源 | 许可 | 用途 |
| --- | --- | --- |
| `tools/dictgen/raw/corpus_*.txt` | **本项目原创** | 现代口语搭配统计；**语料本身只有几千字，但它是唯一反映"这个键盘希望怎么被用"的文本**，所以按等价规模加权，且它的词对永不因上限被淘汰 |
| [Tatoeba](https://tatoeba.org) 中文句子（`cmn` 导出快照，89102 句） | CC BY 2.0 FR（仅署名，无传染、无非商业限制） | 现代口语搭配统计（**默认启用**）；只统计计入次数，**句子文本不入库、不随应用分发** |
| [AISHELL-1](https://www.openslr.org/33/) 转写文本（经 [Alibaba-NLP/AISHELL-NER](https://github.com/Alibaba-NLP/AISHELL-NER) 以同一许可分发） | Apache-2.0 | 现代书面语/新闻搭配统计；**默认关闭**，见下 |
| [OpenCC](https://github.com/BYVoid/OpenCC) `TSCharacters` / `TSPhrases` | Apache-2.0 | 把公版繁体语料归一为简体（表在 `tools/dictgen/opencc/`，许可证随附） |
| Project Gutenberg 公版小说（红楼梦 / 三国演义 / 豆棚闲话） | Public Domain | 词、字搭配统计；**默认关闭**，文本不入库，只发布由它统计出的计数 |

Tatoeba 的句子由志愿者撰写/翻译，导出文件里逐句记录了作者与许可；本仓库只发布由它统计出的
整数词对分数，再分发时保留本声明即可满足署名要求。快照的下载与校验见
`tools/dictgen/fetch_corpora.sh`（会打印 sha256）。

> 两边的份量由权重决定：自撰语料按"一条等于若干条"加权（`DAILY_WEIGHT`），Tatoeba 按原始
> 次数计入。这不是随意取的数——直接按原始计数混合时，Tatoeba 的通用分布会把键盘自己的判断
> 淹掉（`今天|天气` 输给 `今天|是`，联想命中率掉到 8/14）。生成脚本的 `--variant-sweep`
> 会把若干权重各生成一张表，评测台逐张打分。

> 古典文本默认不参与统计。实测它们对现代口语搭配帮助有限，且繁简归一之前权重最高的是
> 「孔明|曰」「下回|分解」这类章回体套语。需要时用
> `python3 tools/dictgen/build_bigram.py --classics` 重新加入。

> AISHELL-1（14 万句朗读文本，Apache-2.0）同样**默认不参与统计**，但原因是实测：
> 它是"读出来的书面语"，不是"打出来的话"。加进来（`--aishell`）之后，全拼基准的第一名命中
> 28 → 27、联想条 top1 9 → 8，换回来的只有 top5 30 → 31；把置信门槛提到 8 次共现以上，
> 分数与不加时完全一致（说明它带来的高分词对本来就在语料里）。两个开关
> （`--aishell` / `--aishell-weight`）留着，`--variant-sweep` 每次都把它和现有表一起打分，
> 以后再评估不必重新找语料。

> 联想本质是语言模型问题，理论上最干净的解法是带平滑的 n-gram 或神经语言模型。评估后
> **未采用任何现成实现**：KenLM 为 LGPL-2.1、SRILM 为非商业许可，中文预训练语言模型多为
> 许可不明或非商业，中文维基语料为 CC BY-SA（传染）。为了保持 MIT，本项目改为在自有语料上
> 自训练一个带绝对折扣平滑的二元模型，分数直接写进上表的整数结果里。

## 图标

`app/src/main/java/com/yuan3271/cloudrift/ui/icons/` 下的图标是**本项目自绘**，由
`tools/icons/build_icons.py` 以几何方式生成，未使用任何第三方图标库的字形数据。
应用启动图标同样由该脚本生成（`res/drawable/ic_launcher_*.xml`），当前采用"云字标"
方案，其余三个候选方案保留在 `tools/design/launcher-preview.svg` 中备查。

之所以自绘：小米只开源了 MiSans 字体，没有开源 UI 图标库；华为的 HarmonyOS Symbol
图标库为专有资源，OpenHarmony 应用仓库中仅含少量系统设置图标，无法覆盖输入法需求；
国内可获取的开源图标库（TDesign、Arco、Ant Design）均为几何描边风格，与 HyperOS /
鸿蒙的圆润实心观感差异较大。图标规范与生成方式记录在 `tools/icons/build_icons.py`
的文件头。
