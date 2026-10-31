"""Small Android bridge around yt-dlp (Unlicense); no remote parsing service.

The two album adapters use the same upstream page/API metadata as yt-dlp.
They return actual album images, never the video thumbnail collection.
"""
import json
import re
from urllib.parse import urlparse, urlunparse, parse_qs, urlencode
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


def work_author(platform, payload):
    """Only a platform-owned immutable author ID can create an author card."""
    if not isinstance(payload, dict):
        return None
    if platform == 'xiaohongshu':
        user_id = payload.get('userId') or payload.get('user_id')
        profile = ('https://www.xiaohongshu.com/user/profile/' + user_id
                   if isinstance(user_id, str) and re.fullmatch(r'[A-Za-z0-9_-]{1,160}', user_id) else '')
    elif platform == 'douyin':
        sec_uid = payload.get('sec_uid') or payload.get('secUid')
        profile = ('https://www.douyin.com/user/' + sec_uid
                   if isinstance(sec_uid, str) and re.fullmatch(r'[A-Za-z0-9_-]{1,200}', sec_uid) else '')
    else:
        profile = ''
    if not profile:
        return None
    name = payload.get('nickname') or payload.get('nickName') or ''
    if not isinstance(name, str) or len(name) > 100 or any(ord(c) < 32 or c in '<>' for c in name):
        name = ''
    return {'profile_url': profile, 'name': name}


def xhs_album(note, note_id):
    """Return every image in a photo note, not the cover used by video notes."""
    images = note.get('imageList') or note.get('image_list') or []
    if not images or note.get('type') == 'video' or (note.get('type') != 'normal' and 'video' in note):
        return None
    entries = []
    for image in images:
        options = image.get('infoList') or image.get('info_list') or []
        address = image.get('urlDefault') or image.get('url_default')
        if not address:
            address = next((item.get('url') for item in options
                            if item.get('imageScene') in ('WB_DFT', 'WB_PRV') and item.get('url')), None)
        if not address:
            address = next((item.get('url') for item in options if item.get('url')), None)
        if address:
            entries.append({'url': address, 'kind': 'image', 'ext': 'jpg'})
    if not entries:
        raise ExtractorError('No album images available', expected=True)
    return {'id': note_id, 'title': note.get('title') or note.get('desc') or '小红书图集',
            '_type': 'liuce_album', 'media': entries,
            'author': work_author('xiaohongshu', note.get('user'))}


class LiuceXhsIE(XiaoHongShuIE):
    IE_NAME = "liuce:xiaohongshu"

    def _real_extract(self, url):
        note_id = self._match_id(url)
        webpage = self._download_webpage(url, note_id)
        state = self._search_json(r'window\.__INITIAL_STATE__\s*=', webpage,
                                  'initial state', note_id, transform_source=js_to_json)
        note = traverse_obj(state, ('note', 'noteDetailMap', note_id, 'note')) or {}
        if not note:
            details = traverse_obj(state, ('note', 'noteDetailMap')) or {}
            note = next((item.get('note') for item in details.values()
                         if isinstance(item, dict) and (item.get('note') or {}).get('noteId') == note_id), {})
        album = xhs_album(note, note_id)
        if album:
            return album
        info = super()._real_extract(url)
        if isinstance(info, dict):
            info['author'] = work_author('xiaohongshu', note.get('user'))
        return info


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
                        '_type': 'liuce_album', 'media': media,
                        'author': work_author('douyin', detail.get('author'))}
        info = self._parse_aweme_video_app(detail)
        if isinstance(info, dict):
            info['author'] = work_author('douyin', detail.get('author'))
        return info


def safe_headers(headers):
    return {key: str(value) for key, value in (headers or {}).items()
            if key.lower() in ('user-agent', 'referer', 'origin')}


def xhs_login_redirect_target(url):
    """Recover the same-site note URL when a share link lands on the login page.

    The redirect parameter is untrusted. Only a literal Xiaohongshu note URL is
    accepted; never use this as a general-purpose redirect resolver.
    """
    parsed = urlparse(url)
    if parsed.hostname not in ('www.xiaohongshu.com', 'xiaohongshu.com') or parsed.path != '/login':
        return None
    values = parse_qs(parsed.query).get('redirectPath') or []
    if len(values) != 1:
        return None
    try:
        note = urlparse(values[0])
        trusted = (note.scheme in ('http', 'https')
                   and note.hostname in ('www.xiaohongshu.com', 'xiaohongshu.com')
                   and note.port is None and note.username is None and note.password is None)
    except ValueError:
        return None
    if not trusted:
        return None
    if not re.fullmatch(r'/(?:discovery/item|explore)/[0-9a-fA-F]{24}/?', note.path):
        return None
    return urlunparse(('https', note.netloc, note.path, '', note.query, ''))


def xhs_error_redirect_target(url):
    """Recover a literal note ID/token when a trusted share redirects to /404.

    Some platform shares use a fallback error URL even though the note remains
    readable in a normal browser. This only reconstructs that same platform's
    public note address; it cannot turn an inaccessible note into a download.
    """
    parsed = urlparse(url)
    if parsed.scheme != 'https' or parsed.hostname not in ('www.xiaohongshu.com', 'xiaohongshu.com') or parsed.path != '/404':
        return None
    values = parse_qs(parsed.query)
    ids = values.get('noteId') or []
    if len(ids) != 1 or not re.fullmatch(r'[0-9a-fA-F]{24}', ids[0]):
        return None
    tokens = values.get('xsec_token') or []
    if len(tokens) > 1 or tokens and not re.fullmatch(r'[A-Za-z0-9_=-]{1,256}', tokens[0]):
        return None
    query = urlencode({'source': 'webshare', 'xsec_token': tokens[0], 'xsec_source': 'pc_share'}) if tokens else ''
    return urlunparse(('https', 'www.xiaohongshu.com', '/discovery/item/' + ids[0], '', query, ''))


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
               if fmt.get('url') and fmt.get('protocol', urlparse(fmt.get('url', '')).scheme) in ('http', 'https')
               and fmt.get('vcodec') != 'none' and fmt.get('acodec') != 'none'
               and fmt.get('ext') in ('mp4', 'webm', 'mov')]
    direct = (info if info.get('url') and info.get('ext') in ('mp4', 'webm', 'mov')
              and info.get('protocol', urlparse(info['url']).scheme) in ('http', 'https')
              and info.get('vcodec') != 'none' and info.get('acodec') != 'none' else None)
    selected = max(formats, key=lambda f: (f.get('height') or 0, f.get('tbr') or 0, f.get('filesize') or 0)) if formats else direct or {}
    address = selected.get('url')
    protocol = selected.get('protocol', 'https')
    if not address or protocol not in ('http', 'https'):
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
        cookie_host = 'www.douyin.com' if host in ('v.douyin.com', 'www.douyin.com', 'douyin.com') else 'www.xiaohongshu.com' if host in ('xhslink.com', 'www.xhslink.com', 'xhslink.cn') else host
        for part in browser_cookies.split(';'):
            if '=' in part:
                name, value = part.strip().split('=', 1)
                ydl.cookiejar.set_cookie(Cookie(0, name, value, None, False, cookie_host, False,
                                                False, '/', True, True, None, True, None, None, {}))
        if host in ('v.douyin.com', 'xhslink.com', 'xhslink.cn') or host.endswith('.xhslink.com'):
            with ydl.urlopen(url) as response:
                url = response.url
            recovered = xhs_login_redirect_target(url) or xhs_error_redirect_target(url)
            if recovered:
                url = recovered
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
                           'source': url, 'media': media, 'author': info.get('author')}, ensure_ascii=False)
