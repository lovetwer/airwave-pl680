package com.airwave.pl680

import android.content.Context
import org.json.JSONArray
import java.util.Locale
import kotlin.math.roundToInt

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val tags: List<String>,
    val state: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val custom: Boolean = false,
)

data class LinkRec(val name: String, val url: String)

object Catalog {
    val TAGS: List<Pair<String, String>> = listOf(
        "新闻" to "news",
        "音乐" to "music",
        "老歌" to "oldies",
        "经济" to "economics",
        "电视" to "tv",
        "古典" to "classical",
        "故事" to "storytelling",
        "相声" to "crosstalk",
    )

    val REGIONS: List<String> = listOf(
        "全国", "北京", "上海", "华南", "江浙皖", "川渝", "东北", "华中", "华北", "西部",
    )

    private val PROVINCES: List<Pair<String, List<String>>> = listOf(
        "北京" to listOf("北京", "Beijing", "Peking"),
        "天津" to listOf("天津", "Tientsin", "Tianjin"),
        "河北" to listOf("河北", "石家庄", "唐山", "保定", "邯郸", "秦皇岛", "Hopei", "Hebei"),
        "山西" to listOf("山西", "太原", "大同", "临汾", "Shansi"),
        "内蒙古" to listOf("内蒙", "呼和浩特", "包头", "鄂尔多斯", "Mongolia"),
        "辽宁" to listOf("辽宁", "沈阳", "大连", "鞍山", "抚顺", "营口", "Liaoning", "Liaotung", "Dalian"),
        "吉林" to listOf("吉林", "长春", "延边", "Kirin"),
        "黑龙江" to listOf("黑龙江", "哈尔滨", "齐齐哈尔", "牡丹江", "大庆", "Amur", "Heilungkiang"),
        "上海" to listOf("上海", "Shanghai", "东广", "东方广播", "第一财经", "浦江", "魔都"),
        "江苏" to listOf("江苏", "南京", "苏州", "无锡", "常州", "南通", "徐州", "扬州", "镇江", "盐城", "Kiangsu"),
        "浙江" to listOf("浙江", "杭州", "宁波", "温州", "绍兴", "嘉兴", "金华", "台州", "湖州", "Chekiang"),
        "安徽" to listOf("安徽", "合肥", "芜湖", "蚌埠", "安庆", "黄山", "阜阳", "Anhwei"),
        "福建" to listOf("福建", "福州", "厦门", "泉州", "漳州", "莆田", "宁德", "Fukien"),
        "江西" to listOf("江西", "南昌", "赣州", "九江", "景德镇", "Kiangsi"),
        "山东" to listOf(
            "山东", "济南", "青岛", "烟台", "潍坊", "临沂", "淄博", "济宁", "泰安", "威海",
            "日照", "东营", "德州", "聊城", "滨州", "菏泽", "枣庄", "Shantung"
        ),
        "河南" to listOf("河南", "郑州", "洛阳", "开封", "南阳", "信阳", "Honan"),
        "湖北" to listOf("湖北", "武汉", "宜昌", "襄阳", "荆州", "十堰", "Hupei"),
        "湖南" to listOf(
            "湖南", "长沙", "株洲", "湘潭", "衡阳", "岳阳", "常德", "张家界", "益阳",
            "娄底", "郴州", "永州", "邵阳", "怀化", "湘西", "芒果", "金鹰", "潇湘", "Hunan"
        ),
        "广东" to listOf(
            "广东", "广州", "深圳", "珠海", "佛山", "东莞", "中山", "惠州", "江门", "汕头",
            "湛江", "肇庆", "韶关", "梅州", "河源", "阳江", "茂名", "云浮", "潮州", "揭阳",
            "汕尾", "羊城", "粤语", "珠江", "Kwangtung"
        ),
        "广西" to listOf("广西", "南宁", "桂林", "柳州", "北海", "贵港", "梧州", "Kwangsi"),
        "海南" to listOf("海南", "海口", "三亚", "Hainan"),
        "重庆" to listOf("重庆", "巴渝", "Chungking"),
        "四川" to listOf("四川", "成都", "绵阳", "德阳", "宜宾", "泸州", "乐山", "南充", "Szechuan"),
        "贵州" to listOf("贵州", "贵阳", "遵义", "安顺", "Kweichow"),
        "云南" to listOf("云南", "昆明", "大理", "丽江", "曲靖", "Yunnan"),
        "陕西" to listOf("陕西", "西安", "宝鸡", "咸阳", "渭南", "Shensi"),
        "甘肃" to listOf("甘肃", "兰州", "天水", "酒泉", "Kansu"),
        "宁夏" to listOf("宁夏", "银川", "Ninghsia"),
        "青海" to listOf("青海", "西宁", "Tsinghai"),
        "新疆" to listOf("新疆", "乌鲁木齐", "喀什", "伊犁", "Sinkiang"),
        "西藏" to listOf("西藏", "拉萨", "Xizang", "Tibet"),
        "港澳" to listOf("香港", "澳门", "Hong Kong", "Macau", "Macao", "RTHK", "濠江", "凤凰"),
        "台湾" to listOf("台湾", "台北", "高雄", "台中", "Formosa"),
    )

