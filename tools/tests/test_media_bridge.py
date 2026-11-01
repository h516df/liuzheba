"""Offline regression tests for the on-device yt-dlp adapter (synthetic metadata)."""
import json
import sys
import unittest
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'app/src/main/python'))
from liuce_media import LiuceXhsIE, LiuceDouyinIE, LiuceInstagramIE, instagram_media, flatten_info, safe_headers, QuietLogger, xhs_album, xhs_login_redirect_target, xhs_error_redirect_target, resolve, work_author
from yt_dlp import YoutubeDL
from yt_dlp.utils import ExtractorError

class MediaBridgeTest(unittest.TestCase):
    def engine(self):
        return YoutubeDL({'quiet': True, 'logger': QuietLogger(), 'cachedir': False})

    def test_creator_identity_requires_explicit_platform_id(self):
        self.assertEqual(work_author('xiaohongshu', {'userId': 'syntheticUser123', 'nickname': '同名'}),
                         {'profile_url': 'https://www.xiaohongshu.com/user/profile/syntheticUser123', 'name': '同名'})
        self.assertEqual(work_author('douyin', {'sec_uid': 'syntheticSecUid', 'nickname': '同名'})['profile_url'],
                         'https://www.douyin.com/user/syntheticSecUid')
        self.assertIsNone(work_author('xiaohongshu', {'nickname': '仅有昵称'}))
        self.assertIsNone(work_author('douyin', {'sec_uid': '/malicious/path'}))
        self.assertIsNone(work_author('instagram', {'userId': 'syntheticUser123'}))
        self.assertEqual(work_author('instagram', {'uploader_id': '123456', 'channel': 'new.handle', 'uploader': '同名'}),
                         {'platform_id': '123456', 'handle': 'new.handle', 'name': '同名'})
        self.assertIsNone(work_author('instagram', {'uploader_id': 'new.handle', 'channel': 'new.handle'}))
        self.assertEqual(work_author('bilibili', {'uploader_id': 123456, 'uploader': 'B站作者'})['profile_url'],
                         'https://space.bilibili.com/123456')
        self.assertIsNone(work_author('bilibili', {'uploader_id': 'someNickname'}))

    def test_xhs_album_preserves_image_order_without_previews(self):
        note_id = '6411cf99000000001300b6d9'
        note = {'type':'normal', 'title':'合成图集', 'desc':'完整文案\n第二行', 'user':{'userId':'syntheticAuthor','nickname':'合成作者'}, 'imageList':[
            {'urlDefault':'https://example.com/full-1.jpg','urlPre':'https://example.com/thumbnail-1.jpg'},
            {'urlDefault':'https://example.com/full-2.jpg'}]}
        state = {'note':{'noteDetailMap':{note_id:{'note':note}}}}
        extractor = LiuceXhsIE(self.engine())
        extractor._download_webpage = lambda *_: '<script>window.__INITIAL_STATE__=' + json.dumps(state) + '</script>'
        result = extractor._real_extract('https://www.xiaohongshu.com/explore/' + note_id)
        self.assertEqual([m['url'] for m in flatten_info(result)], ['https://example.com/full-1.jpg','https://example.com/full-2.jpg'])
        self.assertTrue(all(m['kind']=='image' for m in flatten_info(result)))
        self.assertEqual(result['author']['profile_url'], 'https://www.xiaohongshu.com/user/profile/syntheticAuthor')
        self.assertEqual(result['description'], '完整文案\n第二行')

    def test_xhs_alternate_image_data_prefers_full_items_not_preview(self):
        note = {'type': 'normal', 'image_list': [
            {'info_list': [{'imageScene': 'WB_DFT', 'url': 'https://example.com/original-1.jpg'},
                           {'imageScene': 'WB_PRV', 'url': 'https://example.com/preview-1.jpg'}]},
            {'url_default': 'https://example.com/original-2.webp'}]}
        album = xhs_album(note, 'synthetic-note')
        self.assertEqual([item['url'] for item in album['media']],
                         ['https://example.com/original-1.jpg', 'https://example.com/original-2.webp'])
        self.assertIsNone(xhs_album({'type':'video', 'video':{}, 'imageList':[]}, 'synthetic-note'))

    def test_xhs_short_cn_share_resolves_to_long_item_and_keeps_all_images(self):
        target = 'https://www.xiaohongshu.com/discovery/item/6411cf99000000001300b6d9?xsec_token=synthetic'
        class Redirect:
            url = target
            def __enter__(self): return self
            def __exit__(self, *_): return False
        album = {'id':'6411cf99000000001300b6d9', 'title':'完整图文', 'description':'原文 #生活', '_type':'liuce_album', 'media':[
            {'url':'https://example.com/image1.jpg','kind':'image','ext':'jpg'},
            {'url':'https://example.com/image2.jpg','kind':'image','ext':'jpg'}]}
        with patch.object(YoutubeDL, 'urlopen', return_value=Redirect()), patch.object(LiuceXhsIE, 'extract', return_value=album) as extractor:
            result = json.loads(resolve('https://xhslink.cn/o/SyntheticShort', 'web_session=fake'))
        extractor.assert_called_once_with(target)
        self.assertEqual(result['source'], target)
        self.assertEqual(result['id'], '6411cf99000000001300b6d9')
        self.assertEqual(result['description'], '原文 #生活')
        self.assertEqual(len(result['media']), 2)
        self.assertTrue(all(item['headers']['Referer']==target for item in result['media']))

    def test_xhs_login_redirect_retains_same_site_note_without_following_arbitrary_url(self):
        from urllib.parse import quote
        note = 'http://www.xiaohongshu.com/discovery/item/6ae2c6a2000000002b03912d?xsec_token=synthetic'
        login = 'https://www.xiaohongshu.com/login?redirectPath=' + quote(note, safe='')
        canonical = note.replace('http://', 'https://', 1)
        self.assertEqual(canonical, xhs_login_redirect_target(login))
        for foreign in (
            'https://attacker.example/discovery/item/6ae2c6a2000000002b03912d',
            'https://www.xiaohongshu.com.attacker.example/discovery/item/6ae2c6a2000000002b03912d',
            'https://www.xiaohongshu.com/login',
            'https://www.xiaohongshu.com/discovery/item/not-a-valid-id',
        ):
            self.assertIsNone(xhs_login_redirect_target('https://www.xiaohongshu.com/login?redirectPath=' + quote(foreign, safe='')))

        class Redirect:
            url = login
            def __enter__(self): return self
            def __exit__(self, *_): return False

        album = {'title': '图文', '_type': 'liuce_album',
                 'media': [{'url': 'https://example.com/picture.jpg', 'kind': 'image', 'ext': 'jpg'}]}
        with patch.object(YoutubeDL, 'urlopen', return_value=Redirect()), \
                patch.object(LiuceXhsIE, 'extract', return_value=album) as extractor:
            result = json.loads(resolve('https://xhslink.cn/o/SyntheticShort'))
        extractor.assert_called_once_with(canonical)
        self.assertEqual(result['media'][0]['url'], 'https://example.com/picture.jpg')

    def test_xhs_404_share_redirect_only_recovers_trusted_note_id_and_token(self):
        note_id = '6956513d000000001e022eda'
        target = f'https://www.xiaohongshu.com/discovery/item/{note_id}?source=webshare&xsec_token=synthetic-token%3D&xsec_source=pc_share'
        broken = f'https://www.xiaohongshu.com/404?source=note&noteId={note_id}&xsec_token=synthetic-token%3D'
        self.assertEqual(target, xhs_error_redirect_target(broken))
        self.assertIsNone(xhs_error_redirect_target(broken.replace('xiaohongshu.com', 'xiaohongshu.com.attacker.example')))
        self.assertIsNone(xhs_error_redirect_target(broken.replace(note_id, 'invalid-id')))
        self.assertIsNone(xhs_error_redirect_target(broken + '&xsec_token=second-token'))

        class Redirect:
            url = broken
            def __enter__(self): return self
            def __exit__(self, *_): return False

        album = {'title': '可访问图文', '_type': 'liuce_album',
                 'media': [{'url': 'https://example.com/original.jpg', 'kind': 'image', 'ext': 'jpg'}]}
        with patch.object(YoutubeDL, 'urlopen', return_value=Redirect()), \
                patch.object(LiuceXhsIE, 'extract', return_value=album) as extractor:
            result = json.loads(resolve('https://xhslink.cn/o/SyntheticShort'))
        extractor.assert_called_once_with(target)
        self.assertEqual(result['source'], target)

    def test_douyin_album_uses_images_not_video_cover(self):
        extractor = LiuceDouyinIE(self.engine())
        extractor._download_json = lambda *_, **__: {'aweme_detail':{'desc':'合成作品','author':{'sec_uid':'syntheticSec','nickname':'抖音作者'},'images':[
            {'url_list':['https://example.com/image-1.webp']},
            {'display_image':{'url_list':['https://example.com/image-2.webp']}}],
            'video':{'cover':{'url_list':['https://example.com/cover.jpg']}}}}
        result = extractor._real_extract('https://www.douyin.com/note/123456789')
        self.assertEqual(len(flatten_info(result)), 2)
        self.assertNotIn('cover.jpg', json.dumps(result))
        self.assertEqual(result['author']['profile_url'], 'https://www.douyin.com/user/syntheticSec')
        self.assertEqual(result['description'], '合成作品')

    def test_douyin_public_playback_remains_usable_when_creator_disables_download(self):
        """No download_addr is needed when the publisher still exposes a public playable MP4."""
        detail = {
            'aweme_id': '7698417431442743418', 'desc': '合成公开播放作品',
            'video': {
                'width': 1080, 'height': 1920,
                'play_addr': {'url_list': ['https://example.com/playback.mp4']},
                'download_addr': None,
            },
            'author': {},
        }
        extractor = LiuceDouyinIE(self.engine())
        extractor._download_json = lambda *_, **__: {'aweme_detail': detail}
        result = extractor._real_extract('https://www.douyin.com/video/7698417431442743418')
        self.assertEqual(
            [item['url'] for item in flatten_info(result)],
            ['https://example.com/playback.mp4'],
        )

    def test_douyin_short_share_keeps_public_playback_and_browser_session(self):
        target = 'https://www.iesdouyin.com/share/video/1234567890123456789/?region=CN&from=share'
        canonical = 'https://www.douyin.com/video/1234567890123456789'

        class Redirect:
            url = target
            def __enter__(self): return self
            def __exit__(self, *_): return False

        def extraction(extractor, url):
            self.assertEqual(canonical, url)
            self.assertEqual('synthetic-browser-session', extractor._get_cookies('https://www.douyin.com/')['s_v_web_id'].value)
            return {'title': '只公开播放', 'formats': [
                {'url': 'https://example.com/public-playback.mp4', 'ext': 'mp4', 'acodec': 'aac', 'vcodec': 'h264'}]}

        with patch.object(YoutubeDL, 'urlopen', return_value=Redirect()), \
                patch.object(LiuceDouyinIE, 'extract', extraction):
            result = json.loads(resolve('https://v.douyin.com/SyntheticShare/',
                                        's_v_web_id=synthetic-browser-session'))
        self.assertEqual(result['source'], canonical)
        self.assertEqual(result['media'][0]['url'], 'https://example.com/public-playback.mp4')

    def test_douyin_missing_playback_metadata_fails_closed(self):
        extractor = LiuceDouyinIE(self.engine())
        extractor._download_json = lambda *_, **__: {}
        with self.assertRaisesRegex(ExtractorError, 'fresh browser session'):
            extractor._real_extract('https://www.douyin.com/video/7698417431442743418')

    def test_instagram_reel_uses_its_normal_extractor_with_browser_cookies(self):
        url = 'https://www.instagram.com/reel/De6y3nBMmnZ/?dlrf=synthetic'
        def extraction(extractor, requested_url):
            self.assertEqual(url, requested_url)
            self.assertEqual('synthetic-session', extractor._get_cookies('https://www.instagram.com/')['sessionid'].value)
            return {'title': '公开 Reel', 'formats': [
                {'url': 'https://example.com/instagram.mp4', 'ext': 'mp4', 'acodec': 'aac', 'vcodec': 'h264'}]}
        with patch.object(LiuceInstagramIE, 'extract', extraction):
            result = json.loads(resolve(url, 'sessionid=synthetic-session'))
        self.assertEqual(result['source'], url)
        self.assertEqual(result['media'][0]['url'], 'https://example.com/instagram.mp4')

    def test_instagram_carousel_preserves_only_real_photos_in_order_and_video(self):
        from_info = {'_type': 'playlist', 'id': 'synthetic-post', 'description': '图文原文', 'entries': [
            {'_liuce_photo': True, 'thumbnails': [
                {'url': 'https://example.com/preview-a.jpg', 'width': 320, 'height': 320},
                {'url': 'https://example.com/original-a.jpg', 'width': 1440, 'height': 1920}]},
            {'_liuce_photo': False, 'thumbnails': [{'url': 'https://example.com/cover.jpg'}],
             'formats': [{'url': 'https://example.com/real-video.mp4', 'ext': 'mp4', 'acodec': 'aac', 'vcodec': 'h264'}]},
            {'_liuce_photo': True, 'thumbnails': [{'url': 'https://example.com/original-b.jpg', 'width': 1000, 'height': 1200}]}]}
        self.assertEqual([entry['url'] for entry in instagram_media(from_info)], [
            'https://example.com/original-a.jpg', 'https://example.com/real-video.mp4',
            'https://example.com/original-b.jpg'])
        with patch.object(LiuceInstagramIE, 'extract', return_value=from_info):
            result = json.loads(resolve('https://www.instagram.com/p/synthetic-post/'))
        self.assertEqual(result['description'], '图文原文')
        self.assertEqual([m['kind'] for m in result['media']], ['image', 'video', 'image'])

    def test_instagram_does_not_save_video_cover_when_video_is_inaccessible(self):
        info = {'_type': 'playlist', 'entries': [{'_liuce_photo': False,
                'thumbnails': [{'url': 'https://example.com/cover.jpg'}]}]}
        with self.assertRaises(ExtractorError):
            instagram_media(info)

    def test_instagram_photo_tag_is_from_upstream_media_type_only(self):
        extractor = LiuceInstagramIE(self.engine())
        raw = {'media_type': 1, 'pk': '100', 'user': {'username': 'synthetic'},
               'image_versions2': {'candidates': [{'url': 'https://example.com/full.jpg', 'width': 900, 'height': 1200}]}}
        result = extractor._extract_product(raw, get_comments=False)
        self.assertTrue(result['_liuce_photo'])
        self.assertEqual(instagram_media(result)[0]['url'], 'https://example.com/full.jpg')

    def test_bilibili_video_uses_verified_numeric_mid_not_display_name(self):
        url = 'https://www.bilibili.com/video/BVsynthetic'
        def extraction(_, requested_url, download=False):
            self.assertEqual(url, requested_url)
            return {'id': 'BVsynthetic', 'title': '公开视频', 'description': '原作者文案',
                    'uploader_id': '12501', 'uploader': '同名作者',
                    'formats': [{'url': 'https://example.com/bilibili.mp4', 'ext': 'mp4',
                                 'acodec': 'aac', 'vcodec': 'h264'}]}
        with patch.object(YoutubeDL, 'extract_info', extraction):
            result = json.loads(resolve(url))
        self.assertEqual(result['author']['profile_url'], 'https://space.bilibili.com/12501')
        self.assertEqual(result['author']['name'], '同名作者')
        self.assertEqual(result['description'], '原作者文案')
        self.assertEqual(result['id'], 'BVsynthetic')

    def test_raw_upstream_video_formats_keep_sound(self):
        result={'formats':[
            {'url':'https://example.com/720.mp4','ext':'mp4','height':720,'acodec':'aac','vcodec':'h264'},
            {'url':'https://example.com/silent.mp4','ext':'mp4','height':2160,'acodec':'none','vcodec':'h264'},
            {'url':'https://example.com/1080.mp4','ext':'mp4','height':1080,'acodec':'aac','vcodec':'h264'}]}
        self.assertEqual(flatten_info(result)[0]['url'], 'https://example.com/1080.mp4')
        self.assertEqual(safe_headers({'Cookie':'private','Authorization':'private','Referer':'https://example.com'}), {'Referer':'https://example.com'})

    def test_other_social_video_playlists_keep_real_media_without_thumbnails(self):
        info = {'_type':'playlist', 'entries':[
            {'title':'微博视频','formats':[{'url':'https://cdn.example.com/weibo.mp4','ext':'mp4','acodec':'aac','vcodec':'h264'}],
             'thumbnail':'https://cdn.example.com/weibo-cover.jpg'},
            {'title':'快手视频','formats':[{'url':'https://cdn.example.com/kuaishou.mp4','ext':'mp4','acodec':'aac','vcodec':'h264'}]}]}
        items = flatten_info(info)
        self.assertEqual([item['url'] for item in items],
                         ['https://cdn.example.com/weibo.mp4','https://cdn.example.com/kuaishou.mp4'])
        self.assertTrue(all(item['kind']=='video' for item in items))

    def test_stream_only_or_thumbnail_only_page_cannot_pretend_to_be_downloaded(self):
        for info in (
            {'url':'https://example.com/post/123', 'title':'只有网页'},
            {'thumbnail':'https://example.com/cover.jpg','formats':[]},
            {'formats':[{'url':'https://example.com/stream.m3u8','ext':'mp4','protocol':'m3u8_native'}]},
            {'formats':[{'url':'https://example.com/chunk.m4s','ext':'mp4','protocol':'http_dash_segments'}]}):
            with self.subTest(info=info):
                with self.assertRaises(ExtractorError): flatten_info(info)

if __name__ == '__main__':
    unittest.main()
