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
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | MIT | 口语常用词及其语料排名（用于让"怎么样""今天"这类词排在行业词前面） |
| `tools/dictgen/raw/corpus_*.txt` | **本项目原创** | 现代口语词频（只用它给"本来就常见"的词加权，不据此引入生僻词） |

生成的资产是上述数据的衍生作品，再分发时请一并保留本声明。词库文件本身不包含任何
上游项目的源代码。

> 汉字读音来自 pinyin-data 的**逐字**读音，多字词的读音由逐字读音组合而来；个别字带有
> 罕用/古音（如 `盒` 的 `ān`），生成时为"全用本音读该音节的词"保留了优先权——当这类词
> 明显更常见时，靠异读拼出来的词会被丢弃（否则 `试剂盒` 会凭空得到一个 `shijian` 读音，
> 盖住 `时间`）。

> 曾评估过使用雾凇拼音（rime-ice）的词库，最终**未采用**：该项目为 GPL-3.0-only，
> 且其上游语料包含 CC BY-SA 与来源不明的数据，与本项目保持 MIT 的目标冲突。

### 联想（语言模型）资产

`app/src/main/assets/pinyin_bigrams.txt` 由 `tools/dictgen/build_bigram.py` 离线生成，
它给出"上一个字/词之后最可能接什么"的搭配分数，有三个读者：整句解码、首字母简拼打分，
以及**打完一个词之后的联想条**：

| 来源 | 许可 | 用途 |
| --- | --- | --- |
| `tools/dictgen/raw/corpus_*.txt` | **本项目原创** | 现代口语搭配统计（**默认只用这一类**） |
| [OpenCC](https://github.com/BYVoid/OpenCC) `TSCharacters` / `TSPhrases` | Apache-2.0 | 把公版繁体语料归一为简体（表在 `tools/dictgen/opencc/`，许可证随附） |
| Project Gutenberg 公版小说（红楼梦 / 三国演义 / 豆棚闲话） | Public Domain | 词、字搭配统计；**默认关闭**，文本不入库，只发布由它统计出的计数 |

> 古典文本默认不参与统计。实测它们对现代口语搭配帮助有限，且繁简归一之前权重最高的是
> 「孔明|曰」「下回|分解」这类章回体套语。需要时用
> `python3 tools/dictgen/build_bigram.py --classics` 重新加入。

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