    private val NATIONAL: List<String> = listOf(
        "中国之声", "中国交通", "中国国际", "中央人民", "中央广播电视", "CNR", "CCTV", "CGTN",
        "CRI", "China Plus", "环球资讯", "国际广播", "新闻联播", "中华之声", "神州", "华夏",
        "国家", "民族", "阅读之声", "文艺之声", "经典调频",
    )

    fun load(ctx: Context): List<Station> {
        val text = ctx.assets.open("stations.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val out = ArrayList<Station>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tags = o.optJSONArray("tags") ?: JSONArray()
            out.add(
                Station(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("urlResolved").ifBlank { o.optString("url") },
                    tags = List(tags.length()) { tags.optString(it) },
                    state = o.optString("state"),
                    codec = o.optString("codec"),
                    bitrate = o.optInt("bitrate"),
                    hls = o.optBoolean("hls"),
                    custom = o.optBoolean("custom"),
                )
            )
        }
        return out
    }

    private val TAG_CN = mapOf(
        "news" to "新闻", "music" to "音乐", "oldies" to "老歌", "economics" to "经济",
        "tv" to "电视伴音", "classical" to "古典", "storytelling" to "故事", "crosstalk" to "相声",
        "talk" to "谈话", "culture" to "文化", "entertainment" to "娱乐", "information" to "资讯",
        "instrumental" to "器乐", "pop" to "流行", "ambient" to "氛围", "relax" to "放松",
        "easy listening" to "轻音乐", "golden music" to "经典金曲", "local service" to "地方台",
    )

    fun tagCn(t: String): String = TAG_CN[t.lowercase()] ?: t

    fun provinceOf(st: Station): String {
        val hay = "${st.name} ${st.state}"
        if (st.state.isNotBlank()) {
            PROVINCES.firstOrNull { (_, kw) -> kw.any { st.state.contains(it) } }?.let { return it.first }
        }
        PROVINCES.firstOrNull { (_, kw) -> kw.any { hay.contains(it) } }?.let { return it.first }
        return ""
    }

    fun areaOf(st: Station): String {
        val p = provinceOf(st)
        return if (p.isNotEmpty()) p else "全国"
    }

    fun isNational(st: Station): Boolean = NATIONAL.any { st.name.contains(it) }

    fun matchesRegion(st: Station, key: String): Boolean {
        if (key == "全国") return isNational(st)
        val prov = provinceOf(st)
        return when (key) {
            "北京" -> prov == "北京"
            "上海" -> prov == "上海"
            "华南" -> prov in listOf("广东", "广西", "海南", "福建", "港澳", "台湾")
            "江浙皖" -> prov in listOf("江苏", "浙江", "安徽")
            "川渝" -> prov in listOf("四川", "重庆")
            "东北" -> prov in listOf("辽宁", "吉林", "黑龙江")
            "华中" -> prov in listOf("湖北", "湖南", "河南", "江西")
            "华北" -> prov in listOf("北京", "天津", "河北", "山西", "内蒙古", "山东")
            "西部" -> prov in listOf("陕西", "甘肃", "宁夏", "青海", "新疆", "西藏", "云南", "贵州")
            else -> true
        }
    }

    fun codecOf(st: Station): String = when {
        st.hls -> "HLS"
        st.bitrate > 0 -> "${st.bitrate}k"
        st.codec.isNotBlank() -> st.codec.uppercase()
        else -> "NET"
    }

    fun tagline(st: Station): String =
        if (st.tags.isEmpty()) "中国电台" else st.tags.take(2).joinToString(" / ") { tagCn(it) }

    fun linkStation(l: LinkRec): Station {
        val url = l.url.trim()
        val name = l.name.trim().ifBlank { url }
        return Station(
            id = "link:$url",
            name = name,
            url = url,
            tags = listOf("直链"),
            state = "直链",
            hls = url.lowercase().contains(".m3u8"),
            custom = true,
        )
    }

    private fun hashRatio(s: String): Double {
        var h = 0L
        for (c in s) h = (h * 31 + c.code) % 99991
        return (h % 1000) / 1000.0
    }

    /**
     * 返回 (显示数字, 模式)，和桌面版一致。
     * LCD 只有 4 位数码位（FM 的小数点固定在第 2、3 位），所以 FM 收在 87.50–99.95、
     * NET 收在 5900–9900，保证 4 位数字永远放得下、不会被截位。
     */
    fun freqOf(st: Station): Pair<String, String> {
        if (st.hls || st.id.startsWith("link:")) {
            val seed = if (st.url.isBlank()) st.id else st.url
            val v = 5900.0 + hashRatio(seed) * 4000.0
            val rounded = (v / 5.0).roundToInt() * 5
            return rounded.toString() to "NET"
        }
        val v = 87.5 + hashRatio(if (st.id.isEmpty()) st.name else st.id) * 12.45
        return String.format(Locale.US, "%.2f", v) to "FM"
    }

    fun digits4(text: String): IntArray {
        val d = text.filter { it.isDigit() }.map { it - '0' }
        return IntArray(4) { d.getOrElse(it) { 0 } }
    }
}
