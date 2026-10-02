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
| [THUOCL](https://github.com/thunlp/THUOCL) | MIT | 领域词表（IT、医学、法律、成语等） |
| [pinyin-data](https://github.com/mozillazg/pinyin-data) | MIT | 汉字读音及其常用度排序 |
| [pypinyin](https://github.com/mozillazg/python-pinyin) `phrases_dict.json` | MIT | **词条**读音：多音字词按词定音 |
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | MIT | 口语常用词及其语料排名（用于让"怎么样""今天"这类词排在行业词前面） |
| [Unihan](https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip)（`kHanyuPinlu` / `kMandarin`） | Unicode License v3 | 每个**读音**的使用频率，决定一个字挂在哪些音节下 |
| [hugg95/university-data](https://github.com/hugg95/university-data) | MIT | 全国普通高等学校名单（2,631 条院校名，专名只用本音拼读） |
| [mumuy/data_location](https://github.com/mumuy/data_location) | MIT | 省 / 市 / 区县名（GB/T 2260 行政区划，3,433 条） |
| [chinese-xinhua](https://github.com/pwxcoo/chinese-xinhua) | MIT | 新华字典词条（2.6 万，带拼音）与成语（3 万，带拼音） -> 词表覆盖与词级读音 |
| [Chinese-Names-Corpus](https://github.com/wainshine/Chinese-Names-Corpus) | Apache-2.0 | 5 万条成语表 -> 词表覆盖 |
| [chinese-poetry](https://github.com/chinese-poetry/chinese-poetry) | MIT | 唐诗三百首 / 宋词三百首 -> 搭配模型（`--poetry`，默认关闭） |
| `tools/dictgen/raw/corpus_*.txt` | **本项目原创** | 现代口语词频（只用它给"本来就常见"的词加权，不据此引入生僻词） |

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
