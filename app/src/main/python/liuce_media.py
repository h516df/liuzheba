"""Small Android bridge around yt-dlp (Unlicense); no remote parsing service.

The two album adapters use the same upstream page/API metadata as yt-dlp.
They return actual album images, never the video thumbnail collection.
"""
import json
from urllib.parse import urlparse, urlunparse
from http.cookiejar import Cookie

from yt_dlp import YoutubeDL
from yt_dlp.extractor.xiaohongshu import XiaoHongShuIE
from yt_dlp.extractor.tiktok import DouyinIE
from yt_dlp.utils import js_to_json, ExtractorError
from yt_dlp.utils.traversal import traverse_obj


class QuietLogger:
    def debug(self, *_): pass
    def info(self, *_): pass
    def warning(self, *_): pass
    def error(self, *_): pass


class LiuceXhsIE(XiaoHongShuIE):
    IE_NAME = "liuce:xiaohongshu"

    def _real_extract(self, url):
        note_id = self._match_id(url)
        webpage = self._download_webpage(url, note_id)
        state = self._search_json(r'window\.__INITIAL_STATE__\s*=', webpage,
                                  'initial state', note_id, transform_source=js_to_json)
        note = traverse_obj(state, ('note', 'noteDetailMap', note_id, 'note')) or {}
        if note.get('type') == 'normal' or (note.get('imageList') and not note.get('video')):
            entries = []
            for image in note.get('imageList', []):
                address = image.get('urlDefault') or next((item.get('url') for item in image.get('infoList', [])
                                                        if item.get('imageScene') == 'WB_DFT'), None)
                if address:
                    entries.append({'url': address, 'kind': 'image', 'ext': 'jpg'})
            if not entries:
                raise ExtractorError('No album images available', expected=True)
            return {'id': note_id, 'title': note.get('title') or note.get('desc') or '小红书图集',
                    '_type': 'liuce_album', 'media': entries}
        return super()._real_extract(url)


class LiuceDouyinIE(DouyinIE):
    IE_NAME = "liuce:douyin"
    _VALID_URL = r'https?://(?:www\.)?douyin\.com/(?:video|note|photo)/(?P<id>[0-9]+)'

    def _real_extract(self, url):
        post_id = self._match_id(url)
        detail = traverse_obj(self._download_json(
            'https://www.douyin.com/aweme/v1/web/aweme/detail/', post_id,
            query={'aweme_id': post_id}, fatal=False), ('aweme_detail', {dict}))
        if not detail:
            raise ExtractorError('Platform requires a fresh browser session', expected=True)
        if detail.get('images'):
            media = []
            for image in detail['images']:
                addresses = image.get('url_list') or (image.get('display_image') or {}).get('url_list') or []
                if addresses:
                    media.append({'url': addresses[0], 'kind': 'image', 'ext': 'jpg'})
            if media:
                return {'id': post_id, 'title': detail.get('desc') or '抖音图集',
                        '_type': 'liuce_album', 'media': media}
        return self._parse_aweme_video_app(detail)


def safe_headers(headers):
    return {key: str(value) for key, value in (headers or {}).items()
            if key.lower() in ('user-agent', 'referer', 'origin')}


def flatten_info(info, limit=100):
    if info.get('_type') == 'liuce_album':
        return info['media'][:limit]
    if info.get('_type') in ('playlist', 'multi_video'):
        media = []
        for entry in info.get('entries') or []:
            if entry:
                media.extend(flatten_info(entry, limit - len(media)))
            if len(media) >= limit:
                break
        return media
    # Choose a single self-contained HTTP video. No transcoding or silent audio loss.
    formats = [fmt for fmt in info.get('formats', [])
               if fmt.get('url') and fmt.get('protocol', urlparse(fmt.get('url', '')).scheme).startswith('http')
               and fmt.get('vcodec') != 'none' and fmt.get('acodec') != 'none'
               and fmt.get('ext') in ('mp4', 'webm', 'mov')]
    selected = max(formats, key=lambda f: (f.get('height') or 0, f.get('tbr') or 0, f.get('filesize') or 0)) if formats else info
    address = selected.get('url')
    protocol = selected.get('protocol', 'https')
    if not address or not protocol.startswith('http'):
        raise ExtractorError('No self-contained downloadable media format', expected=True)
    return [{'url': address, 'kind': 'video', 'ext': selected.get('ext') or 'mp4',
             'bytes': selected.get('filesize') or selected.get('filesize_approx') or 0,
             'headers': safe_headers(selected.get('http_headers') or info.get('http_headers'))}]


def resolve(url, browser_cookies=''):
    options = {'quiet': True, 'no_warnings': True, 'logger': QuietLogger(),
               'socket_timeout': 15, 'retries': 1, 'extractor_retries': 1,
               'noplaylist': True, 'skip_download': True, 'ignore_no_formats_error': True,
               'cachedir': False, 'playlistend': 100}
    with YoutubeDL(options) as ydl:
        host = urlparse(url).hostname or ''
        cookie_host = 'www.douyin.com' if 'douyin.com' in host else 'www.xiaohongshu.com' if 'xhslink.com' in host else host
        for part in browser_cookies.split(';'):
            if '=' in part:
                name, value = part.strip().split('=', 1)
                ydl.cookiejar.set_cookie(Cookie(0, name, value, None, False, cookie_host, False,
                                                False, '/', True, True, None, True, None, None, {}))
        if host in ('v.douyin.com', 'xhslink.com') or host.endswith('.xhslink.com'):
            with ydl.urlopen(url) as response:
                url = response.url
        parsed = urlparse(url)
        if 'iesdouyin.com' in (parsed.hostname or '') and '/share/video/' in parsed.path:
            url = 'https://www.douyin.com/video/' + parsed.path.split('/share/video/')[1].strip('/')
        # Invoke adapters directly: standard yt-dlp post-processing assumes video formats.
        adapter = LiuceXhsIE(ydl) if LiuceXhsIE.suitable(url) else LiuceDouyinIE(ydl) if LiuceDouyinIE.suitable(url) else None
        info = adapter.extract(url) if adapter else ydl.extract_info(url, download=False)
        media = flatten_info(info)
        if not media:
            raise ExtractorError('No downloadable media found', expected=True)
        for item in media:
            if item['url'].startswith('http://'):
                item['url'] = 'https://' + item['url'][7:]
            item.setdefault('headers', {'Referer': url, 'User-Agent': ydl.params.get('http_headers', {}).get('User-Agent', 'Mozilla/5.0')})
        return json.dumps({'title': (info.get('title') or '链接收录')[:160],
                           'source': url, 'media': media}, ensure_ascii=False)
