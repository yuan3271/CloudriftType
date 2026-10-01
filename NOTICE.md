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

生成的资产是上述数据的衍生作品，再分发时请一并保留本声明。词库文件本身不包含任何
上游项目的源代码。

> 曾评估过使用雾凇拼音（rime-ice）的词库，最终**未采用**：该项目为 GPL-3.0-only，
> 且其上游语料包含 CC BY-SA 与来源不明的数据，与本项目保持 MIT 的目标冲突。

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
