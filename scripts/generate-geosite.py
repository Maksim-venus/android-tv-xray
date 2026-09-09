#!/usr/bin/env python3
"""Build a compact Xray geosite.dat for the APK default bundle.

Weekly runtime updates replace this with the full Loyalsoldier geosite.dat.
Format: v2ray routercommon GeoSiteList protobuf (no extra deps).
"""
from __future__ import annotations

from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "core-xray/src/main/assets/xray/geosite.dat"

# Domain.Type: Plain=0, Regex=1, Domain(suffix)=2, Full=3
DOMAIN = 2
FULL = 3

CN = [
    "baidu.com", "baidu.cn", "bdstatic.com", "bdimg.com",
    "qq.com", "gtimg.com", "qcloud.com", "tencent.com", "weixin.com", "wechat.com",
    "taobao.com", "tmall.com", "alipay.com", "alipayobjects.com", "alicdn.com",
    "aliyun.com", "alibaba.com", "aliyuncs.com", "mmstat.com",
    "163.com", "126.com", "127.net", "netease.com",
    "sina.com.cn", "sina.com", "weibo.com", "weibo.cn",
    "jd.com", "360buyimg.com",
    "bilibili.com", "bilivideo.com", "hdslb.com", "biliapi.net",
    "douyin.com", "tiktok.com", "bytedance.com", "pstatp.com", "byteimg.com",
    "iqiyi.com", "youku.com", "pptv.com",
    "zhihu.com", "zhimg.com",
    "douban.com", "xiaohongshu.com",
    "ctrip.com", "12306.cn",
    "icbc.com.cn", "ccb.com", "abchina.com", "bankcomm.com",
    "mi.com", "xiaomi.com", "huawei.com", "honor.com", "oppo.com", "vivo.com",
    "meituan.com", "dianping.com", "ele.me",
    "pinduoduo.com", "yangkeduo.com",
    "csdn.net", "oschina.net", "gitee.com",
    "cnki.net", "gov.cn", "edu.cn", "ac.cn",
    "aliapp.org", "amap.com", "autonavi.com",
    "sohu.com", "ifeng.com", "cctv.com", "cntv.cn",
    "kuaishou.com", "kwai.com",
    "sogou.com", "360.cn", "qhimg.com",
    "sspai.com", "juejin.cn",
    "apple.com.cn", "icloud.com.cn",
    "msn.cn", "live.com",
]

NOT_CN = [
    "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
    "youtube.com", "ytimg.com", "googlevideo.com",
    "facebook.com", "fbcdn.net", "instagram.com", "cdninstagram.com",
    "twitter.com", "x.com", "twimg.com",
    "github.com", "githubusercontent.com", "githubassets.com",
    "cloudflare.com", "cloudflare-dns.com",
    "wikipedia.org", "wikimedia.org",
    "netflix.com", "nflxvideo.net",
    "telegram.org", "t.me",
    "openai.com", "chatgpt.com",
    "reddit.com", "twitch.tv",
    "discord.com", "discord.gg",
    "apple.com", "icloud.com", "mzstatic.com",
    "microsoft.com", "office.com", "live.com",
    "amazon.com", "amazonaws.com",
]

ADS = [
    "doubleclick.net", "googleadservices.com", "googlesyndication.com",
    "googletagmanager.com", "google-analytics.com",
    "umeng.com", "umengcloud.com",
    "adservice.google.com",
    "scorecardresearch.com",
    "adsystem.com",
]


def varint(n: int) -> bytes:
    out = bytearray()
    while n > 0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)
    return bytes(out)


def key(field: int, wire: int) -> bytes:
    return varint((field << 3) | wire)


def fld_bytes(field: int, data: bytes) -> bytes:
    return key(field, 2) + varint(len(data)) + data


def fld_str(field: int, s: str) -> bytes:
    return fld_bytes(field, s.encode("utf-8"))


def fld_varint(field: int, n: int) -> bytes:
    return key(field, 0) + varint(n)


def domain(value: str, kind: int = DOMAIN) -> bytes:
    return fld_varint(1, kind) + fld_str(2, value)


def geosite(code: str, domains: list[str]) -> bytes:
    body = fld_str(1, code)
    for d in domains:
        body += fld_bytes(2, domain(d))
    return body


def main() -> None:
    entries = [
        geosite("cn", CN),
        geosite("geolocation-!cn", NOT_CN),
        geosite("category-ads-all", ADS),
    ]
    blob = b"".join(fld_bytes(1, e) for e in entries)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(blob)
    print(f"wrote {OUT} ({len(blob)} bytes, cn={len(CN)} !cn={len(NOT_CN)} ads={len(ADS)})")


if __name__ == "__main__":
    main()
